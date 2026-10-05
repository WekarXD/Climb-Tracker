package com.climbtracker.core.image

import com.climbtracker.core.detection.PointF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageUtilsTest {

    @Test
    fun sampleSizeIsOneForSmallImages() {
        assertEquals(1, ImageMath.sampleSize(1000, 800, 2048))
        assertEquals(1, ImageMath.sampleSize(2048, 1536, 2048))
    }

    @Test
    fun sampleSizeHalvesWhileResultStaysAboveLimit() {
        assertEquals(2, ImageMath.sampleSize(4096, 3072, 2048))
        assertEquals(2, ImageMath.sampleSize(8160, 6120, 2048))   // 48 MP: 4080 px stays above the limit
        assertEquals(4, ImageMath.sampleSize(12240, 16320, 2048)) // 200 MP portrait
    }

    @Test
    fun sampleSizeSurvivesDegenerateInput() {
        assertEquals(1, ImageMath.sampleSize(0, 0, 2048))
        assertEquals(1, ImageMath.sampleSize(100, 100, 0))
    }

    @Test
    fun fitSizeScalesLongSide() {
        assertEquals(640 to 480, ImageMath.fitSize(4000, 3000, 640))
        assertEquals(480 to 640, ImageMath.fitSize(3000, 4000, 640))
        assertEquals(300 to 200, ImageMath.fitSize(300, 200, 640))
        assertEquals(640 to 1, ImageMath.fitSize(10000, 2, 640))
    }

    @Test
    fun cropFullCoversEverything() {
        assertEquals(CropRect(0f, 0f, 1f, 1f), CropRect.FULL)
        assertEquals(CropRect.FULL, CropRect.FULL.normalized())
    }

    @Test
    fun cropNormalizedSortsAndClamps() {
        assertEquals(CropRect(0.2f, 0.1f, 0.8f, 1f), CropRect(0.8f, 1.4f, 0.2f, 0.1f).normalized())
    }

    @Test
    fun cropNormalizedEnforcesMinimumSize() {
        val c = CropRect(0.5f, 0.5f, 0.5f, 0.5f).normalized()
        assertEquals(0.1f, c.right - c.left, 0.0001f)
        assertEquals(0.1f, c.bottom - c.top, 0.0001f)
    }

    @Test
    fun cropNormalizedKeepsMinimumSizeAtTheEdge() {
        val c = CropRect(0.98f, 0.98f, 1f, 1f).normalized()
        assertEquals(1f, c.right, 0.0001f)
        assertEquals(0.9f, c.left, 0.0001f)
        assertTrue(c.top >= 0f && c.bottom <= 1f)
    }

    @Test
    fun contourRoundTrip() {
        val contour = listOf(PointF(0.125f, 0.5f), PointF(1f, 0f), PointF(0.3333f, 0.6667f))
        assertEquals(contour, ContourCodec.decode(ContourCodec.encode(contour)))
    }

    @Test
    fun contourEncodingFormat() {
        assertEquals("0.25,0.5;1.0,0.0", ContourCodec.encode(listOf(PointF(0.25f, 0.5f), PointF(1f, 0f))))
    }

    @Test
    fun contourDecodeToleratesBadInput() {
        assertEquals(emptyList(), ContourCodec.decode(""))
        assertEquals(listOf(PointF(0.5f, 0.5f)), ContourCodec.decode("0.5,0.5;garbage;1.0"))
    }

    @Test
    fun draggingCornerOfMinimumRectAtEdgeDoesNotThrow() {
        // 0.099999994 is what a minimum-width rectangle becomes after float rounding.
        val c = CropRect(0f, 0f, 0.099999994f, 0.5f).dragged(CropHandle.TOP_LEFT, -0.2f, 0f)
        assertTrue(c.left >= 0f && c.right <= 1f)
        assertTrue(c.right - c.left >= 0.0999f)
    }

    @Test
    fun draggingCornerMovesOnlyThatCorner() {
        assertEquals(CropRect(0.2f, 0.3f, 1f, 1f), CropRect.FULL.dragged(CropHandle.TOP_LEFT, 0.2f, 0.3f))
        assertEquals(CropRect(0f, 0f, 0.8f, 0.7f), CropRect.FULL.dragged(CropHandle.BOTTOM_RIGHT, -0.2f, -0.3f))
    }

    @Test
    fun draggingCornerStopsAtMinimumSize() {
        val c = CropRect.FULL.dragged(CropHandle.BOTTOM_RIGHT, -2f, -2f)
        assertEquals(0.1f, c.right, 0.0001f)
        assertEquals(0.1f, c.bottom, 0.0001f)
        val d = CropRect.FULL.dragged(CropHandle.TOP_RIGHT, -2f, 2f)
        assertEquals(0.1f, d.right, 0.0001f)
        assertEquals(0.9f, d.top, 0.0001f)
    }

    @Test
    fun movingKeepsSizeAndStaysInside() {
        val c = CropRect(0.2f, 0.2f, 0.6f, 0.5f).dragged(CropHandle.MOVE, 1f, -1f)
        assertEquals(0.6f, c.left, 0.0001f)
        assertEquals(1f, c.right, 0.0001f)
        assertEquals(0f, c.top, 0.0001f)
        assertEquals(0.3f, c.bottom, 0.0001f)
    }
}
