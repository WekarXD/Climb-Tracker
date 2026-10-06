package com.climbtracker.core.editor

import com.climbtracker.core.detection.PointF
import kotlin.math.abs

/**
 * Splits a hold in two along a line drawn across it, for the times the detector took two holds
 * that touch for one.
 */
object HoldCut {

    /** Smallest part worth keeping, as a share of the hold: less is a slip of the finger. */
    private const val MIN_SHARE = 0.05f

    /**
     * The two parts of [contour] on either side of the line from [a] to [b], the upper one first.
     * Null unless the line crosses the outline exactly twice and leaves two parts of some size:
     * a line that stops inside the hold, or runs along its edge, cuts nothing.
     */
    fun split(contour: List<PointF>, a: PointF, b: PointF): Pair<List<PointF>, List<PointF>>? {
        val n = contour.size
        if (n < 3) return null
        // Where the line crosses each side of the outline: the side it is on, and the point.
        val crossings = ArrayList<Pair<Int, PointF>>()
        for (i in 0 until n) {
            val p = contour[i]
            val q = contour[(i + 1) % n]
            val along = (b.x - a.x) * (q.y - p.y) - (b.y - a.y) * (q.x - p.x)
            if (along == 0f) continue
            val t = ((p.x - a.x) * (q.y - p.y) - (p.y - a.y) * (q.x - p.x)) / along
            val u = ((p.x - a.x) * (b.y - a.y) - (p.y - a.y) * (b.x - a.x)) / along
            // The end of a side belongs to the next one, so a corner is not counted twice.
            if (t in 0f..1f && u >= 0f && u < 1f) crossings += i to PointF(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
        }
        if (crossings.size != 2) return null
        val (first, enter) = crossings[0]
        val (second, leave) = crossings[1]

        val one = ArrayList<PointF>()
        one += enter
        for (i in first + 1..second) one += contour[i]
        one += leave
        val other = ArrayList<PointF>()
        other += leave
        for (i in second + 1 until n) other += contour[i]
        for (i in 0..first) other += contour[i]
        other += enter

        val whole = area(contour)
        if (one.size < 3 || other.size < 3 || area(one) < MIN_SHARE * whole || area(other) < MIN_SHARE * whole) return null
        return if (height(one) <= height(other)) one to other else other to one
    }

    /**
     * What a part of a split hold keeps of the place the hold had in a circuit. The top is the
     * [upper] part and the start the lower one; the other part stays as a plain hold.
     */
    fun inherit(selected: SelectedHold, upper: Boolean): SelectedHold = when (selected.role) {
        HoldRole.TOP -> if (upper) selected else selected.copy(role = HoldRole.NORMAL)
        HoldRole.START -> if (upper) selected.copy(role = HoldRole.NORMAL) else selected
        else -> selected
    }

    /** Of the two parts, the one an attempt that ended on the hold is counted on. */
    fun reachedUpper(role: HoldRole?): Boolean = role == HoldRole.TOP

    private fun area(points: List<PointF>): Float {
        var sum = 0f
        for (i in points.indices) {
            val p = points[i]
            val q = points[(i + 1) % points.size]
            sum += p.x * q.y - q.x * p.y
        }
        return abs(sum) / 2f
    }

    /** Mean height of the corners; smaller is higher up the photo. */
    private fun height(points: List<PointF>): Float = points.sumOf { it.y.toDouble() }.toFloat() / points.size
}
