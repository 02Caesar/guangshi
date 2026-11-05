package com.guangshi.app

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import org.json.JSONObject
import java.io.File
import kotlin.math.*
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

enum class Film(val title: String, val code: String, val grain: Float) {
    DAY("暖调日光", "光 400", 0.18f), NIGHT("冷调夜景", "夜 800", 0.28f), MONO("高反差黑白", "银 100", 0.36f)
}
data class Adjust(val exposure: Float = 0f, val grain: Float = 0.18f, val cyan: Float = 0f, val magenta: Float = 0f, val yellow: Float = 0f)
data class Negative(val id: String, val film: Film, val adjust: Adjust)
class FilmStore(val context: Context) {
    val dir = File(context.filesDir, "negatives").apply { mkdirs() }
    fun original(id: String) = File(dir, "$id.jpg")
    fun list(): List<Negative> = dir.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { f ->
        runCatching { val j=JSONObject(f.readText()); val film=Film.valueOf(j.getString("film")); Negative(f.nameWithoutExtension,film,Adjust(j.optDouble("exposure",0.0).toFloat(),j.optDouble("grain",film.grain.toDouble()).toFloat(),j.optDouble("cyan",0.0).toFloat(),j.optDouble("magenta",0.0).toFloat(),j.optDouble("yellow",0.0).toFloat())) }.getOrNull()
    }.filter { original(it.id).exists() }.sortedByDescending { it.id }
    fun save(n: Negative) {
        val j=JSONObject().put("film",n.film.name).put("exposure",n.adjust.exposure).put("grain",n.adjust.grain).put("cyan",n.adjust.cyan).put("magenta",n.adjust.magenta).put("yellow",n.adjust.yellow)
        val tmp=File(dir,"${n.id}.tmp"); tmp.writeText(j.toString()); check(tmp.renameTo(File(dir,"${n.id}.json")))
    }
    fun load(id: String, maxEdge: Int): Bitmap {
        val file=original(id)
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }; BitmapFactory.decodeFile(file.path,bounds)
        var sample=1
        while(max(bounds.outWidth,bounds.outHeight)/sample > maxEdge*2) sample*=2
        val decoded=requireNotNull(BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=sample; inPreferredConfig=Bitmap.Config.ARGB_8888 }))
        val exif=ExifInterface(file)
        val matrix=Matrix().apply { if(exif.isFlipped) postScale(-1f,1f); postRotate(exif.rotationDegrees.toFloat()) }
        val oriented=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)
        if(oriented!==decoded) decoded.recycle()
        val ratio=min(1f,maxEdge.toFloat()/max(oriented.width,oriented.height))
        if(ratio==1f) return oriented
        return Bitmap.createScaledBitmap(oriented,(oriented.width*ratio).roundToInt(),(oriented.height*ratio).roundToInt(),true).also { if(it!==oriented) oriented.recycle() }
    }
    fun export(bitmap: Bitmap, n: Negative, border: Boolean): String {
        val output=if(border) {
            val pad=(bitmap.width*.045f).roundToInt().coerceAtLeast(16)
            Bitmap.createBitmap(bitmap.width+pad*2,bitmap.height+pad*3,Bitmap.Config.ARGB_8888).also { b ->
                Canvas(b).apply { drawColor(Color.rgb(8,8,8)); drawBitmap(bitmap,pad.toFloat(),pad.toFloat(),null); drawText("${n.film.code}   ▶ ${n.id.takeLast(4)}",pad.toFloat(),b.height-pad*.5f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(239,174,73); textSize=pad*.48f }) }
            }
        } else bitmap
        val values=ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"光室_${n.id}_${System.currentTimeMillis()}.jpg"); put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg"); put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/光室"); put(MediaStore.Images.Media.IS_PENDING,1) }
        val resolver=context.contentResolver
        val uri=requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { check(output.compress(Bitmap.CompressFormat.JPEG,95,it)) }
            check(resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)>0)
        } catch(e: Exception) { resolver.delete(uri,null,null); throw e }
        finally { if(output!==bitmap) output.recycle() }
        return "已保存至系统相册 · Pictures/光室"
    }
}
/** Shared normalized-coordinate grain and tone pipeline for preview and full-size export. */
suspend fun develop(source: Bitmap, film: Film, a: Adjust): Bitmap {
    val w=source.width; val h=source.height
    val out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
    val row=IntArray(w)
    val gain=2.0.pow(a.exposure.toDouble()).toFloat()
    try {
        for(y in 0 until h) {
            coroutineContext.ensureActive()
            source.getPixels(row,0,w,0,y,w,1)
            for(x in 0 until w) {
                row[x]=PixelEngine.pixel(row[x],x,y,w,h,film,a,gain)
            }
            out.setPixels(row,0,w,0,y,w,1)
        }
        return out
    } catch(e: Throwable) { out.recycle(); throw e }
}
