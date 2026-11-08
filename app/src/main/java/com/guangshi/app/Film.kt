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

enum class Film(val title: String, val code: String, val grain: Float, val note: String) {
    NATURAL("原生", "N · 00", 0f, "保留眼前的光"),
    DAY("暖日光", "D · 400", .18f, "金色高光，温暖肤色"),
    NIGHT("冷夜景", "N · 800", .28f, "冷蓝阴影，城市夜色"),
    MONO("银盐黑白", "B · 100", .36f, "深黑与明亮的银"),
    FADE("褪色记忆", "F · 200", .24f, "低反差，柔和旧时光"),
    CINEMA("青橙电影", "C · 500", .12f, "青色暗部，琥珀亮部")
}
data class Adjust(
    val exposure: Float = 0f, val grain: Float = 0f,
    val cyan: Float = 0f, val magenta: Float = 0f, val yellow: Float = 0f,
    val contrast: Float = 1f, val saturation: Float = 1f, val warmth: Float = 0f,
    val aperture: Float = 16f, val focusX: Float = .5f, val focusY: Float = .45f
)
data class Negative(val id: String, val film: Film, val adjust: Adjust, val focalMm: Int = 24, val xpan: Boolean = false)
class FilmStore(val context: Context) {
    val dir = File(context.filesDir, "negatives").apply { mkdirs() }
    fun original(id: String) = File(dir, "$id.jpg")
    fun list(deleted: Boolean = false): List<Negative> = dir.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { f ->
        runCatching {
            val j = JSONObject(f.readText())
            val film = Film.entries.find { it.name == j.getString("film") } ?: Film.NATURAL
            fun read(key: String, default: Float) = j.optDouble(key, default.toDouble()).toFloat().let { if (it.isFinite()) it else default }
            Negative(f.nameWithoutExtension, film, Adjust(
                exposure=read("exposure",0f).coerceIn(-3f,3f), grain=read("grain",film.grain).coerceIn(0f,1f),
                cyan=read("cyan",0f).coerceIn(-1f,1f), magenta=read("magenta",0f).coerceIn(-1f,1f), yellow=read("yellow",0f).coerceIn(-1f,1f),
                contrast=read("contrast",1f).coerceIn(.5f,1.5f), saturation=read("saturation",1f).coerceIn(0f,1.5f), warmth=read("warmth",0f).coerceIn(-1f,1f),
                aperture=read("aperture",16f).coerceIn(1.4f,16f), focusX=read("focusX",.5f).coerceIn(0f,1f), focusY=read("focusY",.45f).coerceIn(0f,1f)
            ), j.optInt("focalMm",24),j.optBoolean("xpan",false))
        }.getOrNull()
    }.filter { original(it.id).exists() && File(dir,"${it.id}.deleted").exists() == deleted }.sortedByDescending { it.id }

    /** Tombstones preserve both JPEG and edits; saving edits never resurrects a negative. */
    fun setDeleted(id: String, deleted: Boolean) {
        require(id.matches(Regex("[A-Za-z0-9_]+")))
        check(original(id).isFile && File(dir,"$id.json").isFile) { "底片不存在" }
        val marker=File(dir,"$id.deleted")
        if(deleted) check(marker.exists() || marker.createNewFile()) { "无法移入回收站" }
        else check(!marker.exists() || marker.delete()) { "无法恢复底片" }
    }

    fun save(n: Negative) {
        val a=n.adjust
        val j=JSONObject().put("film",n.film.name).put("focalMm",n.focalMm).put("xpan",n.xpan)
            .put("exposure",a.exposure).put("grain",a.grain).put("cyan",a.cyan).put("magenta",a.magenta).put("yellow",a.yellow)
            .put("contrast",a.contrast).put("saturation",a.saturation).put("warmth",a.warmth)
            .put("aperture",a.aperture).put("focusX",a.focusX).put("focusY",a.focusY)
        val tmp=File(dir,"${n.id}.tmp")
        tmp.writeText(j.toString())
        check(tmp.renameTo(File(dir,"${n.id}.json"))) { "底片参数写入失败" }
    }
    fun load(id: String, maxEdge: Int): Bitmap {
        val file=original(id)
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }; BitmapFactory.decodeFile(file.path,bounds)
        val sample=DecodeSizing.sampleSize(bounds.outWidth,bounds.outHeight,maxEdge)
        val decoded=requireNotNull(BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=sample; inPreferredConfig=Bitmap.Config.ARGB_8888 }))
        val exif=ExifInterface(file)
        val matrix=Matrix().apply { if(exif.isFlipped) postScale(-1f,1f); postRotate(exif.rotationDegrees.toFloat()) }
        var oriented=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)
        if(oriented!==decoded) decoded.recycle()
        val wide=runCatching { JSONObject(File(dir,"$id.json").readText()).optBoolean("xpan",false) }.getOrDefault(false)
        if(wide) {
            val bounds=wideCrop(oriented.width,oriented.height)
            val cropped=Bitmap.createBitmap(oriented,bounds[0],bounds[1],bounds[2],bounds[3])
            if(cropped!==oriented)oriented.recycle()
            oriented=cropped
        }
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
        return applyAperture(out,a)
    } catch(e: Throwable) { out.recycle(); throw e }
}
