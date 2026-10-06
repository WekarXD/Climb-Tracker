package com.climbtracker.core.tracker

import com.climbtracker.core.detection.ColorSpace
import kotlin.math.atan2
import kotlin.math.hypot

/** The everyday colours holds are told apart by. [spanish] is the name the statistics use as a key. */
enum class ColorName(val spanish: String) {
    BLACK("Negro"), WHITE("Blanco"), GREY("Gris"), PINK("Rosa"), RED("Rojo"), ORANGE("Naranja"),
    YELLOW("Amarillo"), GREEN("Verde"), TURQUOISE("Turquesa"), BLUE("Azul"), PURPLE("Morado"),
}

/** Everyday name for a hold colour, used to label and group boulders. */
object ColorNames {
    /**
     * Below this chroma a colour is named by lightness alone. Low enough for coloured holds
     * photographed in the shade, high enough for grey holds under warm lighting.
     */
    private const val MIN_CHROMA = 9f

    /** The name in Spanish. */
    fun nameOf(argb: Int): String = of(argb).spanish

    fun of(argb: Int): ColorName {
        val lab = ColorSpace.toLab(argb)
        if (hypot(lab.a, lab.b) < MIN_CHROMA) {
            return when {
                lab.l < 30f -> ColorName.BLACK
                lab.l > 80f -> ColorName.WHITE
                else -> ColorName.GREY
            }
        }
        var hue = Math.toDegrees(atan2(lab.b.toDouble(), lab.a.toDouble()))
        if (hue < 0) hue += 360.0
        return when {
            hue < 25 -> ColorName.PINK
            hue < 50 -> ColorName.RED
            hue < 75 -> ColorName.ORANGE
            hue < 110 -> ColorName.YELLOW
            hue < 165 -> ColorName.GREEN
            hue < 250 -> ColorName.TURQUOISE
            hue < 312 -> ColorName.BLUE
            hue < 340 -> ColorName.PURPLE
            else -> ColorName.PINK
        }
    }
}
