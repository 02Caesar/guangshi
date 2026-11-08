package com.guangshi.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Observer
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.conflate
import kotlin.math.*

val Amber=Color(0xFFE7B879)
private val Ink=Color(0xFF101112)
private val Soft=Color(0xFF9D9B96)
private val Paper=Color(0xFFF0E9DE)

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            GuangshiTheme { Studio() }
        }
    }
}

@Composable fun GuangshiTheme(content: @Composable ()->Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Amber,onPrimary=Ink,background=Ink,surface=Color(0xFF202020),onSurface=Paper),content=content)
}

@Composable fun Studio(vm: StudioModel=viewModel()) {
    var darkroom by rememberSaveable { mutableStateOf(false) }
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        val text=vm.message
        if(text.isNotBlank()) { vm.message=""; snackbar.showSnackbar(text) }
    }
    Scaffold(containerColor=Ink,snackbarHost={ SnackbarHost(snackbar) },contentWindowInsets=WindowInsets.safeDrawing) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(listOf(if(darkroom)Color(0xFF301B1C) else Color(0xFF252422),Ink)))) {
            Column(Modifier.fillMaxSize().padding(horizontal=18.dp)) {
                Row(Modifier.fillMaxWidth().height(if(darkroom)54.dp else 38.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Column {
                        Text("光室",color=Paper,fontSize=if(darkroom)25.sp else 22.sp,fontFamily=FontFamily.Serif,letterSpacing=6.sp)
                        if(darkroom)Text("让记忆慢慢显影",color=Soft,fontSize=10.sp,letterSpacing=2.sp)
                    }
                    TextButton(onClick={darkroom=!darkroom}) { Text(if(darkroom)"返回相机 ↗" else "暗房 ↗",color=Amber,fontSize=13.sp) }
                }
                if(darkroom) Darkroom(vm,Modifier.weight(1f)) else CameraScreen(vm,{darkroom=true},Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun CameraScreen(vm: StudioModel,open: ()->Unit,modifier: Modifier) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    val view=LocalView.current
    var granted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted=it }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME) granted=context.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val gpu=remember { if(Build.VERSION.SDK_INT>=33)runCatching { GpuFilmEffect(context) }.getOrNull() else null }
    val preview=remember { if(gpu!=null)PreviewView(context).apply {
        implementationMode=PreviewView.ImplementationMode.COMPATIBLE
        scaleType=PreviewView.ScaleType.FILL_CENTER
    } else null }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var catalog by remember { mutableStateOf<List<LensSpec>>(emptyList()) }
    var lensDescription by remember { mutableStateOf("开启相机后读取镜头") }
    var session by remember { mutableStateOf<LiveCamera?>(null) }
    var showLenses by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var connecting by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var edit by rememberSaveable { mutableStateOf("") }
    DisposableEffect(granted,lifecycle,retry) {
        frame=null; capture=null; connecting=true; failed=false
        val controller=if(granted) LiveCamera(context,lifecycle,view.display,
            onFrame={frame=it; connecting=false},onCatalog={catalog=it},
            onLens={mm,plan->vm.focalMm=mm; lensDescription=plan.description},onCapture={capture=it},
            onError={if(!failed)vm.message=it;failed=true;connecting=false},previewView=preview,initialMm=vm.focalMm
        ).also {it.settings.set(RenderSettings(vm.film,vm.cameraAdjust));it.start()} else null
        val streamObserver=Observer<PreviewView.StreamState> {state->if(state==PreviewView.StreamState.STREAMING)connecting=false}
        if(granted)preview?.previewStreamState?.observe(lifecycle,streamObserver)
        session=controller
        onDispose {preview?.previewStreamState?.removeObserver(streamObserver);controller?.close();session=null;capture=null}
    }
    SideEffect { session?.settings?.set(RenderSettings(vm.film,vm.cameraAdjust)) }
    if(showLenses) AlertDialog(onDismissRequest={showLenses=false},title={Text("镜头与光圈")},text={
        Text("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n系统向本应用开放的后置镜头：\n"+
            catalog.joinToString("\n") {"约 ${it.equivalentMm.roundToInt()}mm · ${it.minZoom}–${it.maxZoom}× · 物理光圈 ${it.apertures.joinToString { f->"ƒ/$f" }}"}+
            "\n\n焦段为等效值估算。系统未开放的焦段置灰，高倍率可能是数码裁切。模拟光圈为区域虚化，不控制物理光圈。")
    },confirmButton={TextButton(onClick={showLenses=false}) {Text("知道了")}})
    BoxWithConstraints(modifier) {
        val compact=maxHeight<540.dp
        Column(Modifier.fillMaxWidth().then(if(compact)Modifier.verticalScroll(rememberScrollState()) else Modifier.fillMaxHeight())) {
            ParameterTabs(edit,{edit=if(edit==it)"" else it},listOf("曝光","颗粒","色彩","光圈"))
            Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("${vm.film.title} · ${vm.film.code}",color=Amber,fontSize=10.sp)
                Text("▸ ${"%03d".format(vm.negatives.size+1)}",color=Amber,fontSize=10.sp,fontFamily=FontFamily.Monospace)
            }
            Box(Modifier.fillMaxWidth().then(if(compact)Modifier.height(340.dp) else Modifier.weight(1f)).background(Color.Black)
                .semantics {contentDescription="相机取景区域"},contentAlignment=Alignment.Center) {
                BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    val aspect=if(view.display?.rotation==android.view.Surface.ROTATION_90||view.display?.rotation==android.view.Surface.ROTATION_270)4f/3f else 3f/4f
                    val imageWidth=minOf(maxWidth,maxHeight*aspect)
                    Box(Modifier.width(imageWidth).height(imageWidth/aspect)) {
                        if(granted) {
                            if(preview!=null&&gpu!=null&&Build.VERSION.SDK_INT>=33) {
                                AndroidView(factory={FilteredPreviewHost(it).apply {attachPreview(preview,gpu)}},
                                    update={it.update(RenderSettings(vm.film,vm.cameraAdjust))},modifier=Modifier.fillMaxSize())
                                val latestFocus=rememberUpdatedState<(Float,Float)->Unit> {x,y->vm.cameraAdjust=vm.cameraAdjust.copy(focusX=x,focusY=y);session?.focus(x,y)}
                                Box(Modifier.fillMaxSize().pointerInput(Unit) {detectTapGestures {p->latestFocus.value((p.x/size.width).coerceIn(0f,1f),(p.y/size.height).coerceIn(0f,1f))}})
                                if(vm.cameraAdjust.aperture<16f)FocusMark(vm.cameraAdjust,Modifier.fillMaxSize())
                            } else FrameImage(frame,Modifier.fillMaxSize(),onPoint={x,y->vm.cameraAdjust=vm.cameraAdjust.copy(focusX=x,focusY=y);session?.focus(x,y)},focus=if(vm.cameraAdjust.aperture<16f)vm.cameraAdjust else null)
                            if(connecting&&!failed)CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.Center),color=Amber,strokeWidth=2.dp)
                            if(failed)TextButton(onClick={retry++},modifier=Modifier.align(Alignment.Center)) {Text("重新连接镜头")}
                        } else Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                            Text("让光进入镜头",fontFamily=FontFamily.Serif,fontSize=22.sp,color=Paper)
                            Text("照片只留在本机",fontSize=12.sp,color=Soft,modifier=Modifier.padding(12.dp))
                            Button(onClick={permission.launch(Manifest.permission.CAMERA)}) {Text("开启相机")}
                            TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}) {Text("权限设置",fontSize=12.sp)}
                        }
                        if(edit.isNotEmpty()) {
                            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha=.86f)).padding(horizontal=10.dp,vertical=4.dp)
                                .heightIn(max=if(edit=="色彩")232.dp else 180.dp).verticalScroll(rememberScrollState())) {
                                AdjustmentPanel(edit,vm.cameraAdjust,vm.film,{vm.cameraAdjust=it},{})
                                TextButton(onClick={edit=""},modifier=Modifier.align(Alignment.End).height(30.dp),contentPadding=PaddingValues(horizontal=10.dp,vertical=0.dp)) {Text("收起",fontSize=10.sp)}
                            }
                        }
                        if(granted) LazyRow(Modifier.align(Alignment.BottomCenter).padding(bottom=8.dp).background(Color.Black.copy(alpha=.38f),RoundedCornerShape(24.dp)).padding(horizontal=5.dp,vertical=3.dp),
                            horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically) {
                            items(listOf(15,24,49,73,146)) {group->
                                val selected=when(group) {24->vm.focalMm in listOf(24,28,35);73->vm.focalMm in listOf(73,85);else->vm.focalMm==group}
                                val target=if(selected)nextFocal(group,vm.focalMm) else group
                                val supported=catalog.isNotEmpty()&&chooseLens(catalog,target)!=null
                                Choice(if(selected)"${vm.focalMm}" else "$group",selected,supported&&!vm.shooting) {capture=null;session?.selectFocal(target)}
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(22.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text(vm.film.note,color=Soft,fontSize=9.sp)
                Text("镜头 ⓘ",color=Amber,fontSize=10.sp,modifier=Modifier.clickable {showLenses=true}.padding(horizontal=8.dp))
            }
            FilmStrip(vm.film,{if(!vm.shooting)vm.chooseFilm(it)},Modifier.padding(vertical=4.dp))
            Row(Modifier.fillMaxWidth().height(98.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Box(Modifier.size(96.dp),contentAlignment=Alignment.Center) {FilmDial(vm.film.ordinal,{if(!vm.shooting)vm.chooseFilm(Film.entries[it])})}
                Box(Modifier.size(80.dp).border(1.dp,Amber.copy(alpha=.5f),CircleShape).padding(5.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Paper,Color(0xFFBFB6A7))))
                    .clickable(enabled=capture!=null&&!connecting&&!vm.shooting) {view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);vm.capture(capture)}
                    .semantics {contentDescription=if(vm.shooting)"正在保存照片" else "快门"},contentAlignment=Alignment.Center) {
                    if(vm.shooting)CircularProgressIndicator(Modifier.size(26.dp),color=Ink,strokeWidth=2.dp)else Box(Modifier.size(60.dp).border(1.dp,Color(0xFF988C78),CircleShape))
                }
                Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.width(68.dp).clickable(onClick=open)) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(3.dp)).border(1.dp,Amber.copy(alpha=.4f)).background(Color.Black).padding(3.dp),contentAlignment=Alignment.Center) {
                        vm.negatives.firstOrNull()?.let {Thumb(vm,it)}?:Text("＋",color=Amber,fontSize=20.sp)
                    }
                    Text("底片盒",color=Soft,fontSize=10.sp,modifier=Modifier.padding(top=4.dp))
                }
            }
        }
    }
}

