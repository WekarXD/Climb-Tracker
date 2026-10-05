package com.climbtracker.core.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutlinesTest {

    private val size = 200

    /** A pale wall of lightness [wall], with the dark dots of its bolt holes. */
    private fun wall(wall: Float): FloatArray {
        val l = FloatArray(size * size) { wall }
        for (y in 10 until size step 20) for (x in 10 until size step 20) {
            for (dy in -1..1) for (dx in -1..1) l[(y + dy) * size + x + dx] = wall - 40f
        }
        return l
    }

    /** A hold as pale as the wall, told apart only by its shading: lit on top, dark underneath. */
    private fun paleHold(l: FloatArray, cx: Int, cy: Int, rx: Int, ry: Int) {
        for (y in cy - ry..cy + ry) for (x in cx - rx..cx + rx) {
            val nx = (x - cx) / rx.toFloat()
            val ny = (y - cy) / ry.toFloat()
            if (nx * nx + ny * ny <= 1f) l[y * size + x] += 3f - 9f * (ny + 1f)
        }
    }

    private fun find(l: FloatArray, background: Float, taken: BooleanArray = BooleanArray(size * size)) =
        Outlines.find(l, FloatArray(size * size) { background }, taken, size, size, 1f, 5f)

    private fun contains(o: Outline, x: Float, y: Float): Boolean {
        var inside = false
        var j = o.hull.size / 2 - 1
        for (i in 0 until o.hull.size / 2) {
            val ax = o.hull[i * 2]; val ay = o.hull[i * 2 + 1]
            val bx = o.hull[j * 2]; val by = o.hull[j * 2 + 1]
            if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) inside = !inside
            j = i
        }
        return inside
    }

    @Test
    fun findsAPaleHoldByItsShading() {
        val l = wall(80f)
        paleHold(l, 105, 105, 34, 24)
        val found = find(l, 80f)
        assertEquals(1, found.size)
        assertTrue(contains(found[0], 105f, 118f))
        assertTrue(!contains(found[0], 30f, 30f))
    }

    @Test
    fun theBoltHolesOfTheWallAreNotHolds() {
        assertEquals(0, find(wall(80f), 80f).size)
    }

    @Test
    fun whatIsAlreadyDetectedIsNotFoundAgain() {
        val l = wall(80f)
        paleHold(l, 105, 105, 34, 24)
        val taken = BooleanArray(size * size)
        for (y in 70..140) for (x in 60..150) taken[y * size + x] = true
        assertEquals(0, find(l, 80f, taken).size)
    }

    @Test
    fun onADarkPanelItIsLeftToTheColourPass() {
        val l = wall(80f)
        paleHold(l, 105, 105, 34, 24)
        assertEquals(0, find(l, 30f).size)
    }

    @Test
    fun aLongThinLineIsAJointNotAHold() {
        val l = wall(80f)
        for (x in 20 until 180) for (y in 99..101) l[y * size + x] = 60f
        assertEquals(0, find(l, 80f).size)
    }
}
