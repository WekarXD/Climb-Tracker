package com.climbtracker.core.editor

import com.climbtracker.core.detection.PointF
import kotlin.math.abs
import kotlin.math.hypot

object HitTest {

    /**
     * Hold under the point ([x], [y]) given in normalised coordinates. A hold whose polygon
     * contains the point wins, the smallest one when several do (a hold bolted onto a volume
     * lies inside the volume's contour); otherwise the nearest hold within [tolerance] image pixels.
     * [width] and [height] are the image size in pixels, used to measure distances.
     */
    fun find(holds: List<EditorHold>, x: Float, y: Float, tolerance: Float, width: Float, height: Float): EditorHold? {
        var inner: EditorHold? = null
        var innerArea = Float.MAX_VALUE
        for (hold in holds) {
            if (!contains(hold.contour, x, y)) continue
            val a = area(hold.contour)
            if (a < innerArea) {
                innerArea = a
                inner = hold
            }
        }
        if (inner != null) return inner
        var best: EditorHold? = null
        var bestDistance = tolerance
        for (hold in holds) {
            val d = distance(hold.contour, x, y, width, height)
            if (d <= bestDistance && d < Float.MAX_VALUE && tolerance > 0f) {
                bestDistance = d
                best = hold
            }
        }
        return best
    }

    /** Shoelace formula, in normalised units. */
    private fun area(polygon: List<PointF>): Float {
        var sum = 0f
        var j = polygon.size - 1
        for (i in polygon.indices) {
            sum += polygon[j].x * polygon[i].y - polygon[i].x * polygon[j].y
            j = i
        }
        return abs(sum) / 2f
    }

    /** Even-odd ray casting. */
    private fun contains(polygon: List<PointF>, x: Float, y: Float): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    private fun distance(polygon: List<PointF>, x: Float, y: Float, width: Float, height: Float): Float {
        if (polygon.isEmpty()) return Float.MAX_VALUE
        val px = x * width
        val py = y * height
        var best = Float.MAX_VALUE
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val ax = polygon[j].x * width
            val ay = polygon[j].y * height
            val bx = polygon[i].x * width
            val by = polygon[i].y * height
            val dx = bx - ax
            val dy = by - ay
            val lengthSq = dx * dx + dy * dy
            val t = if (lengthSq == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0f, 1f)
            val d = hypot(px - (ax + t * dx), py - (ay + t * dy))
            if (d < best) best = d
            j = i
        }
        return best
    }
}
