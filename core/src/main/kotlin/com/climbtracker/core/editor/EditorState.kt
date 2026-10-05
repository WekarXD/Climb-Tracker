package com.climbtracker.core.editor

import com.climbtracker.core.detection.PointF

/** FOOT marks a hold that may only be used with the feet. */
enum class HoldRole { NORMAL, START, TOP, FOOT }

enum class Tool { CIRCUIT, START, TOP, FOOT }

data class EditorHold(val id: Long, val contour: List<PointF>, val colorGroup: Int, val argb: Int)

/** [order] grows with each marking, so the oldest hold of a role has the lowest value. */
data class SelectedHold(val role: HoldRole, val order: Int)

data class EditorState(
    val holds: List<EditorHold>,
    val selection: Map<Long, SelectedHold> = emptyMap(),
    val tool: Tool = Tool.CIRCUIT,
    val undoStack: List<Map<Long, SelectedHold>> = emptyList(),
    val nextOrder: Int = 0,
) {
    val canUndo: Boolean get() = undoStack.isNotEmpty()
}

data class PaletteEntry(val group: Int, val argb: Int, val count: Int, val allSelected: Boolean)
