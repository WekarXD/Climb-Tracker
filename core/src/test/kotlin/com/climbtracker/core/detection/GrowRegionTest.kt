package com.climbtracker.core.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrowRegionTest {

    private val detector = ColorHoldDetector()

    private fun wall(): PixelImage {
        val img = TestImages.solid(240, 180, TestImages.BEIGE)
        TestImages.rect(img, 100, 80, 20, 20, TestImages.RED)
        return img
    }

    @Test
    fun growsHoldFromPointInsideIt() {
        val hold = assertNotNull(detector.growRegion(wall(), 110, 90))
        assertEquals(110f / 240, hold.center.x, 0.02f)
        assertEquals(90f / 180, hold.center.y, 0.02f)
        assertTrue(hold.area in 350..400)
        assertEquals(-1, hold.colorGroup)
        assertTrue(hold.contour.size >= 3)
    }

    @Test
    fun returnsNullOnBackground() {
        assertNull(detector.growRegion(wall(), 20, 20))
    }

    @Test
    fun returnsNullOutsideImage() {
        val img = wall()
        assertNull(detector.growRegion(img, -1, 10))
        assertNull(detector.growRegion(img, 10, -1))
        assertNull(detector.growRegion(img, 240, 10))
        assertNull(detector.growRegion(img, 10, 180))
    }

    @Test
    fun returnsNullOnTinyRegion() {
        val img = TestImages.solid(240, 180, TestImages.BEIGE)
        img.argb[90 * 240 + 110] = TestImages.RED
        assertNull(detector.growRegion(img, 110, 90))
    }

    @Test
    fun growsHoldInImageCorner() {
        val img = TestImages.solid(240, 180, TestImages.BEIGE)
        TestImages.rect(img, 0, 0, 20, 20, TestImages.BLUE)
        val hold = assertNotNull(detector.growRegion(img, 0, 0))
        assertTrue(hold.contour.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test
    fun smoothingCacheDoesNotLeakBetweenImages() {
        assertNotNull(detector.growRegion(wall(), 110, 90))
        val other = TestImages.solid(240, 180, TestImages.BEIGE)
        TestImages.rect(other, 30, 30, 20, 20, TestImages.BLUE)
        assertNull(detector.growRegion(other, 110, 90))
        assertNotNull(detector.growRegion(other, 40, 40))
    }
}
