package com.climbtracker.core.detection

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The square frames the models take: the photo scaled by its longer side into the top left corner. */
object Frames {

    /**
     * Three colour planes, 0..255, of a frame of [side] pixels with [image] scaled into its top
     * left corner; what the photo does not fill is [fill]. Red comes first, or blue with
     * [blueFirst].
     */
    fun planes(image: PixelImage, side: Int, blueFirst: Boolean = false, fill: Float = 0f): FloatArray {
        val scale = side / max(image.width, image.height).toFloat()
        val width = (image.width * scale).roundToInt().coerceIn(1, side)
        val height = (image.height * scale).roundToInt().coerceIn(1, side)
        val plane = side * side
        val data = FloatArray(3 * plane)
        if (fill != 0f) data.fill(fill)
        val stepX = image.width / width.toFloat()
        val stepY = image.height / height.toFloat()
        for (y in 0 until height) {
            // Each pixel of the frame is the blend of the four of the photo around its centre.
            val sy = ((y + 0.5f) * stepY - 0.5f).coerceIn(0f, image.height - 1f)
            val ya = sy.toInt()
            val yb = min(ya + 1, image.height - 1)
            val fy = sy - ya
            for (x in 0 until width) {
                val sx = ((x + 0.5f) * stepX - 0.5f).coerceIn(0f, image.width - 1f)
                val xa = sx.toInt()
                val xb = min(xa + 1, image.width - 1)
                val fx = sx - xa
                val a = image.argb[ya * image.width + xa]
                val b = image.argb[ya * image.width + xb]
                val c = image.argb[yb * image.width + xa]
                val d = image.argb[yb * image.width + xb]
                val i = y * side + x
                for (channel in 0 until 3) {
                    val shift = if (blueFirst) 8 * channel else 16 - 8 * channel
                    val top = ((a shr shift) and 0xFF) * (1 - fx) + ((b shr shift) and 0xFF) * fx
                    val bottom = ((c shr shift) and 0xFF) * (1 - fx) + ((d shr shift) and 0xFF) * fx
                    data[channel * plane + i] = top * (1 - fy) + bottom * fy
                }
            }
        }
        return data
    }
}
