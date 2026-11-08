package com.guangshi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable fun NegativeManager(vm: StudioModel, close: () -> Unit) {
    var trash by rememberSaveable { mutableStateOf(false) }
    var selection by remember { mutableStateOf(emptySet<String>()) }
    var confirm by remember { mutableStateOf(false) }
    val entries=if(trash)vm.trash else vm.negatives
    LaunchedEffect(entries,trash) { selection=selection.intersect(entries.map { it.id }.toSet()) }
    Dialog(onDismissRequest={if(!vm.managing)close()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=Color(0xFF171615)) {
            Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text(if(trash)"回收站" else "管理底片",color=Amber,fontSize=22.sp)
                    TextButton(onClick=close,enabled=!vm.managing) { Text("完成") }
                }
                Row {
                    TextButton(onClick={trash=false;selection=emptySet()},enabled=!vm.managing) { Text("底片盒（${vm.negatives.size}）") }
                    TextButton(onClick={trash=true;selection=emptySet()},enabled=!vm.managing) { Text("回收站（${vm.trash.size}）") }
                }
                Text(if(trash)"原片与调色参数均已保留，可随时恢复。" else "点按选择底片，删除后可在回收站恢复。",color=Color.LightGray,fontSize=12.sp)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick={selection=if(selection.size==entries.size)emptySet() else entries.map { it.id }.toSet()},enabled=!vm.managing&&entries.isNotEmpty()) { Text(if(selection.size==entries.size&&entries.isNotEmpty())"取消全选" else "全选") }
                    Text("已选 ${selection.size} 张",color=Amber,modifier=Modifier.padding(12.dp))
                }
                if(entries.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) { Text(if(trash)"回收站是空的" else "底片盒是空的",color=Amber) }
                else LazyVerticalGrid(GridCells.Adaptive(96.dp),Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(entries,key={it.id}) { item ->
                        val chosen=item.id in selection
                        Column(Modifier.background(if(chosen)Color(0xFF493923) else Color.Black).clickable(enabled=!vm.managing) {
                            selection=if(chosen)selection-item.id else selection+item.id
                        }.semantics { selected=chosen; contentDescription="选择底片 ${item.id}" }.padding(6.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(.8f)) { Thumb(vm,item) }
                            Text("${if(chosen)"✓" else "○"} ${item.film.title}",color=Amber,fontSize=11.sp)
                            Text(item.id.takeLast(8),color=Color.LightGray,fontSize=10.sp)
                        }
                    }
                }
                Button(onClick={if(trash) {vm.moveNegatives(selection,false);selection=emptySet()} else confirm=true},enabled=selection.isNotEmpty()&&!vm.managing,modifier=Modifier.fillMaxWidth()) {
                    Text(if(vm.managing)"正在处理…" else if(trash)"恢复所选（${selection.size}）" else "删除所选（${selection.size}）")
                }
            }
        }
        if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("移入回收站？")},text={Text("所选 ${selection.size} 张底片的原片和调色参数将保留，可在回收站恢复。系统相册中的成片不受影响。")},confirmButton={TextButton(onClick={confirm=false;vm.moveNegatives(selection,true);selection=emptySet()}) {Text("移入回收站")}},dismissButton={TextButton(onClick={confirm=false}) {Text("取消")}})
    }
}