@Composable private fun FocusMark(a:Adjust,modifier:Modifier) {
    Canvas(modifier) {
        val p=Offset(a.focusX*size.width,a.focusY*size.height)
        drawCircle(Amber.copy(alpha=.8f),14.dp.toPx(),p,style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        drawCircle(Amber,1.5.dp.toPx(),p)
    }
}

@Composable private fun Choice(text: String,selected: Boolean,enabled: Boolean=true,onClick: ()->Unit) {
    Box(Modifier.heightIn(min=36.dp).clip(RoundedCornerShape(20.dp))
        .background(if(selected)Amber else Color(0xFF282827))
        .border(.5.dp,if(selected)Amber else Color(0xFF484643),RoundedCornerShape(20.dp))
        .clickable(enabled=enabled,onClick=onClick)
        .semantics { this.selected=selected; if(!enabled)disabled() }
        .padding(horizontal=14.dp,vertical=8.dp),contentAlignment=Alignment.Center) {
        Text(text,color=if(selected)Ink else if(enabled)Paper else Soft.copy(alpha=.35f),fontSize=11.sp)
    }
}

@Composable private fun FilmStrip(current: Film,choose: (Film)->Unit,modifier: Modifier=Modifier) {
    val state=rememberLazyListState()
    LaunchedEffect(current) { state.animateScrollToItem(current.ordinal) }
    LazyRow(modifier.fillMaxWidth(),state=state,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        items(Film.entries) { film -> Choice(film.title,current==film) { choose(film) } }
    }
}

@Composable private fun FilmDial(selected: Int,onChange: (Int)->Unit) {
    val view=LocalView.current
    var turns by remember { mutableFloatStateOf(selected.toFloat()) }
    var previous by remember { mutableIntStateOf(selected) }
    LaunchedEffect(selected) {
        var delta=selected-previous
        if(delta>3)delta-=6
        if(delta< -3)delta+=6
        turns+=delta; previous=selected
    }
    val angle by animateFloatAsState(-turns*60f,spring(dampingRatio=.7f,stiffness=250f),label="机械吸附")
    val latest=rememberUpdatedState(selected)
    val choose=rememberUpdatedState(onChange)
    var drag by remember { mutableFloatStateOf(0f) }
    Box(Modifier.size(112.dp).semantics {
        contentDescription="胶卷转盘，当前${Film.entries[selected].title}"
        stateDescription=Film.entries[selected].title
        customActions=listOf(CustomAccessibilityAction("下一个滤镜") { choose.value((latest.value+1)%6); true },CustomAccessibilityAction("上一个滤镜") { choose.value((latest.value+5)%6); true })
    }.pointerInput(Unit) {
        detectDragGestures(onDragStart={drag=0f},onDragEnd={drag=0f},onDragCancel={drag=0f}) { change,amount ->
            change.consume()
            val v=change.position-Offset(size.width/2f,size.height/2f)
            drag+=(v.x*amount.y-v.y*amount.x)/max(v.getDistance(),1f)
            if(abs(drag)>20.dp.toPx()) {
                val next=(latest.value+(if(drag>0)-1 else 1)+6)%6
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); choose.value(next); drag=0f
            }
        }
    },contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize().rotate(angle)) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF464440),Color(0xFF181918))))
            drawCircle(Color(0xFF171818),radius=size.minDimension*.36f)
            for(i in 0 until 60) {
                val a=i*PI/30; val outer=size.minDimension*.475f; val inner=outer-if(i%10==0)10.dp.toPx() else 4.dp.toPx()
                drawLine(if(i%10==0)Amber else Soft.copy(alpha=.5f),center+Offset(cos(a).toFloat()*inner,sin(a).toFloat()*inner),center+Offset(cos(a).toFloat()*outer,sin(a).toFloat()*outer),1.dp.toPx())
            }
            val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.rgb(231,184,121); textSize=9.dp.toPx(); textAlign=android.graphics.Paint.Align.CENTER }
            listOf("00","400","800","100","200","500").forEachIndexed { i,label ->
                val a=-PI/2+i*PI/3
                drawContext.canvas.nativeCanvas.drawText(label,center.x+cos(a).toFloat()*size.minDimension*.27f,center.y+sin(a).toFloat()*size.minDimension*.27f+paint.textSize/3,paint)
            }
        }
        Text("光",fontFamily=FontFamily.Serif,color=Amber,fontSize=18.sp)
        Text("▼",color=Amber,fontSize=10.sp,modifier=Modifier.align(Alignment.TopCenter))
    }
}

