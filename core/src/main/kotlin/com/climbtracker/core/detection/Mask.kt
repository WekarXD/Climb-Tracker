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
    ): BooleanArray {
        val n = image.width * image.height
        val out = BooleanArray(n)
        for (i in 0 until n) {
            val chroma = hypot(image.a[i] - background.a[i], image.b[i] - background.b[i])
            val light = image.l[i] - background.l[i]
            out[i] = chroma > chromaThreshold || light > lighterThreshold || -light > darkerThreshold
        }
        return out
    }

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
