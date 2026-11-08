package com.guangshi.app

import kotlin.math.roundToInt

/** Store positions as integer tenths so repeated +/- cannot accumulate floating-point drift. */
internal data class TickScale(val minimum: Float,val maximum: Float) {
    private val first=(minimum*10).roundToInt()
    private val last=(maximum*10).roundToInt()
    init { require(last>first) }
    val intervals get()=last-first
    fun index(value: Float)=((value*10).roundToInt()-first).coerceIn(0,intervals)
    fun value(index: Int)=(first+index.coerceIn(0,intervals))/10f
    fun snap(value: Float)=value(index(value))
    fun move(value: Float,ticks: Int)=value(index(value)+ticks)
}
