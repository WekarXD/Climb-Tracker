package com.climbtracker.core.image

import kotlin.math.max
import kotlin.math.roundToInt

object ImageMath {

    /**
     * Largest power-of-two subsampling that keeps the long side at or above [maxSide].
     * Used to decode very large photos without exhausting memory.
     */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        if (maxSide <= 0) return 1
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    /** Size with the same aspect ratio whose long side is at most [maxSide]; never enlarges. */
    fun fitSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        val long = max(width, height)
        if (long <= maxSide) return width to height
        val scale = maxSide.toFloat() / long
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }
}
