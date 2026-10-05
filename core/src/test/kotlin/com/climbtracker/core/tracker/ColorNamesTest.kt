package com.climbtracker.core.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorNamesTest {

    @Test
    fun namesSaturatedColoursByHue() {
        assertEquals("Rojo", ColorNames.nameOf(0xFFD02020.toInt()))
        assertEquals("Naranja", ColorNames.nameOf(0xFFE87020.toInt()))
        assertEquals("Amarillo", ColorNames.nameOf(0xFFE8C020.toInt()))
        assertEquals("Verde", ColorNames.nameOf(0xFF20A040.toInt()))
        assertEquals("Azul", ColorNames.nameOf(0xFF2060D0.toInt()))
        assertEquals("Azul", ColorNames.nameOf(0xFF0000FF.toInt()))
        assertEquals("Morado", ColorNames.nameOf(0xFF8040A0.toInt()))
        assertEquals("Rosa", ColorNames.nameOf(0xFFF070A0.toInt()))
    }

    @Test
    fun mutedColoursAreStillNamedByHue() {
        // Turquoise holds photographed in the shade: little chroma, but clearly not grey.
        assertEquals("Turquesa", ColorNames.nameOf(0xFF6FA89E.toInt()))
        assertEquals("Turquesa", ColorNames.nameOf(0xFF7FA59B.toInt()))
        // A grey hold with the slight warm cast of the gym lighting stays grey.
        assertEquals("Gris", ColorNames.nameOf(0xFF8C867A.toInt()))
    }

    @Test
    fun namesUnsaturatedColoursByLightness() {
        assertEquals("Negro", ColorNames.nameOf(0xFF151515.toInt()))
        assertEquals("Gris", ColorNames.nameOf(0xFF808080.toInt()))
        assertEquals("Blanco", ColorNames.nameOf(0xFFF0F0F0.toInt()))
    }
}
