package com.guangshi.app

import android.app.Application
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class StudioModel(app: Application): AndroidViewModel(app) {
    val store=FilmStore(app)
    var negatives by mutableStateOf<List<Negative>>(emptyList()); private set
    var message by mutableStateOf("")
    var shooting by mutableStateOf(false); private set
    var exporting by mutableStateOf(false); private set
    var film by mutableStateOf(Film.NATURAL); private set
    var cameraAdjust by mutableStateOf(Adjust())
    var focalMm by mutableIntStateOf(24)
    private val saveMutex=Mutex()
    init { reload() }

    fun chooseFilm(value: Film) { film=value; cameraAdjust=cameraAdjust.copy(grain=value.grain) }
    fun reload() { viewModelScope.launch { negatives=withContext(Dispatchers.IO) { store.list() } } }
    fun capture(capture: ImageCapture?) {
        if(shooting || capture==null)return
        shooting=true
        val id=System.currentTimeMillis().toString()+"_"+UUID.randomUUID().toString().take(4)
        val snapshot=Negative(id,film,cameraAdjust,focalMm)
        val file=store.original(id)
        try {
            capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),ContextCompat.getMainExecutor(getApplication()),object: ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    viewModelScope.launch {
                        try {
                            withContext(Dispatchers.IO) { store.save(snapshot) }
                            reload(); message="已收进底片 · ${snapshot.film.title}"
                        } catch(e:Exception) { Log.e("Guangshi","Save negative",e); message="照片已拍摄，底片信息保存失败，请重试" }
                        finally { shooting=false }
                    }
                }
                override fun onError(e: ImageCaptureException) {
                    shooting=false; file.delete(); Log.e("Guangshi","Capture",e)
                    message="拍摄失败，请重新连接相机后重试"
                }
            })
        } catch(e:Exception) { shooting=false; file.delete(); message="相机尚未准备好，请重试" }
    }
    fun persist(n: Negative) {
        viewModelScope.launch { saveMutex.withLock {
            try {
                withContext(Dispatchers.IO) { store.save(n) }
                negatives=negatives.map { if(it.id==n.id)n else it }
            } catch(e:Exception) { Log.e("Guangshi","Save parameters",e); message="参数保存失败，请重试" }
        } }
    }
    fun export(n: Negative,border: Boolean) {
        if(exporting)return
        exporting=true
        viewModelScope.launch {
            try {
                message=withContext(Dispatchers.Default) {
                    val raw=store.load(n.id,Int.MAX_VALUE)
                    try {
                        val result=develop(raw,n.film,n.adjust)
                        try { store.export(result,n,border) } finally { result.recycle() }
                    } finally { raw.recycle() }
                }
            } catch(e:OutOfMemoryError) { message="内存不足，原片已保留。请关闭其他应用后重试" }
            catch(e:Exception) { Log.e("Guangshi","Export",e); message="导出失败，原片已保留：${e.localizedMessage}" }
            finally { exporting=false }
        }
    }
}
