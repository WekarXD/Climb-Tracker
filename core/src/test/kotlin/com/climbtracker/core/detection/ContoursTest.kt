package com.climbtracker.core.detection

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContoursTest {

    private fun block(width: Int, height: Int, x0: Int, y0: Int, x1: Int, y1: Int): BooleanArray {
        val m = BooleanArray(width * height)
        for (y in y0..y1) for (x in x0..x1) m[y * width + x] = true
        return m
    }

    @Test
    fun tracesSquareClockwise() {
        val m = block(5, 5, 1, 1, 3, 3)
        val c = Contours.trace(m, 5, 5, 1 * 5 + 1)
        assertContentEquals(intArrayOf(1, 1, 2, 1, 3, 1, 3, 2, 3, 3, 2, 3, 1, 3, 1, 2), c)
    }

    @Test
    fun tracesSinglePixel() {
        val m = BooleanArray(9)
        m[4] = true
        assertContentEquals(intArrayOf(1, 1), Contours.trace(m, 3, 3, 4))
    }

    @Test
    fun tracesBlockTouchingEveryBorder() {
        val m = BooleanArray(12) { true }
        val c = Contours.trace(m, 4, 3, 0)
        assertEquals(10 * 2, c.size)
        for (i in c.indices step 2) {
            assertTrue(c[i] in 0..3)
            assertTrue(c[i + 1] in 0..2)
        }
    }

    @Test
    fun tracesOnePixelWideLine() {
        val m = block(5, 3, 0, 1, 4, 1)
        val c = Contours.trace(m, 5, 3, 1 * 5)
        assertEquals(0, c[0])
        assertEquals(1, c[1])
        assertTrue(c.size >= 5 * 2)
    }

    /** Outline of a square of the given side, pixel by pixel, clockwise from the top-left corner. */
    private fun square(side: Int): IntArray {
        val out = ArrayList<Int>()
        for (x in 0 until side) { out += x; out += 0 }
        for (y in 0 until side) { out += side; out += y }
        for (x in side downTo 1) { out += x; out += side }
        for (y in side downTo 1) { out += 0; out += y }
        return out.toIntArray()
    }

    @Test
    fun smoothRoundsCornersAndStaysInsideTheShape() {
        val s = Contours.smooth(square(40), radius = 3, maxPoints = 200)
        for (i in s.indices step 2) {
            assertTrue(s[i] in 0f..40f && s[i + 1] in 0f..40f)
        }
        // No point sits on a corner any more, but the middle of each side is untouched.
        assertTrue((0 until s.size / 2).none { s[it * 2] < 0.5f && s[it * 2 + 1] < 0.5f })
        assertTrue((0 until s.size / 2).any { s[it * 2 + 1] == 0f && s[it * 2] in 15f..25f })
    }

    @Test
    fun smoothRemovesThePixelStaircase() {
        // A diagonal edge traced on pixels: steps of one pixel right, one pixel down.
        val stairs = ArrayList<Int>()
        for (i in 0 until 30) { stairs += i; stairs += i; stairs += i + 1; stairs += i }
        for (i in 30 downTo 1) { stairs += i - 10; stairs += i + 10 }
        val s = Contours.smooth(stairs.toIntArray(), radius = 3, maxPoints = 500)
        // Along the staircase part, consecutive points advance steadily in both axes.
        var backwards = 0
        for (k in 6 until 50) if (s[k * 2] < s[(k - 1) * 2] - 0.01f || s[k * 2 + 1] < s[(k - 1) * 2 + 1] - 0.01f) backwards++
        assertEquals(0, backwards)
    }

    @Test
    fun smoothLimitsPointCount() {
        val s = Contours.smooth(square(100), radius = 2, maxPoints = 96)
        assertEquals(96 * 2, s.size)
    }

    @Test
    fun smoothLeavesTinyContoursAlone() {
        val tiny = intArrayOf(3, 3, 4, 3, 4, 4)
        assertContentEquals(floatArrayOf(3f, 3f, 4f, 3f, 4f, 4f), Contours.smooth(tiny, radius = 2))
    }
}
