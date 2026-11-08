package com.guangshi.app

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.camera.view.PreviewView

@RequiresApi(33)
internal class FilteredPreviewHost(context: Context):FrameLayout(context) {
    private var settings=RenderSettings(Film.NATURAL,Adjust())
    private var effect:GpuFilmEffect?=null
    init {clipChildren=true;clipToPadding=true;setBackgroundColor(android.graphics.Color.BLACK);setLayerType(View.LAYER_TYPE_HARDWARE,null)}
    fun attachPreview(preview:PreviewView,effect:GpuFilmEffect) {
        this.effect=effect
        (preview.parent as? android.view.ViewGroup)?.removeView(preview)
        addView(preview,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT))
        effect.apply(this,settings)
    }
    fun update(settings:RenderSettings) {this.settings=settings;effect?.apply(this,settings)}
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if(w>0&&h>0)effect?.apply(this,settings)
    }
    override fun onAttachedToWindow() {super.onAttachedToWindow();effect?.apply(this,settings)}
    override fun onDetachedFromWindow() {effect?.clear(this);super.onDetachedFromWindow()}
}
