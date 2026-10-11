package com.climbtracker.core.editor

import com.climbtracker.core.detection.PointF
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HoldCutTest {

    /** A square from 0.2 to 0.6, clockwise from the top left. */
    private val square = listOf(PointF(0.2f, 0.2f), PointF(0.6f, 0.2f), PointF(0.6f, 0.6f), PointF(0.2f, 0.6f))

    private fun area(points: List<PointF>): Float {
        var sum = 0f
        for (i in points.indices) {
            val p = points[i]
            val q = points[(i + 1) % points.size]
            sum += p.x * q.y - q.x * p.y
        }
        return abs(sum) / 2f
    }

    @Test
    fun aLineAcrossAHoldLeavesTwoPartsTheUpperFirst() {
        val parts = assertNotNull(HoldCut.split(square, PointF(0.1f, 0.3f), PointF(0.7f, 0.3f)))
        val (upper, lower) = parts
        assertTrue(upper.all { it.y <= 0.3001f }, "upper: $upper")
        assertTrue(lower.all { it.y >= 0.2999f }, "lower: $lower")
        assertEquals(0.04f, area(upper), 1e-4f)
        assertEquals(0.12f, area(lower), 1e-4f)
    }

    @Test
    fun theUpperPartComesFirstWhicheverWayTheLineIsDrawn() {
        val (upper, _) = assertNotNull(HoldCut.split(square, PointF(0.7f, 0.5f), PointF(0.1f, 0.5f)))
        assertEquals(0.12f, area(upper), 1e-4f)
    }

    @Test
    fun aSlantedLineCutsToo() {
        // Enters through the left side at 0.3 and leaves through the right one at 0.5.
        val (one, other) = assertNotNull(HoldCut.split(square, PointF(0.1f, 0.25f), PointF(0.7f, 0.55f)))
        assertEquals(0.08f, area(one), 1e-4f)
        assertEquals(0.08f, area(other), 1e-4f)
        assertEquals(0.16f, area(one) + area(other), 1e-4f)
    }

    @Test
    fun aLineThatStopsInsideTheHoldCutsNothing() {
        assertNull(HoldCut.split(square, PointF(0.1f, 0.4f), PointF(0.4f, 0.4f)))
    }

    @Test
    fun aLineThatMissesTheHoldCutsNothing() {
        assertNull(HoldCut.split(square, PointF(0.1f, 0.8f), PointF(0.7f, 0.8f)))
    }

    @Test
    fun aLineThatOnlyShavesACornerCutsNothing() {
        assertNull(HoldCut.split(square, PointF(0.19f, 0.23f), PointF(0.23f, 0.19f)))
    }

    @Test
    fun theTopStaysOnTheUpperPartAndTheStartOnTheLowerOne() {
        val top = SelectedHold(HoldRole.TOP, 3)
        assertEquals(HoldRole.TOP, HoldCut.inherit(top, upper = true).role)
        assertEquals(HoldRole.NORMAL, HoldCut.inherit(top, upper = false).role)
        val start = SelectedHold(HoldRole.START, 1)
        assertEquals(HoldRole.NORMAL, HoldCut.inherit(start, upper = true).role)
        assertEquals(HoldRole.START, HoldCut.inherit(start, upper = false).role)
        val foot = SelectedHold(HoldRole.FOOT, 2)
        assertEquals(foot, HoldCut.inherit(foot, upper = true))
        assertEquals(foot, HoldCut.inherit(foot, upper = false))
    }

    @Test
    fun splittingAHoldInTheEditorKeepsItsPlaceInTheCircuit() {
        val hold = EditorHold(7, square, 2, 0xFF2060D0.toInt())
        val other = EditorHold(8, square, 2, 0xFF2060D0.toInt())
        val state = Editor.toggleRole(Editor.toggleHold(EditorState(listOf(hold, other)), 8), 7, HoldRole.TOP)
        val (upper, lower) = assertNotNull(HoldCut.split(square, PointF(0.1f, 0.4f), PointF(0.7f, 0.4f)))

        val after = Editor.replaceHold(state, 7, hold.copy(id = 20, contour = upper), hold.copy(id = 21, contour = lower))

        assertEquals(listOf(20L, 21L, 8L), after.holds.map { it.id })
        assertEquals(HoldRole.TOP, after.selection.getValue(20).role)
        assertEquals(HoldRole.NORMAL, after.selection.getValue(21).role)
        assertTrue(8L in after.selection && 7L !in after.selection)
        // Earlier steps refer to the hold that is gone, so they cannot be gone back to.
        assertTrue(!after.canUndo)
    }

    @Test
    fun splittingAHoldThatIsNotInTheCircuitLeavesTheCircuitAlone() {
        val hold = EditorHold(7, square, 2, 0xFF2060D0.toInt())
        val state = EditorState(listOf(hold))
        val after = Editor.replaceHold(state, 7, hold.copy(id = 20), hold.copy(id = 21))
        assertEquals(listOf(20L, 21L), after.holds.map { it.id })
        assertTrue(after.selection.isEmpty())
    }
}
