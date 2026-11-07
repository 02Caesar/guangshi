package com.guangshi.app

import kotlin.math.*

internal data class LensSpec(val id: String, val equivalentMm: Float, val minZoom: Float, val maxZoom: Float, val apertures: List<Float> = emptyList())
internal data class LensPlan(val lens: LensSpec, val zoom: Float) {
    val description: String get() = if(abs(zoom-1f)<.03f) "约 ${lens.equivalentMm.roundToInt()}mm 镜头" else "${"%.2f".format(zoom)}× 系统变焦 / 裁切"
}
internal fun chooseLens(lenses: List<LensSpec>, mm: Int): LensPlan? = lenses.mapNotNull { lens ->
    val requested=mm/lens.equivalentMm
    val zoom=if(abs(requested-1f)<.04f && lens.minZoom<=1f && lens.maxZoom>=1f)1f else requested
    if(zoom >= lens.minZoom-.005f && zoom <= lens.maxZoom+.005f) LensPlan(lens,zoom.coerceIn(lens.minZoom,lens.maxZoom)) else null
}.minByOrNull { abs(ln(it.zoom)) }
internal fun nextFocal(group: Int, current: Int): Int = when(group) {
    24 -> when(current) { 24->28; 28->35; else->24 }
    73 -> if(current==73)85 else 73
    else -> group
}
