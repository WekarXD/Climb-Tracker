package com.climbtracker.core.detection

import kotlin.math.hypot

object Mask {

    /**
     * True where the pixel differs from its local background in colour, or clearly in lightness.
     * Lightness has two thresholds: grey holds on a light wall are only moderately darker than it,
     * while chalk on a dark panel is lighter than it and must not count, so being lighter needs a
     * bigger difference than being darker.
     */
    fun foreground(
        image: LabImage,
        background: LabImage,
        chromaThreshold: Float,
        lighterThreshold: Float,
        darkerThreshold: Float,
        surroundings: LabImage? = null,
    ): BooleanArray {
        val n = image.width * image.height
        val out = BooleanArray(n)
        for (i in 0 until n) {
            var coloured = hypot(image.a[i] - background.a[i], image.b[i] - background.b[i]) > chromaThreshold
            if (surroundings != null) {
                val around = hypot(image.a[i] - surroundings.a[i], image.b[i] - surroundings.b[i])
                coloured = (coloured && around > chromaThreshold) || around > SURE * chromaThreshold
            }
            val light = image.l[i] - background.l[i]
            out[i] = coloured || light > lighterThreshold || -light > darkerThreshold
        }
        return out
    }

    /** Times the colour threshold from which the difference with the surroundings is enough by itself. */
    private const val SURE = 2f

    /**
     * Adds to [mask] the pixels lighter than their background by [weak] that are joined to one
     * lighter by [strong]. A white hold on a dark panel passes the strong threshold only where
     * the light falls on it; the rest of it is still lighter than the panel, and of a piece with
     * that part. Chalk rubbed on the panel is as light as that rest, but has no such part.
     *
     * The pixel must be lighter than its [surroundings] too, the background over a wider window:
     * beside a large dark hold the nearer background is the hold, and the wall is lighter than it.
     */
    fun spreadLight(
        mask: BooleanArray,
        light: FloatArray,
        background: FloatArray,
        surroundings: FloatArray,
        strong: Float,
        weak: Float,
        width: Int,
        height: Int,
    ) {
        val queue = IntArray(mask.size)
        val seen = BooleanArray(mask.size)
        var head = 0
        var tail = 0
        for (i in mask.indices) if (light[i] - background[i] > strong) {
            seen[i] = true
            queue[tail++] = i
        }
        while (head < tail) {
            val p = queue[head++]
            val x = p % width
            val y = p / width
            for (d in 0..3) {
                val nx = x + DX[d]
                val ny = y + DY[d]
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                val q = ny * width + nx
                if (seen[q] || light[q] - background[q] <= weak || light[q] - surroundings[q] <= weak) continue
                seen[q] = true
                mask[q] = true
                queue[tail++] = q
            }
        }
    }

    private val DX = intArrayOf(-1, 1, 0, 0)
    private val DY = intArrayOf(0, 0, -1, 1)

    /** 3x3 cross erosion. Pixels outside the image count as set. */
    fun erode(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val out = BooleanArray(mask.size)
        for (y in 0 until height) for (x in 0 until width) {
            val i = y * width + x
            out[i] = mask[i] &&
                (x == 0 || mask[i - 1]) &&
                (x == width - 1 || mask[i + 1]) &&
                (y == 0 || mask[i - width]) &&
                (y == height - 1 || mask[i + width])
        }
        return out
    }

    /** 3x3 cross dilation. Pixels outside the image count as unset. */
    fun dilate(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val out = BooleanArray(mask.size)
        for (y in 0 until height) for (x in 0 until width) {
            val i = y * width + x
            out[i] = mask[i] ||
                (x > 0 && mask[i - 1]) ||
                (x < width - 1 && mask[i + 1]) ||
                (y > 0 && mask[i - width]) ||
                (y < height - 1 && mask[i + width])
        }
        return out
    }

    fun open(mask: BooleanArray, width: Int, height: Int): BooleanArray =
        dilate(erode(mask, width, height), width, height)

    fun close(mask: BooleanArray, width: Int, height: Int): BooleanArray =
        erode(dilate(mask, width, height), width, height)
}
