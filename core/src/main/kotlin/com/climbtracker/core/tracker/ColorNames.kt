package com.climbtracker.core.tracker

import com.climbtracker.core.detection.ColorSpace
import kotlin.math.atan2
import kotlin.math.hypot

/** Everyday Spanish name for a hold colour, used to label and group boulders. */
object ColorNames {
    /**
     * Below this chroma a colour is named by lightness alone. Low enough for coloured holds
     * photographed in the shade, high enough for grey holds under warm lighting.
     */
    private const val MIN_CHROMA = 9f

    fun nameOf(argb: Int): String {
        val lab = ColorSpace.toLab(argb)
        if (hypot(lab.a, lab.b) < MIN_CHROMA) {
            return when {
                lab.l < 30f -> "Negro"
                lab.l > 80f -> "Blanco"
                else -> "Gris"
            }
        }
        var hue = Math.toDegrees(atan2(lab.b.toDouble(), lab.a.toDouble()))
        if (hue < 0) hue += 360.0
        return when {
            hue < 25 -> "Rosa"
            hue < 50 -> "Rojo"
            hue < 75 -> "Naranja"
            hue < 110 -> "Amarillo"
            hue < 165 -> "Verde"
            hue < 250 -> "Turquesa"
            hue < 312 -> "Azul"
            hue < 340 -> "Morado"
            else -> "Rosa"
        }
    }
}
