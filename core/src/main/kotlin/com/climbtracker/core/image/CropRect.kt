package com.climbtracker.core.image

import kotlin.math.max
import kotlin.math.min

enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE }

/** Crop rectangle in normalised image coordinates (0..1). */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {

    /** Sorted, clamped to the image, and at least [minSize] on each side. */
    fun normalized(minSize: Float = 0.1f): CropRect {
        val (l, r) = span(left, right, minSize)
        val (t, b) = span(top, bottom, minSize)
        return CropRect(l, t, r, b)
    }

    /**
     * The rectangle after dragging [handle] by ([dx], [dy]), both normalised to the image size.
     * A corner stops [minSize] short of the opposite one; MOVE keeps the size. Always valid.
     */
    fun dragged(handle: CropHandle, dx: Float, dy: Float, minSize: Float = 0.1f): CropRect {
        val moved = when (handle) {
            CropHandle.TOP_LEFT -> copy(left = min(left + dx, right - minSize), top = min(top + dy, bottom - minSize))
            CropHandle.TOP_RIGHT -> copy(right = max(right + dx, left + minSize), top = min(top + dy, bottom - minSize))
            CropHandle.BOTTOM_LEFT -> copy(left = min(left + dx, right - minSize), bottom = max(bottom + dy, top + minSize))
            CropHandle.BOTTOM_RIGHT -> copy(right = max(right + dx, left + minSize), bottom = max(bottom + dy, top + minSize))
            CropHandle.MOVE -> {
                val w = right - left
                val h = bottom - top
                val l = min(max(left + dx, 0f), max(0f, 1f - w))
                val t = min(max(top + dy, 0f), max(0f, 1f - h))
                CropRect(l, t, l + w, t + h)
            }
        }
        return moved.normalized(minSize)
    }

    private fun span(a: Float, b: Float, minSize: Float): Pair<Float, Float> {
        var lo = min(a, b).coerceIn(0f, 1f)
        var hi = max(a, b).coerceIn(0f, 1f)
        if (hi - lo < minSize) {
            hi = min(1f, lo + minSize)
            lo = hi - minSize
        }
        return lo to hi
    }

    companion object {
        val FULL = CropRect(0f, 0f, 1f, 1f)
    }
}
