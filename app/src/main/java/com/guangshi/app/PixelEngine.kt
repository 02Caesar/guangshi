package com.guangshi.app

import kotlin.math.*

internal object PixelEngine {
    fun pixel(c: Int, x: Int, y: Int, w: Int, h: Int, film: Film, a: Adjust,
              gain: Float = 2.0.pow(a.exposure.toDouble()).toFloat()): Int {
        require(w > 0 && h > 0)
        var r=(c ushr 16 and 255)/255f
        var g=(c ushr 8 and 255)/255f
        var b=(c and 255)/255f
        fun curve(v: Float,k: Float)=((v-.5f)*k+.5f).coerceIn(0f,1f)
        when(film) {
            Film.PASTEL -> {r=curve(r,.82f)*1.04f+.035f;g=curve(g,.86f)+.015f;b=curve(b,.85f)*1.04f+.025f}
            Film.FOREST -> {r=curve(r,1.04f)*.94f+.02f;g=curve(g,.96f)*1.07f;b=curve(b,1.02f)*.91f}
            Film.BLUE -> {r=curve(r,1.08f)*.86f+.01f;g=curve(g,1.02f)*.98f;b=curve(b,1.06f)*1.12f+.04f}
            Film.SUNSET -> {r=curve(r,1.12f)*1.12f+.025f;g=curve(g,1.04f)*1.02f;b=curve(b,1.08f)*.78f}
            Film.NATURAL -> Unit
            Film.DAY -> { r=curve(r,1.06f)*1.06f+.018f; g=curve(g,1.02f)*1.01f; b=curve(b,.94f)*.9f+.015f }
            Film.NIGHT -> { r=curve(r,1.12f)*.88f; g=curve(g,1.08f)*1.02f+.012f; b=curve(b,1.08f)*1.13f+.025f }
            Film.MONO -> { val l=curve(.2126f*r+.7152f*g+.0722f*b,1.48f); r=l; g=l; b=l }
            Film.FADE -> { r=curve(r,.77f)*1.02f+.035f; g=curve(g,.8f)+.02f; b=curve(b,.72f)*.93f+.05f }
            Film.CINEMA -> { val l=.2126f*r+.7152f*g+.0722f*b; r=curve(r,1.12f)+l*.075f-(1-l)*.04f; g=curve(g,1.06f)+(1-l)*.03f; b=curve(b,1.1f)+(1-l)*.085f-l*.055f }
        }
        val l=.2126f*r+.7152f*g+.0722f*b
        if(film!=Film.MONO) {
            r=l+(r-l)*a.saturation+a.warmth*.08f
            g=l+(g-l)*a.saturation
            b=l+(b-l)*a.saturation-a.warmth*.08f
        }
        r=(r-.5f)*a.contrast+.5f; g=(g-.5f)*a.contrast+.5f; b=(b-.5f)*a.contrast+.5f
        val gx=x.toLong()*1600/w; val gy=y.toLong()*1600/h
        var hash=gx.toInt()*374761393+gy.toInt()*668265263
        hash=(hash xor (hash ushr 13))*1274126177
        val noise=((hash ushr 8 and 65535)/65535f-.5f)*a.grain*.24f
        fun channel(v: Float,t: Float)=((v*gain-t*.16f+noise)*255).roundToInt().coerceIn(0,255)
        return (255 shl 24) or (channel(r,if(film==Film.MONO)0f else a.cyan) shl 16) or
            (channel(g,if(film==Film.MONO)0f else a.magenta) shl 8) or channel(b,if(film==Film.MONO)0f else a.yellow)
    }
}