@Composable private fun FrameImage(bitmap: Bitmap?,modifier: Modifier=Modifier,onPoint: ((Float,Float)->Unit)?=null,focus: Adjust?=null) {
    val latestPoint=rememberUpdatedState(onPoint)
    BoxWithConstraints(modifier,contentAlignment=Alignment.Center) {
        if(bitmap!=null) {
            val aspect=bitmap.width.toFloat()/bitmap.height
            val width=minOf(maxWidth,maxHeight*aspect)
            Box(Modifier.width(width).height(width/aspect).then(if(onPoint!=null)Modifier.pointerInput(Unit) { detectTapGestures { latestPoint.value?.invoke((it.x/size.width).coerceIn(0f,1f),(it.y/size.height).coerceIn(0f,1f)) } } else Modifier)) {
                Image(bitmap.asImageBitmap(),"实时画面",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
                if(focus!=null) Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Amber.copy(alpha=.8f),radius=14.dp.toPx(),center=Offset(focus.focusX*size.width,focus.focusY*size.height),style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    drawCircle(Amber,radius=1.5.dp.toPx(),center=Offset(focus.focusX*size.width,focus.focusY*size.height))
                }
            }
        }
    }
}

@Composable private fun Thumb(vm: StudioModel,n: Negative) {
    var bitmap by remember(n.id,n.film,n.adjust) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(n.id,n.film,n.adjust) {
        try { bitmap=withContext(Dispatchers.Default) { val raw=vm.store.load(n.id,140); try { develop(raw,n.film,n.adjust) } finally { raw.recycle() } } }
        catch(e:CancellationException) { throw e }
        catch(_:Exception) { bitmap=null }
    }
    bitmap?.let { Image(it.asImageBitmap(),"${n.film.title}底片",Modifier.fillMaxSize(),contentScale=ContentScale.Crop) }
}

