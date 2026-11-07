package com.guangshi.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Rational
import android.util.Size
import android.view.Display
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.hypot

internal data class RenderSettings(val film: Film, val adjust: Adjust)

/** A latest-frame-only worker drives the actual viewfinder through the same processor as export. */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
internal class LiveCamera(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val display: Display?,
    private val onFrame: (Bitmap) -> Unit,
    private val onCatalog: (List<LensSpec>) -> Unit,
    private val onLens: (Int, LensPlan) -> Unit,
    private val onCapture: (ImageCapture?) -> Unit,
    private val onError: (String) -> Unit,
    initialMm: Int = 24
) {
    val settings=AtomicReference(RenderSettings(Film.NATURAL,Adjust()))
    private val stopped=AtomicBoolean(false)
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private var provider: ProcessCameraProvider?=null
    private var camera: Camera?=null
    private var analysis: ImageAnalysis?=null
    private var capture: ImageCapture?=null
    private var lenses: List<LensSpec> = emptyList()
    private var activeId: String?=null
    private var requestedMm=initialMm
    private var requestGeneration=0
    private var lastFrame=0L
    private val rotation=display?.rotation?:Surface.ROTATION_0
    private val orientation=object: OrientationEventListener(context) {
        override fun onOrientationChanged(value: Int) {
            if(value==ORIENTATION_UNKNOWN)return
            capture?.targetRotation=when(value) {
                in 45..134->Surface.ROTATION_270; in 135..224->Surface.ROTATION_180
                in 225..314->Surface.ROTATION_90; else->Surface.ROTATION_0
            }
        }
    }

    fun start() {
        val future=ProcessCameraProvider.getInstance(context)
        future.addListener({
            if(stopped.get())return@addListener
            try {
                provider=future.get()
                lenses=provider!!.availableCameraInfos.mapNotNull { info ->
                    val c=Camera2CameraInfo.from(info)
                    if(c.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)!=CameraCharacteristics.LENS_FACING_BACK) return@mapNotNull null
                    val physical=c.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?:return@mapNotNull null
                    val focal=c.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()?:return@mapNotNull null
                    val diagonal=hypot(physical.width,physical.height)
                    if(diagonal<=0f || focal<=0f)return@mapNotNull null
                    val zoom=info.zoomState.value
                    LensSpec(c.cameraId,focal*43.2666f/diagonal,zoom?.minZoomRatio?:1f,zoom?.maxZoomRatio?:1f,
                        c.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList().orEmpty())
                }
                check(lenses.isNotEmpty()) { "系统未开放后置相机" }
                onCatalog(lenses)
                selectFocal(requestedMm)
            } catch(e:Exception) { onError("相机启动失败：${e.localizedMessage}") }
        },ContextCompat.getMainExecutor(context))
    }

    fun selectFocal(mm: Int) {
        if(stopped.get() || provider==null)return
        val plan=chooseLens(lenses,mm)
        if(plan==null) { onError("系统未开放 ${mm}mm 所需镜头或变焦范围"); return }
        requestedMm=mm
        val generation=++requestGeneration
        try {
            if(activeId!=plan.lens.id) {
                unbind()
                val selector=CameraSelector.Builder().addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId==plan.lens.id } }.build()
                val aspect=AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                val rs=ResolutionSelector.Builder().setAspectRatioStrategy(aspect)
                    .setResolutionStrategy(ResolutionStrategy(Size(640,480),ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()
                analysis=ImageAnalysis.Builder().setResolutionSelector(rs).setTargetRotation(rotation)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { it.setAnalyzer(worker,::analyze) }
                capture=ImageCapture.Builder().setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setTargetRotation(rotation).build()
                val ratio=if(rotation==Surface.ROTATION_90 || rotation==Surface.ROTATION_270) Rational(4,3) else Rational(3,4)
                val group=UseCaseGroup.Builder().setViewPort(ViewPort.Builder(ratio,rotation).setScaleType(ViewPort.FILL_CENTER).build())
                    .addUseCase(analysis!!).addUseCase(capture!!).build()
                camera=provider!!.bindToLifecycle(owner,selector,group)
                activeId=plan.lens.id
                orientation.enable()
            }
            val zoom=camera!!.cameraControl.setZoomRatio(plan.zoom)
            zoom.addListener({
                if(stopped.get() || generation!=requestGeneration)return@addListener
                try { zoom.get(); onLens(mm,plan); onCapture(capture) }
                catch(e:Exception) { onCapture(null); onError("焦段切换失败，请重试") }
            },ContextCompat.getMainExecutor(context))
        } catch(e:Exception) { onCapture(null); onError("镜头连接失败：${e.localizedMessage}") }
    }

    fun focus(x: Float,y: Float) {
        val cam=camera?:return
        val d=display?:return
        try {
            val point=DisplayOrientedMeteringPointFactory(d,cam.cameraInfo,1f,1f).createPoint(x,y)
            cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).setAutoCancelDuration(3,TimeUnit.SECONDS).build())
        } catch(_:Exception) { /* Fixed focus cameras can still render and capture. */ }
    }

    private fun analyze(image: ImageProxy) {
        try {
            if(stopped.get())return
            val now=SystemClock.elapsedRealtime()
            if(now-lastFrame<66)return
            lastFrame=now
            val input=image.toBitmap()
            val rect=image.cropRect
            val cropped=Bitmap.createBitmap(input,rect.left,rect.top,rect.width(),rect.height())
            if(cropped!==input)input.recycle()
            val degrees=image.imageInfo.rotationDegrees
            val upright=Bitmap.createBitmap(cropped,0,0,cropped.width,cropped.height,Matrix().apply { postRotate(degrees.toFloat()) },true)
            if(upright!==cropped)cropped.recycle()
            val state=settings.get()
            val result=try { runBlocking { develop(upright,state.film,state.adjust) } } finally { upright.recycle() }
            main.post { if(stopped.get())result.recycle() else onFrame(result) }
        } catch(e:Exception) {
            if(!stopped.get()) main.post { if(!stopped.get())onError("实时预览失败：${e.localizedMessage}") }
        } finally { image.close() }
    }

    private fun unbind() {
        analysis?.clearAnalyzer()
        val useCases=listOfNotNull(analysis,capture)
        if(useCases.isNotEmpty()) provider?.unbind(*useCases.toTypedArray())
        camera=null; capture=null; analysis=null; activeId=null
    }
    fun close() {
        stopped.set(true); requestGeneration++; orientation.disable(); unbind(); worker.shutdown()
    }
}
