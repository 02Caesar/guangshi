package com.guangshi.app

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import kotlin.math.roundToInt

/** Center crop applied after EXIF orientation, identically for thumbnail, darkroom and export. */
internal fun wideCrop(w:Int,h:Int):IntArray {
    require(w>0 && h>0)
    val ratio=if(w<h)24.0/65 else 65.0/24
    val cw=minOf(w,(h*ratio).roundToInt()).coerceAtLeast(1)
    val ch=minOf(h,(w/ratio).roundToInt()).coerceAtLeast(1)
    return intArrayOf((w-cw)/2,(h-ch)/2,cw,ch)
}
internal fun zoomLabel(mm:Int):String = "%.1fx".format(java.util.Locale.US,when(mm) {15->.6f;24->1f;49->2f;73->3f;146->6f;else->mm/24f})

@Composable internal fun XpanDevelopment(vm:StudioModel,n:Negative) {
    var image by remember(n.id) {mutableStateOf<Bitmap?>(null)}
    var failed by remember {mutableStateOf(false)}
    val progress=remember(n.id) {Animatable(0f)}
    LaunchedEffect(n.id) {
        try {
            image=withContext(Dispatchers.Default) { val raw=vm.store.load(n.id,1400);try {develop(raw,n.film,n.adjust)} finally {raw.recycle()} }
            delay(150);progress.animateTo(1f,tween(1600));delay(500);vm.dismissDevelopment()
        } catch(e:CancellationException) {throw e} catch(_:Exception) {failed=true}
    }
    Dialog(onDismissRequest={vm.dismissDevelopment()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF121212)).safeDrawingPadding().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Text("XPAN  /  65:24",color=Amber)
            Spacer(Modifier.height(24.dp))
            val p=progress.value
            val m=ColorMatrix(floatArrayOf(2*p-1,0f,0f,0f,255*(1-p),0f,2*p-1,0f,0f,255*(1-p),0f,0f,2*p-1,0f,255*(1-p),0f,0f,0f,1f,0f))
            image?.let {Image(it.asImageBitmap(),"XPAN 显影",Modifier.fillMaxWidth().aspectRatio(it.width.toFloat()/it.height),colorFilter=ColorFilter.colorMatrix(m))}
                ?:Text(if(failed)"预览读取失败，底片已保留" else "正在准备底片…",color=Amber)
            Spacer(Modifier.height(24.dp))
            Text(if(p<1)"让光慢慢浮现" else "已收进底片",color=Amber)
            TextButton(onClick={vm.dismissDevelopment()}) {Text("返回拍摄")}
        }
    }
}
