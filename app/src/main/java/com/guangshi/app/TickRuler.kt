package com.guangshi.app

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

@Composable internal fun TickRuler(title:String,value:Float,range:ClosedFloatingPointRange<Float>,change:(Float)->Unit,finish:()->Unit) {
    val scale=remember(range.start,range.endInclusive) { TickScale(range.start,range.endInclusive) }
    val current=rememberUpdatedState(value)
    val onChange=rememberUpdatedState(change)
    val onFinish=rememberUpdatedState(finish)
    val view=LocalView.current
    val index=scale.index(value)
    val latestIndex=rememberUpdatedState(index)
    fun choose(next:Int) {
        val snapped=scale.value(next)
        if(snapped!=current.value) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onChange.value(snapped)
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical=2.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Text(title,color=Color(0xFFC2BAB0),fontSize=11.sp,modifier=Modifier.align(Alignment.CenterStart))
            Text(if(title=="模拟光圈"&&value>=16f)"关闭" else "%.1f".format(if(abs(value)<.05f)0f else scale.snap(value)),
                color=Amber,fontSize=15.sp,fontFamily=FontFamily.Monospace,modifier=Modifier.align(Alignment.Center))
        }
        Row(Modifier.fillMaxWidth().height(46.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={choose(latestIndex.value-1);onFinish.value()},modifier=Modifier.width(42.dp).semantics {contentDescription="$title 减少 0.1"},contentPadding=PaddingValues(0.dp)) {Text("−",color=Amber,fontSize=21.sp)}
            Canvas(Modifier.weight(1f).fillMaxHeight()
                .semantics {
                    contentDescription=title
                    progressBarRangeInfo=ProgressBarRangeInfo(scale.snap(value),range,scale.intervals-1)
                    setProgress { requested -> onChange.value(scale.snap(requested));onFinish.value();true }
                }
                .pointerInput(scale) {
                    var pending=0f
                    var dragIndex=0
                    val spacing=9.dp.toPx()
                    detectDragGestures(onDragStart={pending=0f;dragIndex=latestIndex.value},onDragEnd={onFinish.value()},onDragCancel={onFinish.value()}) { event,amount ->
                        event.consume();pending+=amount.x
                        val ticks=(-pending/spacing).toInt()
                        if(ticks!=0) {
                            pending+=ticks*spacing
                            val next=(dragIndex+ticks).coerceIn(0,scale.intervals)
                            if(next!=dragIndex) {dragIndex=next;choose(next)}
                        }
                    }
                }
                .pointerInput(scale) {detectTapGestures {p->choose(latestIndex.value+((p.x-size.width/2f)/9.dp.toPx()).roundToInt());onFinish.value()}}
            ) {
                val spacing=9.dp.toPx()
                val half=(size.width/(2*spacing)).toInt()+1
                val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=android.graphics.Color.rgb(170,164,154);textAlign=android.graphics.Paint.Align.CENTER;textSize=9.sp.toPx()}
                for(offset in -half..half) {
                    val i=index+offset
                    if(i !in 0..scale.intervals)continue
                    val x=size.width/2+offset*spacing
                    val tenths=(scale.value(i)*10).roundToInt()
                    val major=tenths%10==0
                    val middle=tenths%5==0
                    drawLine(Color(0xFFBDB5A8).copy(alpha=if(major).8f else .45f),Offset(x,4.dp.toPx()),Offset(x,(if(major)24 else if(middle)18 else 13).dp.toPx()),1.dp.toPx())
                    if(major&&offset!=0)drawContext.canvas.nativeCanvas.drawText("%.1f".format(scale.value(i)),x,size.height-3.dp.toPx(),paint)
                }
                drawLine(Amber,Offset(size.width/2,0f),Offset(size.width/2,28.dp.toPx()),2.dp.toPx())
            }
            TextButton(onClick={choose(latestIndex.value+1);onFinish.value()},modifier=Modifier.width(42.dp).semantics {contentDescription="$title 增加 0.1"},contentPadding=PaddingValues(0.dp)) {Text("＋",color=Amber,fontSize=20.sp)}
        }
    }
}
