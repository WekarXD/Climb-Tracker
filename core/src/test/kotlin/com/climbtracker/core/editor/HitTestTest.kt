package com.climbtracker.core.editor

import com.climbtracker.core.detection.PointF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HitTestTest {

    private fun square(id: Long, x0: Float, y0: Float, x1: Float, y1: Float) = EditorHold(
        id, listOf(PointF(x0, y0), PointF(x1, y0), PointF(x1, y1), PointF(x0, y1)), 0, 0,
    )

    // Image of 100 x 100 px, so normalised 0.01 equals one pixel.
    private val holds = listOf(square(1, 0.10f, 0.10f, 0.20f, 0.20f), square(2, 0.50f, 0.50f, 0.60f, 0.60f))

    private fun find(x: Float, y: Float, tolerance: Float = 5f) = HitTest.find(holds, x, y, tolerance, 100f, 100f)

    @Test
    fun pointInsidePolygonHits() {
        assertEquals(1L, find(0.15f, 0.15f)?.id)
        assertEquals(2L, find(0.55f, 0.55f)?.id)
    }

    @Test
    fun pointJustOutsideHitsWithinTolerance() {
        assertEquals(1L, find(0.23f, 0.15f)?.id)
    }

    @Test
    fun pointBeyondToleranceMisses() {
        assertNull(find(0.30f, 0.15f))
    }

    @Test
    fun zeroToleranceRequiresPointInside() {
        assertNull(find(0.23f, 0.15f, tolerance = 0f))
        assertEquals(1L, find(0.15f, 0.15f, tolerance = 0f)?.id)
    }

    @Test
    fun nearestHoldWinsBetweenTwoCandidates() {
        val near = listOf(square(1, 0.10f, 0.10f, 0.20f, 0.20f), square(2, 0.26f, 0.10f, 0.36f, 0.20f))
        assertEquals(2L, HitTest.find(near, 0.24f, 0.15f, 8f, 100f, 100f)?.id)
    }

    @Test
    fun toleranceUsesImagePixelsOnNonSquareImages() {
        // 200 px wide: 0.03 normalised is 6 px, beyond a 5 px tolerance.
        assertNull(HitTest.find(holds, 0.23f, 0.15f, 5f, 200f, 100f))
    }

    @Test
    fun pointOutsideImageMisses() {
        assertNull(find(-0.5f, -0.5f))
        assertNull(find(1.5f, 1.5f))
    }

    @Test
    fun emptyListMisses() {
        assertNull(HitTest.find(emptyList(), 0.5f, 0.5f, 5f, 100f, 100f))
    }

    @Test
    fun singlePointContourCanBeHit() {
        val dot = listOf(EditorHold(7, listOf(PointF(0.5f, 0.5f)), 0, 0))
        assertEquals(7L, HitTest.find(dot, 0.52f, 0.5f, 5f, 100f, 100f)?.id)
    }

    @Test
    fun innermostHoldWinsWhenNested() {
        val nested = listOf(square(1, 0.10f, 0.10f, 0.60f, 0.60f), square(2, 0.30f, 0.30f, 0.40f, 0.40f))
        assertEquals(2L, HitTest.find(nested, 0.35f, 0.35f, 5f, 100f, 100f)?.id)
        assertEquals(1L, HitTest.find(nested, 0.15f, 0.15f, 5f, 100f, 100f)?.id)
    }
}
