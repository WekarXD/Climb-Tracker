package com.climbtracker.core.detection

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Drops detections that have the colour and size of a hold but are something else, told apart
 * by where they are or by what is around them.
 */
object Clutter {
    /** A part must be this many times smaller than the hold around it to be taken as part of it. */
    private const val NESTED_RATIO = 4

    /** Lightness difference under which two colourless regions are the same thing in other light. */
    private const val SAME_SHADE = 15f

    /** Fewest alike shapes in a row that are taken for lettering or a grille. */
    private const val RUN_LENGTH = 4

    /** How alike in size the shapes of a run are, as the largest ratio between neighbours. */
    private const val RUN_SIZE_RATIO = 1.7f

    /** Largest gap between neighbours of a run, in times their size. */
    private const val RUN_GAP = 0.8f

    /** How straight a run is: its width over its length. */
    private const val RUN_STRAIGHTNESS = 0.2f

    fun remove(
        holds: List<DetectedHold>,
        width: Int,
        height: Int,
        nested: Boolean = true,
        runs: Boolean = true,
    ): List<DetectedHold> {
        val labs = holds.map { ColorSpace.toLab(it.argb).let { lab -> floatArrayOf(lab.l, lab.a, lab.b) } }
        val drop = BooleanArray(holds.size)
        if (nested) markNested(holds, labs, drop)
        if (runs) markRuns(holds, labs, width, height, drop)
        return holds.filterIndexed { i, _ -> !drop[i] }
    }

    /**
     * A small region inside a much larger hold is part of that hold when it has no colour of its
     * own (a bolt hole, a glint) or the same colour (a face in other light). One of another
     * colour is a hold screwed onto a volume, and stays.
     */
    private fun markNested(holds: List<DetectedHold>, labs: List<FloatArray>, drop: BooleanArray) {
        for (i in holds.indices) {
            val small = holds[i]
            for (j in holds.indices) {
                val big = holds[j]
                if (i == j || big.area < small.area * NESTED_RATIO || !inside(small.center, big.contour)) continue
                val a = labs[i]
                val b = labs[j]
                val partOfIt = when {
                    !Shading.isNeutral(a) -> Shading.sameMaterial(a, b)
                    !Shading.isNeutral(b) -> true
                    else -> abs(a[0] - b[0]) < SAME_SHADE
                }
                if (partOfIt) {
                    drop[i] = true
                    break
                }
            }
        }
    }

    /**
     * Colourless shapes of about the same size, close together and in a straight line: the
     * letters painted on a panel, or the slats of a ventilation grille. Holds of one route are
     * seldom that regular, and when they are they have a colour.
     */
    private fun markRuns(holds: List<DetectedHold>, labs: List<FloatArray>, width: Int, height: Int, drop: BooleanArray) {
        val candidates = holds.indices.filter { Shading.isNeutral(labs[it]) }
        val x = FloatArray(holds.size) { holds[it].center.x * width }
        val y = FloatArray(holds.size) { holds[it].center.y * height }
        val size = FloatArray(holds.size) {
            val b = holds[it].bounds
            max((b.right - b.left) * width, (b.bottom - b.top) * height)
        }
        val parent = IntArray(holds.size) { it }
        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            parent[i] = r
            return r
        }
        for (a in candidates.indices) for (b in a + 1 until candidates.size) {
            val i = candidates[a]
            val j = candidates[b]
            val larger = max(size[i], size[j])
            val smaller = minOf(size[i], size[j])
            if (larger > smaller * RUN_SIZE_RATIO) continue
            // The gap is what is left between the two once their own extent is taken away.
            if (hypot(x[i] - x[j], y[i] - y[j]) - larger > larger * RUN_GAP) continue
            parent[root(i)] = root(j)
        }
        for (group in candidates.groupBy { root(it) }.values) {
            if (group.size < RUN_LENGTH) continue
            // Spread along and across the best-fitting line, from the covariance of the centres.
            val mx = group.map { x[it] }.average().toFloat()
            val my = group.map { y[it] }.average().toFloat()
            var sxx = 0f
            var syy = 0f
            var sxy = 0f
            for (i in group) {
                sxx += (x[i] - mx) * (x[i] - mx)
                syy += (y[i] - my) * (y[i] - my)
                sxy += (x[i] - mx) * (y[i] - my)
            }
            val mean = (sxx + syy) / 2
            val spread = sqrt(((sxx - syy) / 2) * ((sxx - syy) / 2) + sxy * sxy)
            val along = mean + spread
            val across = max(0f, mean - spread)
            if (along > 0f && sqrt(across / along) < RUN_STRAIGHTNESS) for (i in group) drop[i] = true
        }
    }

    /** Even-odd test of [p] against the polygon [outline]. */
    private fun inside(p: PointF, outline: List<PointF>): Boolean {
        var inside = false
        var j = outline.size - 1
        for (i in outline.indices) {
            val a = outline[i]
            val b = outline[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }
}
