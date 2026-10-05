package com.climbtracker.core.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaskTest {

    private fun uniform(n: Int, l: Float, a: Float, b: Float) =
        LabImage(n, 1, FloatArray(n) { l }, FloatArray(n) { a }, FloatArray(n) { b })

    @Test
    fun foregroundUsesChromaAndLightThresholds() {
        val bg = uniform(4, 50f, 0f, 0f)
        val img = uniform(4, 50f, 0f, 0f)
        img.a[0] = 20f          // chroma 20 > 18
        img.l[1] = 80f          // light 30 < 38
        img.l[2] = 90f          // light 40 > 38
        val m = Mask.foreground(img, bg, 18f, 38f, 38f)
        assertTrue(m[0])
        assertFalse(m[1])
        assertTrue(m[2])
        assertFalse(m[3])
    }

    @Test
    fun openRemovesIsolatedPixel() {
        val m = BooleanArray(25)
        m[12] = true
        assertEquals(0, Mask.open(m, 5, 5).count { it })
    }

    @Test
    fun openKeepsLargeBlock() {
        val m = BooleanArray(100)
        for (y in 2..7) for (x in 2..7) m[y * 10 + x] = true
        val o = Mask.open(m, 10, 10)
        assertTrue(o[4 * 10 + 4])
        assertTrue(o.count { it } >= 32)
        assertFalse(o[0])
    }

    @Test
    fun closeFillsSinglePixelHole() {
        val m = BooleanArray(49)
        for (y in 1..5) for (x in 1..5) m[y * 7 + x] = true
        m[3 * 7 + 3] = false
        assertTrue(Mask.close(m, 7, 7)[3 * 7 + 3])
    }

    @Test
    fun erodeDoesNotEatBlockAtImageBorder() {
        val m = BooleanArray(16) { true }
        assertEquals(16, Mask.erode(m, 4, 4).count { it })
    }

    @Test
    fun darkerPixelsUseTheirOwnThreshold() {
        val bg = uniform(4, 80f, 0f, 0f)
        val img = uniform(4, 80f, 0f, 0f)
        img.l[0] = 50f          // 30 darker: above the darker threshold of 27
        img.l[1] = 60f          // 20 darker: below it
        val m = Mask.foreground(img, bg, 18f, 38f, 27f)
        assertTrue(m[0])
        assertFalse(m[1])
    }

    @Test
    fun lighterPixelsKeepTheStricterThreshold() {
        // Chalk on a dark panel is lighter than its background and must not count as a hold.
        val bg = uniform(2, 30f, 0f, 0f)
        val img = uniform(2, 30f, 0f, 0f)
        img.l[0] = 60f          // 30 lighter: below the lighter threshold of 38
        img.l[1] = 75f          // 45 lighter: above it
        val m = Mask.foreground(img, bg, 18f, 38f, 27f)
        assertFalse(m[0])
        assertTrue(m[1])
    }
}