@Composable private fun Darkroom(vm: StudioModel,modifier: Modifier) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val n=vm.negatives.find { it.id==selected }?:vm.negatives.firstOrNull()
    var adjust by remember(n?.id) { mutableStateOf(n?.adjust?:Adjust()) }
    var film by remember(n?.id) { mutableStateOf(n?.film?:Film.NATURAL) }
    var compare by remember { mutableStateOf(false) }
    var border by rememberSaveable { mutableStateOf(true) }
    var edit by rememberSaveable { mutableStateOf("曝光") }
    var raw by remember(n?.id) { mutableStateOf<Bitmap?>(null) }
    var rendered by remember(n?.id) { mutableStateOf<Bitmap?>(null) }
    var error by remember(n?.id) { mutableStateOf("") }
    LaunchedEffect(n?.id) {
        if(n!=null) try { raw=withContext(Dispatchers.IO) { vm.store.load(n.id,800) } }
        catch(e:CancellationException) { throw e }
        catch(_:Exception) { error="底片读取失败，原片未删除" }
    }
    // Conflate keeps the newest slider request without cancelling every in-flight image.
    // It therefore keeps producing images while the finger is still moving.
    LaunchedEffect(raw,n?.id) {
        val source=raw?:return@LaunchedEffect
        snapshotFlow { RenderSettings(film,adjust) }.conflate().collect { settings ->
            try { rendered=withContext(Dispatchers.Default) { develop(source,settings.film,settings.adjust) }; error="" }
            catch(e:CancellationException) { throw e }
            catch(_:Exception) { error="预览处理失败，请重置参数重试" }
        }
    }
    if(n==null) {
        Box(modifier.fillMaxWidth(),contentAlignment=Alignment.Center) { Text("底片盒还是空的\n回到相机，拍下第一束光。",color=Amber,lineHeight=30.sp) }
        return
    }
    fun snapshot()=n.copy(film=film,adjust=adjust)
    BoxWithConstraints(modifier) {
    val compact=maxHeight<560.dp
    Column(Modifier.fillMaxWidth().then(if(compact)Modifier.verticalScroll(rememberScrollState()) else Modifier.fillMaxHeight())) {
        Row(Modifier.fillMaxWidth().padding(vertical=10.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("${film.code}   /   ${n.focalMm} mm",color=Amber,fontSize=10.sp,letterSpacing=1.sp)
            Text("▸ ${n.id.takeLast(4).uppercase()}",color=Amber,fontSize=10.sp,fontFamily=FontFamily.Monospace)
        }
        Box(Modifier.fillMaxWidth().then(if(compact)Modifier.height(220.dp) else Modifier.weight(1f).heightIn(min=130.dp)).background(Color.Black).padding(8.dp),contentAlignment=Alignment.Center) {
            FrameImage(if(compare)raw else rendered,Modifier.fillMaxSize(),onPoint={x,y->adjust=adjust.copy(focusX=x,focusY=y);vm.persist(snapshot())},focus=if(!compare&&edit=="光圈"&&adjust.aperture<16)adjust else null)
            if(rendered==null)Text(if(error.isEmpty())"显影中…" else error,color=Amber,fontSize=12.sp)
        }
        Row(Modifier.fillMaxWidth().padding(vertical=5.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text(if(compare)"原始底片" else film.note,color=Soft,fontSize=10.sp)
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF48292A)).pointerInput(Unit) { detectTapGestures(onPress={compare=true;try { tryAwaitRelease() } finally {compare=false} }) }.padding(horizontal=12.dp,vertical=10.dp)) { Text("按住看原片",color=Amber,fontSize=11.sp) }
        }
        LazyRow(Modifier.fillMaxWidth().height(76.dp).padding(vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            items(vm.negatives,key={it.id}) { item ->
                Box(Modifier.width(64.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Color.Black)
                    .border(if(item.id==n.id)1.5.dp else .5.dp,if(item.id==n.id)Amber else Color(0xFF4B4640)).padding(4.dp).clickable { vm.persist(snapshot());selected=item.id }) { Thumb(vm,item) }
            }
        }
        FilmStrip(film,{film=it; adjust=adjust.copy(grain=TickScale(0f,1f).snap(it.grain)); vm.persist(snapshot())},Modifier.padding(vertical=6.dp))
        ParameterTabs(edit,{edit=it},listOf("曝光","层次","色彩","颗粒","光圈"))
        Box(Modifier.fillMaxWidth().heightIn(max=190.dp).verticalScroll(rememberScrollState())) {
            AdjustmentPanel(edit,adjust,film,{adjust=it},{vm.persist(snapshot())})
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={adjust=Adjust(grain=TickScale(0f,1f).snap(film.grain));vm.persist(snapshot())}) { Text("重置",fontSize=12.sp) }
            Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(border,{border=it}); Text("黑色胶片边框",color=Soft,fontSize=11.sp) }
        }
        Button(onClick={val snapshot=snapshot();vm.persist(snapshot);vm.export(snapshot,border)},enabled=!vm.exporting&&raw!=null,
            shape=RoundedCornerShape(5.dp),modifier=Modifier.fillMaxWidth().height(46.dp)) { Text(if(vm.exporting)"正在冲印原图…" else "冲印 · 导出到系统相册",letterSpacing=1.sp) }
        Spacer(Modifier.height(10.dp))
    }
    }
    // Save the latest controls when returning to the camera as well as when releasing a slider.
    val finalSnapshot=remember(n.id) {java.util.concurrent.atomic.AtomicReference(snapshot())}
    SideEffect {finalSnapshot.set(snapshot())}
    DisposableEffect(n.id) {onDispose {vm.persist(finalSnapshot.get())}}
}

