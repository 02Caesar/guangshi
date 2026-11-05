package com.guangshi.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import java.util.UUID
import kotlin.math.*

val Amber=Color(0xFFF1B45B)
class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)); setContent {
        MaterialTheme(colorScheme=darkColorScheme(primary=Amber,background=Color(0xFF101113),surface=Color(0xFF212124))) { Studio() }
    } }
}
class StudioModel(app: android.app.Application): AndroidViewModel(app) {
    val store=FilmStore(app)
    var negatives by mutableStateOf<List<Negative>>(emptyList()); private set
    var message by mutableStateOf("")
    var shooting by mutableStateOf(false)
    var exporting by mutableStateOf(false)
    init { reload() }
    fun reload() { viewModelScope.launch { negatives=withContext(Dispatchers.IO) { store.list() } } }
    fun capture(capture: ImageCapture?, film: Film) {
        if(shooting || capture==null) return
        shooting=true
        val id=System.currentTimeMillis().toString()+"_"+UUID.randomUUID().toString().take(4)
        val file=store.original(id)
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),ContextCompat.getMainExecutor(getApplication()),object:ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                viewModelScope.launch { try { withContext(Dispatchers.IO) { store.save(Negative(id,film,Adjust(grain=film.grain))) }; reload(); message="已收进底片 · ${film.title}" } catch(e:Exception) { message="底片保存失败：${e.localizedMessage}" } finally { shooting=false } }
            }
            override fun onError(e:ImageCaptureException) { shooting=false; file.delete(); message="拍摄失败，请重试：${e.localizedMessage}" }
        })
    }
    private val saveMutex=Mutex()
    fun persist(n: Negative) { viewModelScope.launch { saveMutex.withLock { try { withContext(Dispatchers.IO) { store.save(n) }; negatives=negatives.map { if(it.id==n.id)n else it } } catch(e:Exception) { message="参数保存失败" } } } }
    fun export(n:Negative,border:Boolean) {
        if(exporting)return
        exporting=true
        viewModelScope.launch { try {
            message=withContext(Dispatchers.Default) {
                val raw=store.load(n.id,Int.MAX_VALUE)
                try { val rendered=develop(raw,n.film,n.adjust); try { store.export(rendered,n,border) } finally { rendered.recycle() } } finally { raw.recycle() }
            }
        } catch(e:OutOfMemoryError) { message="照片过大，内存不足，请关闭其他应用后重试" }
        catch(e:Exception) { message="导出失败：${e.localizedMessage}" } finally { exporting=false } }
    }
}
@Composable fun Studio(vm:StudioModel=viewModel()) {
    var darkroom by rememberSaveable { mutableStateOf(false) }
    var film by rememberSaveable { mutableIntStateOf(0) }
    val snack=remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { if(vm.message.isNotBlank()) { snack.showSnackbar(vm.message); vm.message="" } }
    Scaffold(snackbarHost={ SnackbarHost(snack) },containerColor=if(darkroom)Color(0xFF210E10) else Color(0xFF101113)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp)) {
            Row(Modifier.fillMaxWidth().height(52.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text("光 室  /  ${if(darkroom)"彩色暗房" else "胶片相机"}",color=Amber)
                TextButton(onClick={darkroom=!darkroom}) { Text(if(darkroom)"返回相机" else "进入暗房") }
            }
            if(darkroom) Darkroom(vm,Modifier.weight(1f)) else CameraScreen(vm,Film.entries[film],{film=it},{darkroom=true},Modifier.weight(1f))
        }
    }
}
@Composable fun CameraScreen(vm:StudioModel,film:Film,onFilm:(Int)->Unit,open:()->Unit,modifier:Modifier) {
    val context=LocalContext.current; val lifecycle=LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted=it }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) granted=ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED }
        lifecycle.lifecycle.addObserver(observer); onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val preview=remember { PreviewView(context).apply { scaleType=PreviewView.ScaleType.FIT_CENTER; implementationMode=PreviewView.ImplementationMode.COMPATIBLE } }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var ready by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    DisposableEffect(granted,lifecycle,retry) {
        var disposed=false; var provider:ProcessCameraProvider?=null
        val orientation=object:OrientationEventListener(context) { override fun onOrientationChanged(angle:Int) {
            if(angle==ORIENTATION_UNKNOWN)return
            capture?.targetRotation=when(angle) { in 45..134->Surface.ROTATION_270; in 135..224->Surface.ROTATION_180; in 225..314->Surface.ROTATION_90; else->Surface.ROTATION_0 }
        } }
        if(granted) {
            val future=ProcessCameraProvider.getInstance(context)
            future.addListener({ if(!disposed) try {
                provider=future.get()
                val p=Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val c=ImageCapture.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setTargetRotation(preview.display?.rotation?:Surface.ROTATION_0).build()
                provider!!.unbindAll(); provider!!.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,p,c)
                capture=c; ready=true; orientation.enable()
            } catch(e:Exception) { ready=false; vm.message="相机启动失败：${e.localizedMessage}" } },ContextCompat.getMainExecutor(context))
        }
        onDispose { disposed=true; orientation.disable(); provider?.unbindAll(); capture=null; ready=false }
    }
    Column(modifier, horizontalAlignment=Alignment.CenterHorizontally) {
        Text("${film.code}     /     ${film.title}     ▸ ${vm.negatives.size+1}",color=Amber,modifier=Modifier.padding(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black),contentAlignment=Alignment.Center) {
            if(granted) AndroidView(factory={preview},modifier=Modifier.fillMaxSize()) else Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text("让光进入镜头",color=Amber)
                Text("仅使用相机权限，照片留在本机",modifier=Modifier.padding(12.dp))
                Button(onClick={permission.launch(Manifest.permission.CAMERA)}) { Text("允许使用相机") }
                TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}) { Text("前往系统设置") }
            }
            if(granted && !ready) TextButton(onClick={retry++}) { Text("重新连接相机") }
        }
        Text("原始取景 · 4:3 完整画面 · 拍摄后生成胶片效果",style=MaterialTheme.typography.labelSmall,color=Color.Gray,modifier=Modifier.padding(6.dp))
        FilmDial(film.ordinal,onFilm)
        Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(60.dp).clickable(onClick=open),contentAlignment=Alignment.Center) { vm.negatives.firstOrNull()?.let { Thumb(vm,it) } ?: Text("底片",color=Amber) }
            Button(onClick={vm.capture(capture,film)},enabled=granted&&ready&&!vm.shooting,shape=CircleShape,contentPadding=PaddingValues(0.dp),modifier=Modifier.size(76.dp)) { Text(if(vm.shooting)"保存中" else "快门") }
            TextButton(onClick=open) { Text("暗房 →") }
        }
    }
}
@Composable fun FilmDial(selected:Int,onChange:(Int)->Unit) {
    val view=LocalView.current
    val angle by animateFloatAsState(-selected*120f,label="拨盘吸附")
    var drag by remember { mutableFloatStateOf(0f) }
    fun choose(i:Int) { val n=(i+3)%3; if(n!=selected) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onChange(n) } }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
        Box(Modifier.size(104.dp).pointerInput(selected) {
            detectDragGestures(onDragStart={drag=0f},onDragEnd={drag=0f},onDragCancel={drag=0f}) { change,amount ->
                change.consume()
                val center=Offset(size.width/2f,size.height/2f); val v=change.position-center
                drag+=(v.x*amount.y-v.y*amount.x)/max(v.getDistance(),1f)
                if(abs(drag)>24.dp.toPx()) { choose(selected+if(drag>0)1 else -1); drag=0f }
            }
        },contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize().rotate(angle)) {
                drawCircle(Color(0xFF303136)); drawCircle(Color(0xFF151619),radius=size.minDimension*.4f)
                for(i in 0 until 36) { val a=i*PI/18; val outer=size.minDimension*.46f; val inner=outer-if(i%12==0)14.dp.toPx() else 5.dp.toPx(); drawLine(if(i%12==0)Amber else Color.Gray,center+Offset(cos(a).toFloat()*inner,sin(a).toFloat()*inner),center+Offset(cos(a).toFloat()*outer,sin(a).toFloat()*outer),2f) }
                val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.rgb(241,180,91); textSize=10.dp.toPx(); textAlign=android.graphics.Paint.Align.CENTER }
                listOf("400","800","100").forEachIndexed { i,label -> val a=-PI/2+i*2*PI/3; drawContext.canvas.nativeCanvas.drawText(label,center.x+cos(a).toFloat()*size.minDimension*.27f,center.y+sin(a).toFloat()*size.minDimension*.27f+paint.textSize/3,paint) }
            }
            Text("光室",color=Amber,style=MaterialTheme.typography.labelSmall)
            Text("▼",color=Amber,modifier=Modifier.align(Alignment.TopCenter))
        }
        Column { Film.entries.forEachIndexed { i,f -> TextButton(onClick={choose(i)}) { Text("${if(i==selected)"●" else "○"} ${f.code}  ${f.title}",color=if(i==selected)Amber else Color.Gray) } } }
    }
}
@Composable fun Thumb(vm:StudioModel,n:Negative) {
    var bitmap by remember(n.id,n.adjust) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(n.id,n.adjust) {
        try { bitmap=withContext(Dispatchers.Default) { val source=vm.store.load(n.id,160); try { develop(source,n.film,n.adjust) } finally {source.recycle()} } }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { bitmap=null }
    }
    bitmap?.let { Image(it.asImageBitmap(),contentDescription="${n.film.title}底片",modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop) }
}
@Composable fun Darkroom(vm:StudioModel,modifier:Modifier) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val n=vm.negatives.find { it.id==selected }?:vm.negatives.firstOrNull()
    var adjust by remember(n?.id) { mutableStateOf(n?.adjust?:Adjust()) }
    var compare by remember { mutableStateOf(false) }
    var border by rememberSaveable { mutableStateOf(true) }
    var raw by remember { mutableStateOf<Bitmap?>(null) }
    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(n?.id) {
        raw=null; rendered=null; error=""
        if(n!=null) try { raw=withContext(Dispatchers.IO) { vm.store.load(n.id,1000) } } catch(e:Exception) { error="底片读取失败" }
    }
    LaunchedEffect(raw,adjust) {
        val source=raw?:return@LaunchedEffect
        delay(65)
        try { rendered=withContext(Dispatchers.Default) { develop(source,n!!.film,adjust) } } catch(e:CancellationException) { throw e } catch(e:Exception) { error="预览处理失败" }
    }
    Column(modifier.verticalScroll(rememberScrollState())) {
        if(n==null) { Text("底片盒还是空的\n回到相机，拍下第一束光。",modifier=Modifier.padding(32.dp),color=Amber); return@Column }
        Column(Modifier.fillMaxWidth().background(Color.Black).padding(12.dp)) {
            Text("${n.film.code}    ▸ ${n.id.takeLast(4)}",color=Amber,style=MaterialTheme.typography.labelSmall)
            Box(Modifier.fillMaxWidth().heightIn(min=160.dp,max=340.dp).aspectRatio(4f/3f),contentAlignment=Alignment.Center) {
                val image=if(compare)raw else rendered
                if(image!=null) Image(image.asImageBitmap(),"暗房成片",modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit) else Text(if(error.isEmpty())"显影中…" else error,color=Amber)
            }
            Text("光室    ${if(compare)"原片" else n.film.title}",color=Amber,style=MaterialTheme.typography.labelSmall)
        }
        LazyRow(Modifier.fillMaxWidth().height(90.dp).padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(vm.negatives,key={it.id}) { item -> Box(Modifier.width(76.dp).fillMaxHeight().border(2.dp,if(item.id==n.id)Amber else Color.Black).padding(5.dp).clickable { selected=item.id },contentAlignment=Alignment.Center) { Thumb(vm,item) } }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("彩色暗房",color=Amber,modifier=Modifier.padding(8.dp))
            Text("按住看原片",color=Amber,modifier=Modifier.background(Color(0xFF422026)).padding(12.dp).pointerInput(Unit) { detectTapGestures(onPress={compare=true; try { tryAwaitRelease() } finally {compare=false} }) })
        }
        fun update(a:Adjust) { adjust=a }
        Control("曝光",adjust.exposure,-2f..2f,{update(adjust.copy(exposure=it))},{vm.persist(n.copy(adjust=adjust))})
        Control("颗粒",adjust.grain,0f..1f,{update(adjust.copy(grain=it))},{vm.persist(n.copy(adjust=adjust))})
        if(n.film!=Film.MONO) {
            Control("青",adjust.cyan,-1f..1f,{update(adjust.copy(cyan=it))},{vm.persist(n.copy(adjust=adjust))})
            Control("品红",adjust.magenta,-1f..1f,{update(adjust.copy(magenta=it))},{vm.persist(n.copy(adjust=adjust))})
            Control("黄",adjust.yellow,-1f..1f,{update(adjust.copy(yellow=it))},{vm.persist(n.copy(adjust=adjust))})
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={adjust=Adjust(grain=n.film.grain);vm.persist(n.copy(adjust=adjust))}) { Text("重置") }
            Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(border,{border=it}); Text("黑色胶片边框") }
        }
        Button(onClick={vm.persist(n.copy(adjust=adjust));vm.export(n.copy(adjust=adjust),border)},enabled=!vm.exporting,modifier=Modifier.fillMaxWidth().padding(bottom=16.dp)) { Text(if(vm.exporting)"正在冲印原图…" else "导出到系统相册") }
    }
}
@Composable fun Control(title:String,value:Float,range:ClosedFloatingPointRange<Float>,change:(Float)->Unit,finish:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(title,modifier=Modifier.width(48.dp),color=Amber)
        Slider(value,onValueChange=change,valueRange=range,onValueChangeFinished=finish,modifier=Modifier.weight(1f))
        Text("%.2f".format(value),style=MaterialTheme.typography.labelSmall,modifier=Modifier.width(42.dp))
    }
}
