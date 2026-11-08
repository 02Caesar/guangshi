package com.guangshi.app

import android.content.Context
import android.graphics.*
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GpuFilmEffectTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private fun render(input:Bitmap,film:Film,a:Adjust,pad:Int=0):Bitmap {
        val effect=GpuFilmEffect(context)
        effect.parameters(RenderSettings(film,a),input.width,input.height)
        effect.shader.setInputShader("image",BitmapShader(input,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP))
        val node=RenderNode("film-test").apply {setPosition(0,0,input.width+2*pad,input.height+2*pad)}
        val recording=node.beginRecording()
        recording.translate(pad.toFloat(),pad.toFloat())
        recording.drawRect(-pad.toFloat(),-pad.toFloat(),input.width.toFloat()+pad,input.height.toFloat()+pad,Paint().apply {shader=effect.shader})
        node.endRecording()
        val hardware=ReflectionHelpers.callStaticMethod<Bitmap>(HardwareRenderer::class.java,"createHardwareBitmap",
            ClassParameter.from(RenderNode::class.java,node),ClassParameter.from(Int::class.javaPrimitiveType,input.width+2*pad),ClassParameter.from(Int::class.javaPrimitiveType,input.height+2*pad))
        return requireNotNull(hardware.copy(Bitmap.Config.ARGB_8888,false)).also {hardware.recycle()}

    }
    @Test fun pixelsOutsideViewfinderRemainTransparentForAllFilms() {
        val input=Bitmap.createBitmap(24,32,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.GRAY)}
        for(film in Film.entries) {
            val output=render(input,film,Adjust(aperture=2f),8)
            assertEquals(0,Color.alpha(output.getPixel(0,20)))
            assertEquals(0,Color.alpha(output.getPixel(20,0)))
            assertEquals(0,Color.alpha(output.getPixel(39,47)))
            assertEquals(255,Color.alpha(output.getPixel(20,20)))
            output.recycle()
        }
        input.recycle()
    }
    @Test fun shaderCompilesAndMatchesExportToneForEveryFilm() {
        val input=Bitmap.createBitmap(24,32,Bitmap.Config.ARGB_8888)
        for(y in 0 until input.height)for(x in 0 until input.width)input.setPixel(x,y,Color.rgb(40+x*7,30+y*5,160))
        val adjustments=listOf(Adjust(),Adjust(exposure=.6f,cyan=-.3f,magenta=.2f,yellow=.1f,contrast=1.2f,saturation=.8f,warmth=.2f))
        for(film in Film.entries)for(a in adjustments) {
            val output=render(input,film,a)
            for(y in listOf(2,16,29))for(x in listOf(2,12,21)) {
                val cpu=PixelEngine.pixel(input.getPixel(x,y),x,y,input.width,input.height,film,a)
                val gpu=output.getPixel(x,y)
                for(shift in listOf(0,8,16))assertTrue("$film channel $shift",abs((cpu ushr shift and 255)-(gpu ushr shift and 255))<=3)
            }
            output.recycle()
        }
        input.recycle()
    }
    @Test fun apertureAndGrainChangeThePreviewAndKeepTappedCenterSharp() {
        val input=Bitmap.createBitmap(120,160,Bitmap.Config.ARGB_8888)
        for(y in 0 until input.height)for(x in 0 until input.width)input.setPixel(x,y,if((x/3+y/3)%2==0)Color.BLACK else Color.WHITE)
        val sharp=render(input,Film.NATURAL,Adjust())
        val blur=render(input,Film.NATURAL,Adjust(aperture=1.4f,focusX=.5f,focusY=.5f))
        assertEquals(sharp.getPixel(60,80),blur.getPixel(60,80))
        assertNotEquals(sharp.getPixel(5,5),blur.getPixel(5,5))
        input.eraseColor(Color.GRAY)
        val grain=render(input,Film.NATURAL,Adjust(grain=.8f))
        assertNotEquals(Color.GRAY,grain.getPixel(20,50))
        sharp.recycle();blur.recycle();grain.recycle();input.recycle()
    }
    @Test fun changingUniformsRebuildsEffectWithoutNeedingANewCameraFrame() {
        val view=View(context).apply {layout(0,0,240,320)}
        val effect=GpuFilmEffect(context)
        effect.apply(view,RenderSettings(Film.NATURAL,Adjust()))
        effect.apply(view,RenderSettings(Film.CINEMA,Adjust(exposure=.8f,aperture=2.7f,grain=.4f)))
        effect.clear(view)
        val preview=androidx.camera.view.PreviewView(context)
        val first=FilteredPreviewHost(context).apply {attachPreview(preview,effect);layout(0,0,240,320)}
        first.update(RenderSettings(Film.CINEMA,Adjust(aperture=2.7f)))
        val second=FilteredPreviewHost(context).apply {attachPreview(preview,effect);layout(0,0,240,320)}
        assertEquals(0,first.childCount);assertEquals(1,second.childCount)
        effect.clear(first)
        second.update(RenderSettings(Film.DAY,Adjust(exposure=.5f)))
        effect.clear(second)
    }
}