@Composable private fun ParameterTabs(current: String,choose: (String)->Unit,values: List<String>) {
    LazyRow(Modifier.fillMaxWidth().padding(vertical=2.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        items(values) { name ->
            Column(Modifier.clickable { choose(name) }.padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text(name,color=if(current==name)Amber else Soft,fontSize=12.sp)
                Spacer(Modifier.height(5.dp))
                Box(Modifier.width(12.dp).height(1.dp).background(if(current==name)Amber else Color.Transparent))
            }
        }
    }
}

@Composable private fun AdjustmentPanel(mode: String,a: Adjust,film: Film,change: (Adjust)->Unit,finish: ()->Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal=4.dp)) {
        when(mode) {
            "曝光" -> Control("曝光 EV",a.exposure,-3f..3f,{change(a.copy(exposure=it))},finish)
            "颗粒" -> Control("颗粒",a.grain,0f..1f,{change(a.copy(grain=it))},finish)
            "层次" -> {
                Control("反差",a.contrast,.5f..1.5f,{change(a.copy(contrast=it))},finish)
                if(film!=Film.MONO) {
                    Control("饱和度",a.saturation,0f..1.5f,{change(a.copy(saturation=it))},finish)
                    Control("冷暖",a.warmth,-1f..1f,{change(a.copy(warmth=it))},finish)
                }
            }
            "色彩" -> if(film==Film.MONO) Text("银盐黑白保留曝光、层次与颗粒",fontSize=11.sp,color=Soft,modifier=Modifier.padding(vertical=16.dp)) else {
                Control("红 R",-a.cyan,-1f..1f,{change(a.copy(cyan=-it))},finish)
                Control("绿 G",-a.magenta,-1f..1f,{change(a.copy(magenta=-it))},finish)
                Control("蓝 B",-a.yellow,-1f..1f,{change(a.copy(yellow=-it))},finish)
            }
            "光圈" -> {
                Control("模拟光圈",a.aperture,1.4f..16f,{change(a.copy(aperture=it))},finish)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    items(listOf(1.4f,2.8f,5.6f,8f,16f)) {value->Choice(if(value==16f)"关闭" else "$value",a.aperture==value) {change(a.copy(aperture=value));finish()}}
                }
                Text("点击画面选择清晰区域",fontSize=9.sp,color=Soft,modifier=Modifier.padding(vertical=6.dp))
            }
        }
    }
}

@Composable private fun Control(title:String,value:Float,range:ClosedFloatingPointRange<Float>,change:(Float)->Unit,finish:()->Unit) {
    TickRuler(title,value,range,change,finish)
}
