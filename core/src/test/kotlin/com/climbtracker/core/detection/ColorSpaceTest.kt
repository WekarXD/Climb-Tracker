package com.climbtracker.core.detection

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorSpaceTest {

    @Test
    fun whiteIsL100() {
        val lab = ColorSpace.toLab(0xFFFFFFFF.toInt())
        assertEquals(100f, lab.l, 0.5f)
        assertEquals(0f, lab.a, 0.5f)
        assertEquals(0f, lab.b, 0.5f)
    }

    @Test
    fun blackIsL0() {
        val lab = ColorSpace.toLab(0xFF000000.toInt())
        assertEquals(0f, lab.l, 0.5f)
    }

    @Test
    fun pureRedMatchesReference() {
        val lab = ColorSpace.toLab(255, 0, 0)
        assertEquals(53.24f, lab.l, 0.5f)
        assertEquals(80.09f, lab.a, 0.5f)
        assertEquals(67.20f, lab.b, 0.5f)
    }

    @Test
    fun roundTripKeepsColour() {
        for (argb in listOf(0xFFD02020, 0xFF2060D0, 0xFFE8E0D0, 0xFF151515, 0xFF20A040)) {
            val back = ColorSpace.toArgb(ColorSpace.toLab(argb.toInt()))
            for (shift in listOf(16, 8, 0)) {
                val expected = (argb.toInt() shr shift) and 0xFF
                val actual = (back shr shift) and 0xFF
                assertTrue(abs(expected - actual) <= 1, "channel $shift of ${argb.toString(16)}: $expected vs $actual")
            }
            assertEquals(0xFF, (back ushr 24) and 0xFF)
        }
    }

    @Test
    fun distances() {
        val a = Lab(50f, 10f, 10f)
        val b = Lab(60f, 13f, 14f)
        assertEquals(5f, a.chromaDistance(b), 0.001f)
        assertEquals(11.18f, a.distance(b), 0.01f)
    }
}
