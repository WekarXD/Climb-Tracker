package com.climbtracker.core.image

import com.climbtracker.core.detection.PointF

/** Stores a contour as "x,y;x,y;..." for the database. */
object ContourCodec {

    fun encode(contour: List<PointF>): String = contour.joinToString(";") { "${it.x},${it.y}" }

    /** Malformed points are skipped rather than failing the whole contour. */
    fun decode(text: String): List<PointF> {
        if (text.isBlank()) return emptyList()
        return text.split(';').mapNotNull { pair ->
            val parts = pair.split(',')
            if (parts.size != 2) return@mapNotNull null
            val x = parts[0].toFloatOrNull() ?: return@mapNotNull null
            val y = parts[1].toFloatOrNull() ?: return@mapNotNull null
            PointF(x, y)
        }
    }
}
