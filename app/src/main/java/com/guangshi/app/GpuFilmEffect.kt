package com.guangshi.app

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.view.View
import androidx.annotation.RequiresApi
import kotlin.math.pow

/** Camera frames stay in the TextureView/RenderThread path; no Bitmap readback or CPU pixel loop. */
@RequiresApi(33)
internal class GpuFilmEffect(context: Context) {
    val shader=RuntimeShader(context.resources.openRawResource(R.raw.film).bufferedReader().use { it.readText() })
    private var previous: RenderSettings?=null
    private var target: View?=null
    private var width=0
    private var height=0

    fun parameters(settings: RenderSettings,w: Int,h: Int) {
        require(w>0 && h>0)
        val a=settings.adjust
        shader.setFloatUniform("size",w.toFloat(),h.toFloat())
        shader.setIntUniform("film",settings.film.ordinal)
        shader.setFloatUniform("toneSettings",2.0.pow(a.exposure.toDouble()).toFloat(),a.grain,a.contrast,a.saturation)
        shader.setFloatUniform("colors",a.cyan,a.magenta,a.yellow,a.warmth)
        shader.setFloatUniform("focus",a.aperture,a.focusX,a.focusY)
    }

    fun apply(view: View,settings: RenderSettings) {
        if(view.width<=0 || view.height<=0)return
        if(target===view && settings==previous && width==view.width && height==view.height)return
        width=view.width; height=view.height; previous=settings; target=view
        parameters(settings,width,height)
        // RenderEffect snapshots the shader. Recreate on parameter changes, never for each camera frame.
        view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader,"image"))
        view.invalidate()
    }
    fun clear(view: View) {
        view.setRenderEffect(null)
        if(target===view) {previous=null;target=null;width=0;height=0}
    }
}
