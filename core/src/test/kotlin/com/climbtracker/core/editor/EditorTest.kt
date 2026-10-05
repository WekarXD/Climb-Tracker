package com.climbtracker.core.editor

import com.climbtracker.core.detection.PointF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EditorTest {

    private val red = 0xFFD02020.toInt()
    private val blue = 0xFF2060D0.toInt()

    private fun hold(id: Long, group: Int, argb: Int) = EditorHold(id, listOf(PointF(0f, 0f)), group, argb)

    private val base = EditorState(
        holds = listOf(hold(1, 0, red), hold(2, 0, red), hold(3, 0, red), hold(4, 1, blue), hold(5, 1, blue)),
    )

    @Test
    fun toggleColorGroupSelectsWholeGroup() {
        val s = Editor.toggleColorGroup(base, 0)
        assertEquals(setOf(1L, 2L, 3L), s.selection.keys)
        assertTrue(s.selection.values.all { it.role == HoldRole.NORMAL })
    }

    @Test
    fun toggleColorGroupCompletesPartialGroup() {
        val s = Editor.toggleColorGroup(Editor.toggleHold(base, 2), 0)
        assertEquals(setOf(1L, 2L, 3L), s.selection.keys)
    }

    @Test
    fun toggleColorGroupRemovesFullySelectedGroup() {
        val s = Editor.toggleColorGroup(Editor.toggleColorGroup(base, 0), 0)
        assertTrue(s.selection.isEmpty())
    }

    @Test
    fun toggleColorGroupKeepsOtherGroups() {
        val s = Editor.toggleColorGroup(Editor.toggleColorGroup(base, 0), 1)
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), s.selection.keys)
    }

    @Test
    fun toggleUnknownGroupDoesNothing() {
        assertSame(base, Editor.toggleColorGroup(base, 9))
    }

    @Test
    fun toggleHoldAddsThenRemoves() {
        val added = Editor.toggleHold(base, 4)
        assertEquals(setOf(4L), added.selection.keys)
        assertTrue(Editor.toggleHold(added, 4).selection.isEmpty())
    }

    @Test
    fun toggleUnknownHoldDoesNothing() {
        assertSame(base, Editor.toggleHold(base, 99))
    }

    @Test
    fun toggleRoleMarksAndAddsHold() {
        val s = Editor.toggleRole(base, 1, HoldRole.START)
        assertEquals(HoldRole.START, s.selection.getValue(1).role)
    }

    @Test
    fun toggleRoleTwiceReturnsToNormalButStaysInCircuit() {
        val s = Editor.toggleRole(Editor.toggleRole(base, 1, HoldRole.START), 1, HoldRole.START)
        assertEquals(HoldRole.NORMAL, s.selection.getValue(1).role)
    }

    @Test
    fun thirdStartDemotesOldest() {
        var s = Editor.toggleRole(base, 1, HoldRole.START)
        s = Editor.toggleRole(s, 2, HoldRole.START)
        s = Editor.toggleRole(s, 3, HoldRole.START)
        assertEquals(HoldRole.NORMAL, s.selection.getValue(1).role)
        assertEquals(HoldRole.START, s.selection.getValue(2).role)
        assertEquals(HoldRole.START, s.selection.getValue(3).role)
    }

    @Test
    fun secondTopDemotesFirst() {
        val s = Editor.toggleRole(Editor.toggleRole(base, 4, HoldRole.TOP), 5, HoldRole.TOP)
        assertEquals(HoldRole.NORMAL, s.selection.getValue(4).role)
        assertEquals(HoldRole.TOP, s.selection.getValue(5).role)
    }

    @Test
    fun topReplacesStartOnSameHold() {
        val s = Editor.toggleRole(Editor.toggleRole(base, 1, HoldRole.START), 1, HoldRole.TOP)
        assertEquals(HoldRole.TOP, s.selection.getValue(1).role)
    }

    @Test
    fun removingHoldDropsItsRole() {
        val s = Editor.toggleHold(Editor.toggleRole(base, 1, HoldRole.START), 1)
        assertNull(s.selection[1])
        assertEquals(HoldRole.NORMAL, Editor.toggleHold(s, 1).selection.getValue(1).role)
    }

    @Test
    fun toggleRoleNormalDoesNothing() {
        assertSame(base, Editor.toggleRole(base, 1, HoldRole.NORMAL))
    }

    @Test
    fun tapDependsOnTool() {
        assertEquals(setOf(4L, 5L), Editor.tap(base, 4).selection.keys)
        val start = Editor.tap(Editor.setTool(base, Tool.START), 4)
        assertEquals(mapOf(4L to HoldRole.START), start.selection.mapValues { it.value.role })
        val top = Editor.tap(Editor.setTool(base, Tool.TOP), 5)
        assertEquals(HoldRole.TOP, top.selection.getValue(5).role)
    }

    @Test
    fun addManualHoldAppendsAndSelects() {
        val s = Editor.addManualHold(base, hold(10, 1, blue))
        assertEquals(6, s.holds.size)
        assertEquals(setOf(10L), s.selection.keys)
    }

    @Test
    fun undoRevertsLastAction() {
        val one = Editor.toggleHold(base, 1)
        val two = Editor.toggleColorGroup(one, 1)
        assertTrue(two.canUndo)
        val back = Editor.undo(two)
        assertEquals(one.selection, back.selection)
        assertTrue(Editor.undo(back).selection.isEmpty())
        assertFalse(Editor.undo(Editor.undo(back)).canUndo)
    }

    @Test
    fun undoOnEmptyStackDoesNothing() {
        assertSame(base, Editor.undo(base))
    }

    @Test
    fun setToolIsNotUndoable() {
        assertFalse(Editor.setTool(base, Tool.TOP).canUndo)
    }

    @Test
    fun undoStackIsLimitedTo50() {
        var s = base
        repeat(60) { s = Editor.toggleHold(s, 1) }
        assertEquals(50, s.undoStack.size)
    }

    @Test
    fun paletteSummarisesGroups() {
        val p = Editor.palette(Editor.toggleColorGroup(base, 1))
        assertEquals(listOf(0, 1), p.map { it.group })
        assertEquals(listOf(3, 2), p.map { it.count })
        assertEquals(listOf(false, true), p.map { it.allSelected })
        assertEquals(red, p[0].argb)
    }

    @Test
    fun nearestGroupPicksClosestColour() {
        assertEquals(1, Editor.nearestGroup(base, 0xFF3070E0.toInt()))
        assertEquals(0, Editor.nearestGroup(base, 0xFFC03030.toInt()))
        assertEquals(0, Editor.nearestGroup(EditorState(emptyList()), red))
    }

    @Test
    fun footRoleHasNoLimit() {
        var s = base
        for (id in 1L..5L) s = Editor.toggleRole(s, id, HoldRole.FOOT)
        assertEquals(5, s.selection.values.count { it.role == HoldRole.FOOT })
    }

    @Test
    fun footToolMarksAndUnmarksFeet() {
        val foot = Editor.tap(Editor.setTool(base, Tool.FOOT), 4)
        assertEquals(HoldRole.FOOT, foot.selection.getValue(4).role)
        assertEquals(HoldRole.NORMAL, Editor.tap(foot, 4).selection.getValue(4).role)
    }

    @Test
    fun footReplacesAnotherRoleOnTheSameHold() {
        val s = Editor.toggleRole(Editor.toggleRole(base, 1, HoldRole.START), 1, HoldRole.FOOT)
        assertEquals(HoldRole.FOOT, s.selection.getValue(1).role)
    }
}
