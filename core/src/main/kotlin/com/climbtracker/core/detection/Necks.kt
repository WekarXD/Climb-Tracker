package com.climbtracker.core.detection

import kotlin.math.max
import kotlin.math.min

/**
 * Tells apart two holds of the same colour that touch. By colour they are one region, but its
 * shape gives them away: two bodies joined by a neck much narrower than either. A single hold,
 * however bent, is about as thick all along.
 */
object Necks {

    /**
     * The parts of [c], in an image [width] pixels wide: itself, or its bodies when it is several
     * joined by necks. A neck is at most [neck] times as wide as the bodies it joins; bodies
     * thinner than [minRadius] pixels at their thickest, or smaller than [minShare] of the whole,
     * are not holds of their own.
     */
    fun split(c: Component, width: Int, minRadius: Float, neck: Float = 0.5f, minShare: Float = 0.12f): List<Component> {
        // The region in a frame of its own, with a margin of background around it.
        val w = c.maxX - c.minX + 3
        val h = c.maxY - c.minY + 3
        val inside = BooleanArray(w * h)
        for (p in c.pixels) inside[(p / width - c.minY + 1) * w + (p % width - c.minX + 1)] = true
        val depth = depth(inside, w, h)
        val deepest = depth.max()
        if (deepest < 2 * minRadius * UNIT) return listOf(c)

        // Peeled layer by layer, a region made of two bodies comes apart when the neck is gone,
        // while both bodies still have most of their thickness left.
        var level = UNIT
        while (level < deepest * neck) {
            val core = BooleanArray(w * h) { depth[it] > level }
            val bodies = Components.find(core, w, h).filter { body ->
                val thickest = body.pixels.maxOf { depth[it] }
                thickest >= minRadius * UNIT && level <= thickest * neck
            }
            if (bodies.size >= 2) {
                val parts = grow(bodies, inside, w, h)
                if (parts.all { it.size >= minShare * c.area }) {
                    return parts.map { pixels -> component(pixels, w, c.minX - 1, c.minY - 1, width) }
                }
            }
            level += UNIT
        }
        return listOf(c)
    }

    private const val UNIT = 3
    private const val DIAGONAL = 4

    /** Distance from each pixel of [inside] to the nearest one outside it, in thirds of a pixel. */
    private fun depth(inside: BooleanArray, w: Int, h: Int): IntArray {
        val far = Int.MAX_VALUE / 2
        val d = IntArray(w * h) { if (inside[it]) far else 0 }
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (d[i] == 0) continue
            d[i] = min(min(d[i], d[i - 1] + UNIT), min(d[i - w] + UNIT, min(d[i - w - 1] + DIAGONAL, d[i - w + 1] + DIAGONAL)))
        }
        for (y in h - 2 downTo 1) for (x in w - 2 downTo 1) {
            val i = y * w + x
            if (d[i] == 0) continue
            d[i] = min(min(d[i], d[i + 1] + UNIT), min(d[i + w] + UNIT, min(d[i + w + 1] + DIAGONAL, d[i + w - 1] + DIAGONAL)))
        }
        return d
    }

    /** Each pixel of [inside] given to the body it is nearest to, going through the region. */
    private fun grow(bodies: List<Component>, inside: BooleanArray, w: Int, h: Int): List<IntArray> {
        val owner = IntArray(w * h) { -1 }
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        bodies.forEachIndexed { b, body ->
            for (p in body.pixels) {
                owner[p] = b
                queue[tail++] = p
            }
        }
        while (head < tail) {
            val p = queue[head++]
            for (d in 0..3) {
                val q = p + when (d) {
                    0 -> 1
                    1 -> -1
                    2 -> w
                    else -> -w
                }
                if (q < 0 || q >= inside.size || !inside[q] || owner[q] >= 0) continue
                owner[q] = owner[p]
                queue[tail++] = q
            }
        }
        val sizes = IntArray(bodies.size)
        for (o in owner) if (o >= 0) sizes[o]++
        val parts = List(bodies.size) { IntArray(sizes[it]) }
        val filled = IntArray(bodies.size)
        // In raster order, so that the first pixel of each part is its first in the image.
        for (p in owner.indices) {
            val o = owner[p]
            if (o >= 0) parts[o][filled[o]++] = p
        }
        return parts
    }

    /** [pixels] of a frame [w] wide placed at ([left], [top]), as a region of the whole image. */
    private fun component(pixels: IntArray, w: Int, left: Int, top: Int, width: Int): Component {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        val out = IntArray(pixels.size) { i ->
            val x = pixels[i] % w + left
            val y = pixels[i] / w + top
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
            y * width + x
        }
        return Component(out, minX, minY, maxX, maxY)
    }
}
