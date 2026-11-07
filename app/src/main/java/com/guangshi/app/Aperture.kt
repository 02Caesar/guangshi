package com.guangshi.app

import android.graphics.Bitmap
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.*

/** Region blur, not depth estimation. The tapped ellipse remains sharp at every output resolution. */
internal suspend fun applyAperture(image: Bitmap, a: Adjust): Bitmap {
    if(a.aperture >= 15.9f) return image
    val ratio=min(1f,480f/max(image.width,image.height))
    val small=Bitmap.createScaledBitmap(image,max(1,(image.width*ratio).roundToInt()),max(1,(image.height*ratio).roundToInt()),true)
    val w=small.width; val h=small.height
    var src=IntArray(w*h); var dst=IntArray(w*h)
    small.getPixels(src,0,w,0,0,w,h)
    if(small!==image) small.recycle()
    val strength=((16f-a.aperture)/(16f-1.4f)).coerceIn(0f,1f)
    val radius=max(1,((2+strength*10)*max(w,h)/480f).roundToInt())
    // Three separable box passes approximate a Gaussian; work stays at <=480 px.
    repeat(3) {
        coroutineContext.ensureActive()
        for(y in 0 until h) {
            var r=0; var g=0; var b=0
            for(k in -radius..radius) { val c=src[y*w+k.coerceIn(0,w-1)]; r+=c ushr 16 and 255; g+=c ushr 8 and 255; b+=c and 255 }
            val count=2*radius+1
            for(x in 0 until w) {
                dst[y*w+x]=(255 shl 24) or (r/count shl 16) or (g/count shl 8) or (b/count)
                val old=src[y*w+(x-radius).coerceIn(0,w-1)]; val next=src[y*w+(x+radius+1).coerceIn(0,w-1)]
                r+=(next ushr 16 and 255)-(old ushr 16 and 255); g+=(next ushr 8 and 255)-(old ushr 8 and 255); b+=(next and 255)-(old and 255)
            }
        }
        val swap=src; src=dst; dst=swap
        for(x in 0 until w) {
            var r=0; var g=0; var b=0
            for(k in -radius..radius) { val c=src[k.coerceIn(0,h-1)*w+x]; r+=c ushr 16 and 255; g+=c ushr 8 and 255; b+=c and 255 }
            val count=2*radius+1
            for(y in 0 until h) {
                dst[y*w+x]=(255 shl 24) or (r/count shl 16) or (g/count shl 8) or (b/count)
                val old=src[(y-radius).coerceIn(0,h-1)*w+x]; val next=src[(y+radius+1).coerceIn(0,h-1)*w+x]
                r+=(next ushr 16 and 255)-(old ushr 16 and 255); g+=(next ushr 8 and 255)-(old ushr 8 and 255); b+=(next and 255)-(old and 255)
            }
        }
        val swap2=src; src=dst; dst=swap2
    }
    val row=IntArray(image.width)
    for(y in 0 until image.height) {
        coroutineContext.ensureActive()
        image.getPixels(row,0,image.width,0,y,image.width,1)
        val ny=y.toFloat()/image.height
        for(x in row.indices) {
            val nx=x.toFloat()/image.width
            val d=sqrt(((nx-a.focusX)/.34f).pow(2)+((ny-a.focusY)/.29f).pow(2))
            val t=((d-.48f)/.95f).coerceIn(0f,1f)
            val alpha=t*t*(3-2*t)*strength
            val c=row[x]; val blur=src[min(h-1,y.toLong().times(h).div(image.height).toInt())*w+min(w-1,x.toLong().times(w).div(image.width).toInt())]
            fun mix(shift:Int):Int { val v=c ushr shift and 255; val b=blur ushr shift and 255; return (v+(b-v)*alpha).roundToInt().coerceIn(0,255) }
            row[x]=(255 shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
        image.setPixels(row,0,image.width,0,y,image.width,1)
    }
    return image
}
