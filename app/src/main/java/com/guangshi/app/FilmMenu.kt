package com.guangshi.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun FilmMenu(current:Film,choose:(Film)->Unit,close:()->Unit) {
    AlertDialog(onDismissRequest=close,title={Text("装入一卷光",fontSize=18.sp)},text={
        Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Film.entries.forEach {film->
                Row(Modifier.fillMaxWidth().border(1.dp,if(current==film)Amber else Color(0xFF414145),RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF343439),Color(0xFF18181B))),RoundedCornerShape(10.dp))
                    .clickable {choose(film)}.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.width(65.dp).height(42.dp).background(Brush.horizontalGradient(listOf(Color.Black,Color(0xFF656568),Color.Black)),RoundedCornerShape(6.dp)).border(.5.dp,Color.Gray,RoundedCornerShape(6.dp)),contentAlignment=Alignment.Center) {
                        Text(film.code,color=Amber,fontSize=10.sp)
                    }
                    Column(Modifier.padding(start=12.dp)) {Text(film.title,color=if(current==film)Amber else Color.White,fontSize=13.sp);Text(film.note,color=Color.LightGray,fontSize=10.sp)}
                }
            }
        }
    },confirmButton={TextButton(onClick=close) {Text("返回取景")}})
}
