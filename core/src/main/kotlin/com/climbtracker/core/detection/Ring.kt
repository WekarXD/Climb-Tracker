package com.climbtracker.core.detection

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Ring {
    /** Outer radius of the ring, in pixels of a 640 px image. */
    private const val OUTER = 4

    /**
     * True when more than [fraction] of the pixels in a ring around [c] have the component's own
     * colour (Lab distance below [maxDistance]). Such a component is a piece of wall left over at
     * the boundary between two background panels, not a hold.
     */
    fun continues(c: Component, lab: LabImage, maxDistance: Float, fraction: Float, scale: Float = 1f): Boolean {
        val w = lab.width
        val h = lab.height
        // The ring starts beyond the blurred edge of the component and both radii grow with the
        // image, so it covers the same part of the wall at any resolution.
        val outer = max(OUTER, (OUTER * scale).roundToInt())
        val inner = max(1, scale.roundToInt())
        val outerSq = outer * outer + outer
        val innerSq = inner * inner
        val x0 = max(0, c.minX - outer)
        val y0 = max(0, c.minY - outer)
        val x1 = min(w - 1, c.maxX + outer)
        val y1 = min(h - 1, c.maxY + outer)
        val bw = x1 - x0 + 1
        val bh = y1 - y0 + 1

        val local = BooleanArray(bw * bh)
        var sl = 0f
        var sa = 0f
        var sb = 0f
        for (p in c.pixels) {
            local[(p / w - y0) * bw + (p % w - x0)] = true
            sl += lab.l[p]
            sa += lab.a[p]
            sb += lab.b[p]
        }
        val mean = Lab(sl / c.area, sa / c.area, sb / c.area)

        var ring = 0
        var same = 0
        for (y in 0 until bh) for (x in 0 until bw) {
            if (local[y * bw + x]) continue
            var best = Int.MAX_VALUE
            for (dy in -outer..outer) {
                val yy = y + dy
                if (yy < 0 || yy >= bh) continue
                for (dx in -outer..outer) {
                    val xx = x + dx
                    if (xx < 0 || xx >= bw) continue
                    if (local[yy * bw + xx]) {
                        val d = dx * dx + dy * dy
                        if (d < best) best = d
                    }
                }
            }
            // Skip pixels touching the component (blurred edge) and those too far away.
            if (best <= innerSq || best > outerSq) continue
            ring++
            if (mean.distance(lab.at((y + y0) * w + (x + x0))) < maxDistance) same++
        }
        return ring > 0 && same.toFloat() / ring > fraction
    }
}
