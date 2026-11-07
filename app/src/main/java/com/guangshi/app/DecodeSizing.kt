package com.guangshi.app

/** Uses Long arithmetic: Int.MAX_VALUE * 2 previously overflowed and sample became zero. */
internal object DecodeSizing {
    fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        require(width > 0 && height > 0) { "照片尺寸无效" }
        require(maxEdge > 0) { "读取尺寸必须大于零" }
        val longest = maxOf(width, height).toLong()
        val limit = maxEdge.toLong() * 2L
        var sample = 1
        while (longest / sample > limit && sample <= Int.MAX_VALUE / 2) sample *= 2
        return sample
    }
}
