package com.climbtracker.core.detection

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A hold found by its outline. [hull] is its convex outline as x, y pairs in pixels, [area] the
 * area inside it, and [pixels] the edge pixels it was built from.
 */
class Outline(val hull: FloatArray, val area: Float, val pixels: IntArray)

/** Sizes are for an image whose long side is 640 px, as in [DetectorParams]. */
data class OutlineParams(
    val sigma: Float = 1f,
    /** Longest side of an edge too small to be part of a hold: the wall's bolt holes. */
    val dot: Float = 7f,
    /** How far apart two edges may be and still belong to the same hold. */
    val reach: Float = 1.5f,
    val minSide: Float = 17f,
    val maxSide: Float = 210f,
    /** Background lightness from which a wall counts as light. */
    val lightWall: Float = 60f,
    /** Least share of its outline's area that the edges of a hold cover. */
    val minFill: Float = 0.25f,
    /** Edges all over, rather than around: a grille or a texture, not a hold. */
    val maxFill: Float = 0.8f,
    val maxCompactness: Float = 2.2f,
)

/**
 * Finds holds of the colour of the wall, which the colour pass cannot see. What gives them away
 * is their relief: the shaded underside and the shadow they cast draw most of their outline.
 * The edges of the image are gathered into groups, and a group of the size and shape of a hold
 * is one. Only on light walls: on dark panels a pale hold already stands out by its lightness.
 */
object Outlines {

    /**
     * [l] is the lightness of the image and [backgroundL] that of its local background. Edges
     * under [taken] are ignored: they belong to holds already found. [threshold] is the change
     * in lightness across two pixels from which there is an edge.
     */
    fun find(
        l: FloatArray,
        backgroundL: FloatArray,
        taken: BooleanArray,
        width: Int,
        height: Int,
        scale: Float,
        threshold: Float,
        params: OutlineParams = OutlineParams(),
    ): List<Outline> {
        val smooth = blur(l, width, height, params.sigma * scale)
        var edges = BooleanArray(width * height)
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val i = y * width + x
            if (taken[i]) continue
            edges[i] = hypot(smooth[i + 1] - smooth[i - 1], smooth[i + width] - smooth[i - width]) > threshold
        }
        // The wall is dotted with bolt holes, each a tiny ring of edge. Without them what is
        // left is mostly the outlines of holds.
        val dot = params.dot * scale
        for (c in Components.find(edges, width, height)) {
            if (max(c.maxX - c.minX, c.maxY - c.minY) + 1 < dot) for (p in c.pixels) edges[p] = false
        }
        repeat(max(1, (params.reach * scale).roundToInt())) { edges = Mask.dilate(edges, width, height) }

