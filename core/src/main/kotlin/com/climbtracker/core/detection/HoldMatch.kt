package com.climbtracker.core.detection

import kotlin.math.max
import kotlin.math.min

/** Pairs the holds of an earlier detection with those of a new one on the same photo. */
object HoldMatch {
    /** Overlap of the two bounding boxes, as intersection over union, needed to be the same hold. */
    private const val MIN_OVERLAP = 0.3f

    /** A fragment counts as part of a larger hold when this share of its box lies inside it. */
    private const val MIN_COVERED = 0.7f

    /**
     * For each contour in [old], the index in [fresh] of the hold at the same place, or -1 when
     * none is. Several old holds may map to one new hold, when the new detection joins pieces
     * the old one found apart.
     */
    fun match(old: List<List<PointF>>, fresh: List<List<PointF>>): IntArray {
        val freshBoxes = fresh.map { box(it) }
        return IntArray(old.size) { i ->
            val a = box(old[i]) ?: return@IntArray -1
            var best = -1
            var bestScore = 0f
            for (j in freshBoxes.indices) {
                val b = freshBoxes[j] ?: continue
                val w = min(a[2], b[2]) - max(a[0], b[0])
                val h = min(a[3], b[3]) - max(a[1], b[1])
                if (w <= 0f || h <= 0f) continue
                val inter = w * h
                val areaA = (a[2] - a[0]) * (a[3] - a[1])
                val areaB = (b[2] - b[0]) * (b[3] - b[1])
                val overlap = inter / (areaA + areaB - inter)
                val covered = if (areaA > 0f) inter / areaA else 0f
                if (overlap < MIN_OVERLAP && covered < MIN_COVERED) continue
                val score = max(overlap, covered * 0.5f)
                if (score > bestScore) {
                    bestScore = score
                    best = j
                }
            }
            best
        }
    }

    /** left, top, right, bottom; null for an empty contour. */
    private fun box(contour: List<PointF>): FloatArray? {
        if (contour.isEmpty()) return null
        return floatArrayOf(contour.minOf { it.x }, contour.minOf { it.y }, contour.maxOf { it.x }, contour.maxOf { it.y })
    }
}
