package com.climbtracker.core.detection

import kotlin.test.Test
import kotlin.test.assertContentEquals

class HoldMatchTest {

    private fun box(x: Float, y: Float, size: Float = 0.1f) =
        listOf(PointF(x, y), PointF(x + size, y), PointF(x + size, y + size), PointF(x, y + size))

    @Test
    fun identicalHoldsMatchThemselves() {
        val holds = listOf(box(0.1f, 0.1f), box(0.5f, 0.5f))
        assertContentEquals(intArrayOf(0, 1), HoldMatch.match(holds, holds))
    }

    @Test
    fun slightlyMovedHoldStillMatches() {
        val old = listOf(box(0.10f, 0.10f))
        val fresh = listOf(box(0.80f, 0.80f), box(0.12f, 0.11f))
        assertContentEquals(intArrayOf(1), HoldMatch.match(old, fresh))
    }

    @Test
    fun holdWithNothingNearHasNoMatch() {
        assertContentEquals(intArrayOf(-1), HoldMatch.match(listOf(box(0.1f, 0.1f)), listOf(box(0.6f, 0.6f))))
        assertContentEquals(intArrayOf(-1), HoldMatch.match(listOf(box(0.1f, 0.1f)), emptyList()))
    }

    @Test
    fun barelyOverlappingHoldDoesNotMatch() {
        assertContentEquals(intArrayOf(-1), HoldMatch.match(listOf(box(0.10f, 0.10f)), listOf(box(0.19f, 0.19f))))
    }

    @Test
    fun bestOverlapWins() {
        val old = listOf(box(0.10f, 0.10f))
        val fresh = listOf(box(0.15f, 0.10f), box(0.11f, 0.10f))
        assertContentEquals(intArrayOf(1), HoldMatch.match(old, fresh))
    }

    @Test
    fun twoFragmentsMapToTheHoldThatNowCoversBoth() {
        // The old detection split a hold in two; the new one finds it whole.
        val old = listOf(box(0.10f, 0.10f, 0.05f), box(0.15f, 0.10f, 0.05f))
        val fresh = listOf(listOf(PointF(0.10f, 0.10f), PointF(0.20f, 0.10f), PointF(0.20f, 0.15f), PointF(0.10f, 0.15f)))
        assertContentEquals(intArrayOf(0, 0), HoldMatch.match(old, fresh))
    }

    @Test
    fun emptyContoursNeverMatch() {
        assertContentEquals(intArrayOf(-1), HoldMatch.match(listOf(emptyList()), listOf(box(0.1f, 0.1f))))
    }
}
