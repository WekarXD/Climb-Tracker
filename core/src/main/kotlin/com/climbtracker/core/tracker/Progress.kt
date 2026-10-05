package com.climbtracker.core.tracker

import com.climbtracker.core.editor.EditorHold
import com.climbtracker.core.editor.HoldRole
import com.climbtracker.core.editor.SelectedHold

/**
 * How far up a circuit an attempt got, measured by height on the photo between the start and
 * the top. Heights are normalised image coordinates, so 0 is the top edge of the photo.
 */
object Progress {
    private const val GREY = 0xFF9E9E9E.toInt()

    /** A hold that is not the top never reads as complete. */
    private const val ALMOST = 0.99f

    fun centerY(hold: EditorHold): Float =
        if (hold.contour.isEmpty()) 0f else hold.contour.map { it.y }.average().toFloat()

    /** True for the hold marked TOP, or for the highest hold when none is marked. */
    fun isTop(holds: List<EditorHold>, selection: Map<Long, SelectedHold>, id: Long): Boolean {
        val circuit = holds.filter { it.id in selection }
        if (circuit.none { it.id == id }) return false
        if (selection.values.any { it.role == HoldRole.TOP }) return selection[id]?.role == HoldRole.TOP
        val hands = hands(circuit, selection)
        return hands.size > 1 && hands.minBy { centerY(it) }.id == id ||
            hands.size == 1 && hands[0].id == id && selection[id]?.role != HoldRole.START
    }

    /** 0..1 for the hold [reachedId]; 0 when it is null or not part of the circuit. */
    fun fraction(holds: List<EditorHold>, selection: Map<Long, SelectedHold>, reachedId: Long?): Float {
        val circuit = holds.filter { it.id in selection }
        val reached = circuit.firstOrNull { it.id == reachedId } ?: return 0f
        if (isTop(holds, selection, reached.id)) return 1f
        val start = startY(circuit, selection)
        val top = topY(circuit, selection)
        val span = start - top
        if (span <= 0f) return 0f
        return ((start - centerY(reached)) / span).coerceIn(0f, ALMOST)
    }

    /** Best fraction over [attempts] (result, last hold reached); 1 when any of them was a send. */
    fun best(
        holds: List<EditorHold>,
        selection: Map<Long, SelectedHold>,
        attempts: List<Pair<AttemptResult, Long?>>,
    ): Float {
        if (attempts.any { it.first == AttemptResult.SEND }) return 1f
        return attempts.maxOfOrNull { fraction(holds, selection, it.second) } ?: 0f
    }

    /** Height at which [fraction] sits, for drawing the progress line; null for an empty circuit. */
    fun lineY(holds: List<EditorHold>, selection: Map<Long, SelectedHold>, fraction: Float): Float? {
        val circuit = holds.filter { it.id in selection }
        if (circuit.isEmpty()) return null
        val start = startY(circuit, selection)
        return start - fraction.coerceIn(0f, 1f) * (start - topY(circuit, selection))
    }

    /** Mean colour of the colour group with most holds in the circuit; grey for an empty circuit. */
    fun circuitColor(holds: List<EditorHold>, selection: Map<Long, SelectedHold>): Int {
        val group = holds.filter { it.id in selection }.groupBy { it.colorGroup }.values.maxByOrNull { it.size }
            ?: return GREY
        var r = 0
        var g = 0
        var b = 0
        for (hold in group) {
            r += (hold.argb shr 16) and 0xFF
            g += (hold.argb shr 8) and 0xFF
            b += hold.argb and 0xFF
        }
        val n = group.size
        return (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }

    /** Mean height of the START holds, else the lowest hold of the circuit. */
    private fun startY(circuit: List<EditorHold>, selection: Map<Long, SelectedHold>): Float {
        val starts = circuit.filter { selection[it.id]?.role == HoldRole.START }
        return if (starts.isNotEmpty()) starts.map { centerY(it) }.average().toFloat() else hands(circuit, selection).maxOf { centerY(it) }
    }

    /** Height of the TOP hold, else the highest hold of the circuit. */
    private fun topY(circuit: List<EditorHold>, selection: Map<Long, SelectedHold>): Float {
        val top = circuit.firstOrNull { selection[it.id]?.role == HoldRole.TOP }
        return if (top != null) centerY(top) else hands(circuit, selection).minOf { centerY(it) }
    }

    /** The holds that count for height: all but the foot-only ones, unless there is nothing else. */
    private fun hands(circuit: List<EditorHold>, selection: Map<Long, SelectedHold>): List<EditorHold> =
        circuit.filter { selection[it.id]?.role != HoldRole.FOOT }.ifEmpty { circuit }
}
