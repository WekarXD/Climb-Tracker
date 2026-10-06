package com.climbtracker.core.editor

import com.climbtracker.core.detection.ColorSpace

/** Pure actions on [EditorState]. Every function returns a new state, or the same one if nothing changed. */
object Editor {
    const val MAX_UNDO = 50
    const val MAX_START = 2
    const val MAX_TOP = 1

    fun setTool(state: EditorState, tool: Tool): EditorState = state.copy(tool = tool)

    /** Adds the whole colour group, or removes it when every hold of the group is already selected. */
    fun toggleColorGroup(state: EditorState, group: Int): EditorState {
        val ids = state.holds.filter { it.colorGroup == group }.map { it.id }
        if (ids.isEmpty()) return state
        if (ids.all { it in state.selection }) return commit(state, state.selection - ids.toSet(), state.nextOrder)
        var order = state.nextOrder
        val selection = state.selection.toMutableMap()
        for (id in ids) if (id !in selection) selection[id] = SelectedHold(HoldRole.NORMAL, order++)
        return commit(state, selection, order)
    }

    fun toggleHold(state: EditorState, id: Long): EditorState {
        if (state.holds.none { it.id == id }) return state
        return if (id in state.selection) {
            commit(state, state.selection - id, state.nextOrder)
        } else {
            commit(state, state.selection + (id to SelectedHold(HoldRole.NORMAL, state.nextOrder)), state.nextOrder + 1)
        }
    }

    /** Sets or clears START, TOP or FOOT on a hold, adding it to the circuit if needed; START and TOP are limited. */
    fun toggleRole(state: EditorState, id: Long, role: HoldRole): EditorState {
        if (role == HoldRole.NORMAL || state.holds.none { it.id == id }) return state
        val selection = state.selection.toMutableMap()
        val current = selection[id]
        var order = state.nextOrder
        if (current?.role == role) {
            selection[id] = current.copy(role = HoldRole.NORMAL)
        } else {
            selection[id] = SelectedHold(role, order++)
            val limit = when (role) {
                HoldRole.START -> MAX_START
                HoldRole.TOP -> MAX_TOP
                else -> Int.MAX_VALUE
            }
            val marked = selection.entries.filter { it.value.role == role }.sortedBy { it.value.order }
            for (entry in marked.dropLast(limit)) {
                selection[entry.key] = entry.value.copy(role = HoldRole.NORMAL)
            }
        }
        return commit(state, selection, order)
    }

    /** A tap on a hold: selects its colour group, or marks its role, depending on the active tool. */
    fun tap(state: EditorState, id: Long): EditorState {
        val hold = state.holds.firstOrNull { it.id == id } ?: return state
        return when (state.tool) {
            Tool.CIRCUIT -> toggleColorGroup(state, hold.colorGroup)
            Tool.START -> toggleRole(state, id, HoldRole.START)
            Tool.TOP -> toggleRole(state, id, HoldRole.TOP)
            Tool.FOOT -> toggleRole(state, id, HoldRole.FOOT)
        }
    }

    fun addManualHold(state: EditorState, hold: EditorHold): EditorState = commit(
        state.copy(holds = state.holds + hold),
        state.selection + (hold.id to SelectedHold(HoldRole.NORMAL, state.nextOrder)),
        state.nextOrder + 1,
    )

    /**
     * The state once the hold [id] has been split into [upper] and [lower], which take its place
     * in the list and in the circuit. The steps taken so far refer to the hold that is gone, so
     * they can no longer be undone.
     */
    fun replaceHold(state: EditorState, id: Long, upper: EditorHold, lower: EditorHold): EditorState = state.copy(
        holds = state.holds.flatMap { if (it.id == id) listOf(upper, lower) else listOf(it) },
        selection = splitSelection(state.selection, id, upper.id, lower.id),
        undoStack = emptyList(),
    )

    /** [selection] with the hold [id] replaced by its two parts, each with what it keeps of its role. */
    fun splitSelection(selection: Map<Long, SelectedHold>, id: Long, upperId: Long, lowerId: Long): Map<Long, SelectedHold> {
        val selected = selection[id] ?: return selection
        return selection - id + (upperId to HoldCut.inherit(selected, upper = true)) + (lowerId to HoldCut.inherit(selected, upper = false))
    }

    fun undo(state: EditorState): EditorState {
        if (state.undoStack.isEmpty()) return state
        return state.copy(selection = state.undoStack.last(), undoStack = state.undoStack.dropLast(1))
    }

    fun palette(state: EditorState): List<PaletteEntry> =
        state.holds.groupBy { it.colorGroup }.toSortedMap().map { (group, holds) ->
            PaletteEntry(group, average(holds.map { it.argb }), holds.size, holds.all { it.id in state.selection })
        }

    /** Colour group whose palette colour is closest to [argb]; 0 when there are no groups. */
    fun nearestGroup(state: EditorState, argb: Int): Int {
        val target = ColorSpace.toLab(argb)
        return palette(state).minByOrNull { ColorSpace.toLab(it.argb).distance(target) }?.group ?: 0
    }

    private fun commit(state: EditorState, selection: Map<Long, SelectedHold>, nextOrder: Int): EditorState {
        if (selection == state.selection) return state
        return state.copy(
            selection = selection,
            undoStack = (state.undoStack + listOf(state.selection)).takeLast(MAX_UNDO),
            nextOrder = nextOrder,
        )
    }

    private fun average(colours: List<Int>): Int {
        var r = 0
        var g = 0
        var b = 0
        for (c in colours) {
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        val n = colours.size
        return (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }
}
