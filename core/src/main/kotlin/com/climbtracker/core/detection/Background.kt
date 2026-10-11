package com.climbtracker.core.detection

import kotlin.math.roundToInt

object Background {

    /**
     * The colour of the wall around each pixel of a [width] x [height] image, from [reduced], a
     * smaller copy of it: a median of [radius] over a copy [scale] times smaller still, so the
     * window is far larger than any hold.
     *
     * The median is taken in Lab, not in red, green and blue. Where the window holds a dark mat,
     * a pale wall and a red hold, the middle value of each of red, green and blue comes from a
     * different one of them and together they make a red that fills none of the window; the
     * middle lightness and the middle of each colour axis are those of the wall or the mat.
     */
    fun surroundings(reduced: Planes, scale: Int, radius: Int, width: Int, height: Int): LabImage {
        val small = Filters.downscale(reduced, scale)
        val n = small.width * small.height
        // Lab packed into the 0..255 the median works on.
        val l = IntArray(n)
        val a = IntArray(n)
        val b = IntArray(n)
        for (i in 0 until n) {
            val lab = ColorSpace.toLab(small.r[i], small.g[i], small.b[i])
            l[i] = (lab.l * LIGHTNESS).roundToInt().coerceIn(0, 255)
            a[i] = (lab.a + ZERO).roundToInt().coerceIn(0, 255)
            b[i] = (lab.b + ZERO).roundToInt().coerceIn(0, 255)
        }
        val wide = Filters.upscale(Filters.median(Planes(small.width, small.height, l, a, b), radius), width, height)
        return LabImage(
            width, height,
            FloatArray(width * height) { wide.r[it] / LIGHTNESS },
            FloatArray(width * height) { wide.g[it] - ZERO },
            FloatArray(width * height) { wide.b[it] - ZERO },
        )
    }

    private const val LIGHTNESS = 2.55f
    private const val ZERO = 128f
}