        val out = ArrayList<Outline>()
        for (c in Components.find(edges, width, height)) {
            val side = (max(c.maxX - c.minX, c.maxY - c.minY) + 1).toFloat()
            if (side < params.minSide * scale || side > params.maxSide * scale) continue
            // Cut by the edge of the photo: there is no telling what it is.
            if (c.minX <= 1 || c.minY <= 1 || c.maxX >= width - 2 || c.maxY >= height - 2) continue
            val hull = hull(c.pixels, width)
            var twiceArea = 0f
            var perimeter = 0f
            var cx = 0f
            var cy = 0f
            val n = hull.size / 2
            for (i in 0 until n) {
                val j = (i + 1) % n
                twiceArea += hull[i * 2] * hull[j * 2 + 1] - hull[j * 2] * hull[i * 2 + 1]
                perimeter += hypot(hull[j * 2] - hull[i * 2], hull[j * 2 + 1] - hull[i * 2 + 1])
                cx += hull[i * 2]
                cy += hull[i * 2 + 1]
            }
            val area = abs(twiceArea) / 2
            if (area <= 0f) continue
            // A joint between panels or the shadow under a hold is a line; a hold is rounder.
            if (perimeter * perimeter / (4f * PI.toFloat() * area) > params.maxCompactness) continue
            if (c.area / area < params.minFill || c.area / area > params.maxFill) continue
            val centre = (cy / n).roundToInt().coerceIn(0, height - 1) * width + (cx / n).roundToInt().coerceIn(0, width - 1)
            if (backgroundL[centre] < params.lightWall) continue
            out += Outline(hull, area, c.pixels)
        }
        return out
    }

    /** True when ([x], [y]), in pixels, is inside the outline. */
    fun contains(o: Outline, x: Float, y: Float): Boolean {
        val n = o.hull.size / 2
        var sign = 0
        for (i in 0 until n) {
            val j = (i + 1) % n
            val cross = (o.hull[j * 2] - o.hull[i * 2]) * (y - o.hull[i * 2 + 1]) - (o.hull[j * 2 + 1] - o.hull[i * 2 + 1]) * (x - o.hull[i * 2])
            if (cross == 0f) continue
            val s = if (cross > 0f) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    /** Share of the pixels inside the outline that are set in [mask]. */
    fun covered(o: Outline, mask: BooleanArray, width: Int, height: Int): Float {
        var minX = Float.MAX_VALUE
        var maxX = 0f
        var minY = Float.MAX_VALUE
        var maxY = 0f
        for (i in 0 until o.hull.size / 2) {
            minX = minOf(minX, o.hull[i * 2])
            maxX = maxOf(maxX, o.hull[i * 2])
            minY = minOf(minY, o.hull[i * 2 + 1])
            maxY = maxOf(maxY, o.hull[i * 2 + 1])
        }
        var inside = 0
        var set = 0
        for (y in minY.toInt().coerceAtLeast(0)..maxY.toInt().coerceAtMost(height - 1)) {
            for (x in minX.toInt().coerceAtLeast(0)..maxX.toInt().coerceAtMost(width - 1)) {
                if (!contains(o, x.toFloat(), y.toFloat())) continue
                inside++
                if (mask[y * width + x]) set++
            }
        }
        return if (inside == 0) 0f else set.toFloat() / inside
    }

    /** Gaussian blur, edges repeated. */
    private fun blur(src: FloatArray, width: Int, height: Int, sigma: Float): FloatArray {
        val radius = max(1, ceil(sigma * 3).toInt())
        val kernel = FloatArray(radius * 2 + 1) { exp(-((it - radius) * (it - radius)) / (2f * sigma * sigma)) }
        val total = kernel.sum()
        for (i in kernel.indices) kernel[i] /= total
        val rows = FloatArray(src.size)
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0f
            for (k in -radius..radius) sum += kernel[k + radius] * src[y * width + (x + k).coerceIn(0, width - 1)]
            rows[y * width + x] = sum
        }
        val out = FloatArray(src.size)
        for (y in 0 until height) for (x in 0 until width) {
            var sum = 0f
            for (k in -radius..radius) sum += kernel[k + radius] * rows[(y + k).coerceIn(0, height - 1) * width + x]
            out[y * width + x] = sum
        }
        return out
    }

    /** Convex hull of [pixels] (indices y * width + x), as x, y pairs. */
    private fun hull(pixels: IntArray, width: Int): FloatArray {
        // Sorted by x, then y.
        val keys = LongArray(pixels.size) { ((pixels[it] % width).toLong() shl 32) or (pixels[it] / width).toLong() }
        keys.sort()
        val xs = IntArray(keys.size) { (keys[it] shr 32).toInt() }
        val ys = IntArray(keys.size) { (keys[it] and 0xFFFFFFFFL).toInt() }
        val stack = IntArray(keys.size * 2 + 2)
        var top = 0
        fun turnsLeft(a: Int, b: Int, c: Int): Boolean =
            (xs[b] - xs[a]).toLong() * (ys[c] - ys[a]) - (ys[b] - ys[a]).toLong() * (xs[c] - xs[a]) > 0
        for (i in keys.indices) {
            while (top >= 2 && !turnsLeft(stack[top - 2], stack[top - 1], i)) top--
            stack[top++] = i
        }
        val lower = top + 1
        for (i in keys.size - 2 downTo 0) {
            while (top >= lower && !turnsLeft(stack[top - 2], stack[top - 1], i)) top--
            stack[top++] = i
        }
        val count = max(1, top - 1)
        return FloatArray(count * 2) { if (it % 2 == 0) xs[stack[it / 2]].toFloat() else ys[stack[it / 2]].toFloat() }
    }
}
