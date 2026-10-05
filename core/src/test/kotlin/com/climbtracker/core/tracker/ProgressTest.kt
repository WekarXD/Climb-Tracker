package com.climbtracker.core.tracker

import com.climbtracker.core.detection.PointF
import com.climbtracker.core.editor.EditorHold
import com.climbtracker.core.editor.HoldRole
import com.climbtracker.core.editor.SelectedHold
import com.climbtracker.core.tracker.AttemptResult.FAIL
import com.climbtracker.core.tracker.AttemptResult.SEND
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressTest {

    private val yellow = 0xFFE8C020.toInt()
    private val blue = 0xFF2060D0.toInt()

    /** A small square hold centred at height [y] (0 = top of the photo). */
    private fun hold(id: Long, y: Float, group: Int = 0, argb: Int = yellow) = EditorHold(
        id,
        listOf(PointF(0.4f, y - 0.02f), PointF(0.6f, y - 0.02f), PointF(0.6f, y + 0.02f), PointF(0.4f, y + 0.02f)),
        group,
        argb,
    )

    private val holds = listOf(hold(1, 0.9f), hold(2, 0.7f), hold(3, 0.5f), hold(4, 0.1f))

    private fun sel(vararg roles: Pair<Long, HoldRole>) =
        roles.withIndex().associate { (i, p) -> p.first to SelectedHold(p.second, i) }

    private val marked = sel(1L to HoldRole.START, 2L to HoldRole.NORMAL, 3L to HoldRole.NORMAL, 4L to HoldRole.TOP)
    private val unmarked = sel(1L to HoldRole.NORMAL, 2L to HoldRole.NORMAL, 3L to HoldRole.NORMAL, 4L to HoldRole.NORMAL)

    @Test
    fun fractionIsHeightBetweenStartAndTop() {
        assertEquals(0.5f, Progress.fraction(holds, marked, 3), 0.001f)
        assertEquals(0.25f, Progress.fraction(holds, marked, 2), 0.001f)
    }

    @Test
    fun startIsZeroAndTopIsOne() {
        assertEquals(0f, Progress.fraction(holds, marked, 1), 0.001f)
        assertEquals(1f, Progress.fraction(holds, marked, 4), 0.001f)
    }

    @Test
    fun noReachedHoldIsZero() {
        assertEquals(0f, Progress.fraction(holds, marked, null), 0.001f)
        assertEquals(0f, Progress.fraction(holds, marked, 99), 0.001f)
    }

    @Test
    fun holdOutsideCircuitIsZero() {
        assertEquals(0f, Progress.fraction(holds, sel(1L to HoldRole.START, 4L to HoldRole.TOP), 3), 0.001f)
    }

    @Test
    fun withoutRolesLowestAndHighestHoldsAreUsed() {
        assertEquals(0.5f, Progress.fraction(holds, unmarked, 3), 0.001f)
        assertEquals(1f, Progress.fraction(holds, unmarked, 4), 0.001f)
        assertTrue(Progress.isTop(holds, unmarked, 4))
        assertFalse(Progress.isTop(holds, unmarked, 3))
    }

    @Test
    fun twoStartHoldsUseTheirMeanHeight() {
        val two = sel(1L to HoldRole.START, 2L to HoldRole.START, 3L to HoldRole.NORMAL, 4L to HoldRole.TOP)
        assertEquals(0.3f / 0.7f, Progress.fraction(holds, two, 3), 0.001f)
        assertEquals(0f, Progress.fraction(holds, two, 1), 0.001f)
    }

    @Test
    fun holdAboveTheTopNeverReads100WithoutSending() {
        val lowTop = sel(1L to HoldRole.START, 3L to HoldRole.TOP, 4L to HoldRole.NORMAL)
        assertEquals(0.99f, Progress.fraction(holds, lowTop, 4), 0.001f)
        assertTrue(Progress.isTop(holds, lowTop, 3))
        assertFalse(Progress.isTop(holds, lowTop, 4))
    }

    @Test
    fun singleHoldCircuitDoesNotDivideByZero() {
        val one = listOf(hold(1, 0.5f))
        assertEquals(1f, Progress.fraction(one, sel(1L to HoldRole.NORMAL), 1), 0.001f)
        assertEquals(0f, Progress.fraction(one, sel(1L to HoldRole.START), 1), 0.001f)
    }

    @Test
    fun bestTakesHighestAttemptAndSendIsFull() {
        assertEquals(0f, Progress.best(holds, marked, emptyList()), 0.001f)
        assertEquals(0.5f, Progress.best(holds, marked, listOf(FAIL to 2L, FAIL to 3L, FAIL to null)), 0.001f)
        assertEquals(1f, Progress.best(holds, marked, listOf(FAIL to 2L, SEND to null)), 0.001f)
    }

    @Test
    fun lineSitsAtTheHeightOfTheFraction() {
        assertEquals(0.5f, Progress.lineY(holds, marked, 0.5f)!!, 0.001f)
        assertEquals(0.1f, Progress.lineY(holds, marked, 1f)!!, 0.001f)
        assertEquals(0.9f, Progress.lineY(holds, marked, 0f)!!, 0.001f)
        assertNull(Progress.lineY(holds, emptyMap(), 0.5f))
    }

    @Test
    fun circuitColourIsThatOfItsLargestGroup() {
        val mixed = holds + hold(5, 0.3f, group = 1, argb = blue)
        val all = marked + (5L to SelectedHold(HoldRole.NORMAL, 9))
        assertEquals(yellow, Progress.circuitColor(mixed, all))
        assertEquals(blue, Progress.circuitColor(mixed, sel(5L to HoldRole.NORMAL)))
        assertEquals(0xFF9E9E9E.toInt(), Progress.circuitColor(mixed, emptyMap()))
    }

    @Test
    fun footOnlyHoldsDoNotSetTheStartOrTheTop() {
        // A foothold below the hand start and another above the last hand hold.
        val all = holds + hold(5, 0.98f) + hold(6, 0.02f)
        val feet = unmarked + (5L to SelectedHold(HoldRole.FOOT, 8)) + (6L to SelectedHold(HoldRole.FOOT, 9))
        assertEquals(0.5f, Progress.fraction(all, feet, 3), 0.001f)
        assertTrue(Progress.isTop(all, feet, 4))
        assertFalse(Progress.isTop(all, feet, 6))
    }

    @Test
    fun aCircuitOfOnlyFeetStillWorks() {
        val feet = sel(1L to HoldRole.FOOT, 4L to HoldRole.FOOT)
        assertEquals(1f, Progress.fraction(holds, feet, 4), 0.001f)
        assertEquals(0f, Progress.fraction(holds, feet, 1), 0.001f)
    }
}
