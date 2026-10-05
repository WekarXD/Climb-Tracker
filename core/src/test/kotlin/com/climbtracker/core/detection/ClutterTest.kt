package com.climbtracker.core.detection

import kotlin.test.Test
import kotlin.test.assertEquals

class ClutterTest {

    private val blue = 0xFF2060C0.toInt()
    private val red = 0xFFD02020.toInt()
    private val grey = 0xFF707070.toInt()
    private val white = 0xFFF0F0F0.toInt()

    /** A square hold of [side] (fraction of the image) centred at ([x], [y]). */
    private fun hold(x: Float, y: Float, side: Float, argb: Int): DetectedHold {
        val r = side / 2
        return DetectedHold(
            contour = listOf(PointF(x - r, y - r), PointF(x + r, y - r), PointF(x + r, y + r), PointF(x - r, y + r)),
            bounds = Bounds(x - r, y - r, x + r, y + r),
            center = PointF(x, y),
            argb = argb,
            colorGroup = 0,
            area = (side * 1000 * side * 1000).toInt(),
        )
    }

    private fun kept(vararg holds: DetectedHold) = Clutter.remove(holds.toList(), 1000, 1000).map { it.center }

    @Test
    fun aColourlessSpeckInsideAHoldIsPartOfIt() {
        val big = hold(0.5f, 0.5f, 0.2f, blue)
        val bolt = hold(0.52f, 0.5f, 0.02f, grey)
        assertEquals(listOf(big.center), kept(big, bolt))
    }

    @Test
    fun aPatchOfTheSameColourInsideAHoldIsPartOfIt() {
        val big = hold(0.5f, 0.5f, 0.2f, blue)
        val patch = hold(0.45f, 0.55f, 0.03f, 0xFF3070D0.toInt())
        assertEquals(listOf(big.center), kept(big, patch))
    }

    @Test
    fun aHoldOfAnotherColourOnAVolumeIsKept() {
        val volume = hold(0.5f, 0.5f, 0.3f, grey)
        val onIt = hold(0.5f, 0.45f, 0.04f, red)
        assertEquals(2, kept(volume, onIt).size)
    }

    @Test
    fun aDarkHoldOnAPaleVolumeIsKept() {
        val volume = hold(0.5f, 0.5f, 0.3f, 0xFFB0B0B0.toInt())
        val onIt = hold(0.5f, 0.45f, 0.04f, 0xFF202020.toInt())
        assertEquals(2, kept(volume, onIt).size)
    }

    @Test
    fun aHoldBesideAnotherIsKept() {
        assertEquals(2, kept(hold(0.3f, 0.5f, 0.2f, blue), hold(0.45f, 0.5f, 0.02f, grey)).size)
    }

    @Test
    fun aRowOfAlikeColourlessShapesIsLetteringOrAGrille() {
        val letters = (0 until 5).map { hold(0.10f + it * 0.05f, 0.10f + it * 0.01f, 0.03f, white) }
        val hold = hold(0.6f, 0.6f, 0.05f, white)
        assertEquals(listOf(hold.center), kept(*letters.toTypedArray(), hold))
    }

    @Test
    fun threeAlikeShapesInARowAreStillHolds() {
        val three = (0 until 3).map { hold(0.10f + it * 0.05f, 0.10f, 0.03f, white) }
        assertEquals(3, kept(*three.toTypedArray()).size)
    }

    @Test
    fun alikeShapesScatteredAboutAreStillHolds() {
        val scattered = listOf(0.10f to 0.10f, 0.15f to 0.16f, 0.11f to 0.21f, 0.17f to 0.10f, 0.16f to 0.24f).map { (x, y) -> hold(x, y, 0.03f, grey) }
        assertEquals(5, kept(*scattered.toTypedArray()).size)
    }

    @Test
    fun aRowOfColouredHoldsIsARoute() {
        val row = (0 until 5).map { hold(0.10f + it * 0.05f, 0.10f, 0.03f, red) }
        assertEquals(5, kept(*row.toTypedArray()).size)
    }

    @Test
    fun aRowOfShapesFarApartIsNotLettering() {
        val row = (0 until 5).map { hold(0.10f + it * 0.18f, 0.10f, 0.03f, grey) }
        assertEquals(5, kept(*row.toTypedArray()).size)
    }
}
