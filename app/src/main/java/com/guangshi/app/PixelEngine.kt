package com.guangshi.app

import kotlin.math.*

internal object PixelEngine {
 fun pixel(c:Int,x:Int,y:Int,w:Int,h:Int,film:Film,a:Adjust,gain:Float=2.0.pow(a.exposure.toDouble()).toFloat()):Int {
                var r=(c ushr 16 and 255)/255f; var g=(c ushr 8 and 255)/255f; var b=(c and 255)/255f
                fun curve(v:Float,k:Float)=((v-.5f)*k+.5f).coerceIn(0f,1f)
                when(film) {
                    Film.DAY -> { r=curve(r,1.06f)*1.06f+.018f; g=curve(g,1.02f)*1.01f; b=curve(b,.94f)*.9f+.015f }
                    Film.NIGHT -> { r=curve(r,1.12f)*.88f; g=curve(g,1.08f)*1.02f+.012f; b=curve(b,1.08f)*1.13f+.025f }
                    Film.MONO -> { val l=curve(.2126f*r+.7152f*g+.0722f*b,1.48f); r=l; g=l; b=l }
                }
                val gx=x*1600/w; val gy=y*1600/h
                var hash=gx*374761393+gy*668265263; hash=(hash xor (hash ushr 13))*1274126177
                val noise=((hash ushr 8 and 65535)/65535f-.5f)*a.grain*.24f
                fun channel(v:Float,t:Float)=((v*gain-t*.16f+noise)*255).roundToInt().coerceIn(0,255)
                return (255 shl 24) or (channel(r,if(film==Film.MONO)0f else a.cyan) shl 16) or (channel(g,if(film==Film.MONO)0f else a.magenta) shl 8) or channel(b,if(film==Film.MONO)0f else a.yellow)
 }
}
