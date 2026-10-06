package com.climbtracker.core.image

import com.climbtracker.core.detection.PixelImage
import com.climbtracker.core.detection.PointF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The stretch of wall to keep from a photo, as its four corners in normalised image coordinates
 * (0..1). A photo taken from one side or from below shows the wall as a slanted shape; marking
 * its corners lets it be straightened into a rectangle. With the corners square it is a plain
 * crop.
 */
data class CropQuad(val topLeft: PointF, val topRight: PointF, val bottomRight: PointF, val bottomLeft: PointF) {

    /** Clockwise from the top left. */
    val corners: List<PointF> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** The upright rectangle with these corners, or null when the shape is slanted. */
    fun asRect(): CropRect? {
        val square = topLeft.x == bottomLeft.x && topRight.x == bottomRight.x && topLeft.y == topRight.y && bottomLeft.y == bottomRight.y
        return if (square) CropRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y) else null
    }

    /** The smallest upright rectangle that holds the shape. */
    fun bounds(): CropRect = CropRect(
        corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y },
    )

    /**
     * The shape after dragging [handle] by ([dx], [dy]), both normalised to the image size. A
     * corner moves alone and stays inside the image; a move that would fold the shape over, or
     * leave a side shorter than [minSize], is not made. MOVE shifts the whole shape.
     */
    fun dragged(handle: CropHandle, dx: Float, dy: Float, minSize: Float = 0.1f): CropQuad {
        fun PointF.moved() = PointF((x + dx).coerceIn(0f, 1f), (y + dy).coerceIn(0f, 1f))
        val moved = when (handle) {
            CropHandle.TOP_LEFT -> copy(topLeft = topLeft.moved())
            CropHandle.TOP_RIGHT -> copy(topRight = topRight.moved())
            CropHandle.BOTTOM_RIGHT -> copy(bottomRight = bottomRight.moved())
            CropHandle.BOTTOM_LEFT -> copy(bottomLeft = bottomLeft.moved())
            CropHandle.MOVE -> {
                val box = bounds()
                val sx = dx.coerceIn(-box.left, 1f - box.right)
                val sy = dy.coerceIn(-box.top, 1f - box.bottom)
                fun PointF.shifted() = PointF(x + sx, y + sy)
                CropQuad(topLeft.shifted(), topRight.shifted(), bottomRight.shifted(), bottomLeft.shifted())
            }
        }
        return if (moved.isUsable(minSize)) moved else this
    }

    /** True for a shape that bulges outwards at every corner and has no side shorter than [minSize]. */
    fun isUsable(minSize: Float = 0.1f): Boolean {
        val c = corners
        for (i in 0 until 4) {
            val a = c[i]
            val b = c[(i + 1) % 4]
            val d = c[(i + 2) % 4]
            if (hypot(b.x - a.x, b.y - a.y) < minSize) return false
            // Clockwise on screen, where y grows downwards, every turn has a positive cross product.
            val turn = (b.x - a.x) * (d.y - b.y) - (b.y - a.y) * (d.x - b.x)
            if (turn < minSize * minSize / 2) return false
        }
        return true
    }

    /**
     * Size of the straightened picture for a photo of [width] x [height] pixels: as wide as the
     * longer of the top and bottom sides, as tall as the longer of the other two.
     */
    fun outputSize(width: Int, height: Int): Pair<Int, Int> {
        fun length(a: PointF, b: PointF) = hypot((b.x - a.x) * width, (b.y - a.y) * height)
        val w = max(length(topLeft, topRight), length(bottomLeft, bottomRight))
        val h = max(length(topLeft, bottomLeft), length(topRight, bottomRight))
        return max(1, w.roundToInt()) to max(1, h.roundToInt())
    }

    /**
     * Where the point ([u], [v]) of the straightened picture, both 0..1, lies in the photo. The
     * corners of the picture land on the corners of the shape, and straight lines stay straight.
     */
    fun toPhoto(u: Float, v: Float): PointF {
        val k = coefficients()
        val w = k[6] * u + k[7] * v + 1f
        return PointF((k[0] * u + k[1] * v + k[2]) / w, (k[3] * u + k[4] * v + k[5]) / w)
    }

    /** The projective map from the unit square onto the shape: a, b, c, d, e, f, g, h. */
    private fun coefficients(): FloatArray {
        val (x0, y0) = topLeft
        val (x1, y1) = topRight
        val (x2, y2) = bottomRight
        val (x3, y3) = bottomLeft
        val dx1 = x1 - x2
        val dx2 = x3 - x2
        val dy1 = y1 - y2
        val dy2 = y3 - y2
        val sx = x0 - x1 + x2 - x3
        val sy = y0 - y1 + y2 - y3
        val det = dx1 * dy2 - dx2 * dy1
        // A parallelogram needs no foreshortening, and the two terms vanish.
        val g = if (det == 0f) 0f else (sx * dy2 - dx2 * sy) / det
        val h = if (det == 0f) 0f else (dx1 * sy - sx * dy1) / det
        return floatArrayOf(
            x1 - x0 + g * x1, x3 - x0 + h * x3, x0,
            y1 - y0 + g * y1, y3 - y0 + h * y3, y0,
            g, h,
        )
    }

    /** The part of [image] inside the shape, straightened into a rectangle of [outputSize]. */
    fun straighten(image: PixelImage): PixelImage {
        val (width, height) = outputSize(image.width, image.height)
        val k = coefficients()
        val out = IntArray(width * height)
        for (y in 0 until height) {
            val v = (y + 0.5f) / height
            for (x in 0 until width) {
                val u = (x + 0.5f) / width
                val w = k[6] * u + k[7] * v + 1f
                val sx = ((k[0] * u + k[1] * v + k[2]) / w * image.width - 0.5f).coerceIn(0f, image.width - 1f)
                val sy = ((k[3] * u + k[4] * v + k[5]) / w * image.height - 0.5f).coerceIn(0f, image.height - 1f)
                // Blend of the four pixels around the point, so edges do not come out jagged.
                val xa = sx.toInt()
                val ya = sy.toInt()
                val xb = min(xa + 1, image.width - 1)
                val yb = min(ya + 1, image.height - 1)
                val fx = sx - xa
                val fy = sy - ya
                val a = image.argb[ya * image.width + xa]
                val b = image.argb[ya * image.width + xb]
                val c = image.argb[yb * image.width + xa]
                val d = image.argb[yb * image.width + xb]
                var pixel = 0xFF shl 24
                for (shift in intArrayOf(16, 8, 0)) {
                    val top = ((a shr shift) and 0xFF) * (1 - fx) + ((b shr shift) and 0xFF) * fx
                    val bottom = ((c shr shift) and 0xFF) * (1 - fx) + ((d shr shift) and 0xFF) * fx
                    pixel = pixel or ((top * (1 - fy) + bottom * fy).roundToInt().coerceIn(0, 255) shl shift)
                }
                out[y * width + x] = pixel
            }
        }
        return PixelImage(width, height, out)
    }

    companion object {
        val FULL = of(CropRect.FULL)

        fun of(rect: CropRect) = CropQuad(
            PointF(rect.left, rect.top), PointF(rect.right, rect.top),
            PointF(rect.right, rect.bottom), PointF(rect.left, rect.bottom),
        )
    }
}
