package com.climbtracker.core.image

import com.climbtracker.core.detection.PixelImage
import com.climbtracker.core.detection.PointF
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CropQuadTest {

    private val slanted = CropQuad(PointF(0.2f, 0.1f), PointF(0.9f, 0.2f), PointF(0.8f, 0.9f), PointF(0.1f, 0.7f))

    private fun assertNear(expected: PointF, actual: PointF) {
        assertTrue(abs(expected.x - actual.x) < 1e-4f && abs(expected.y - actual.y) < 1e-4f, "expected $expected, got $actual")
    }

    @Test
    fun aSquareShapeIsAPlainCrop() {
        val rect = CropRect(0.2f, 0.1f, 0.8f, 0.9f)
        assertEquals(rect, CropQuad.of(rect).asRect())
        assertEquals(CropRect.FULL, CropQuad.FULL.asRect())
        assertNull(slanted.asRect())
    }

    @Test
    fun theCornersOfThePictureLandOnTheCornersOfTheShape() {
        assertNear(slanted.topLeft, slanted.toPhoto(0f, 0f))
        assertNear(slanted.topRight, slanted.toPhoto(1f, 0f))
        assertNear(slanted.bottomRight, slanted.toPhoto(1f, 1f))
        assertNear(slanted.bottomLeft, slanted.toPhoto(0f, 1f))
    }

    @Test
    fun straightLinesStayStraight() {
        // The middle of the picture is where the diagonals of the shape cross.
        val centre = slanted.toPhoto(0.5f, 0.5f)
        fun side(a: PointF, b: PointF, p: PointF) = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        assertTrue(abs(side(slanted.topLeft, slanted.bottomRight, centre)) < 1e-4f)
        assertTrue(abs(side(slanted.topRight, slanted.bottomLeft, centre)) < 1e-4f)
    }

    @Test
    fun aCornerMovesAloneAndStaysInsideTheImage() {
        val moved = CropQuad.FULL.dragged(CropHandle.TOP_LEFT, 0.2f, 0.1f)
        assertEquals(PointF(0.2f, 0.1f), moved.topLeft)
        assertEquals(CropQuad.FULL.topRight, moved.topRight)
        assertEquals(CropQuad.FULL.bottomLeft, moved.bottomLeft)
        assertEquals(PointF(1f, 0f), CropQuad.FULL.dragged(CropHandle.TOP_RIGHT, 0.5f, -0.5f).topRight)
    }

    @Test
    fun aDragThatWouldFoldTheShapeOverIsNotMade() {
        // The top left corner dragged past the opposite one.
        assertEquals(CropQuad.FULL, CropQuad.FULL.dragged(CropHandle.TOP_LEFT, 0.95f, 0.95f))
        // Dragged onto its neighbour: a side of no length.
        assertEquals(CropQuad.FULL, CropQuad.FULL.dragged(CropHandle.TOP_LEFT, 0.98f, 0f))
    }

    @Test
    fun movingKeepsTheShapeAndStopsAtTheEdge() {
        val moved = slanted.dragged(CropHandle.MOVE, 1f, 1f)
        assertTrue(abs(moved.bounds().right - 1f) < 1e-5f && abs(moved.bounds().bottom - 1f) < 1e-5f)
        assertTrue(abs((moved.topRight.x - moved.topLeft.x) - (slanted.topRight.x - slanted.topLeft.x)) < 1e-5f)
    }

    @Test
    fun thePictureIsAsLargeAsTheLongerSides() {
        val quad = CropQuad(PointF(0f, 0f), PointF(1f, 0f), PointF(0.75f, 1f), PointF(0.25f, 1f))
        // The slanted sides run 100 px across and 200 px down.
        assertEquals(400 to 224, quad.outputSize(400, 200))
        assertEquals(200 to 100, CropQuad.of(CropRect(0.25f, 0.25f, 0.75f, 0.75f)).outputSize(400, 200))
    }

    @Test
    fun straighteningASquareShapeCopiesThatPartOfThePhoto() {
        val image = PixelImage(8, 4, IntArray(32) { 0xFF000000.toInt() or (it * 8) })
        val part = CropQuad.of(CropRect(0.25f, 0f, 0.75f, 0.5f)).straighten(image)
        assertEquals(4, part.width)
        assertEquals(2, part.height)
        for (y in 0 until 2) for (x in 0 until 4) assertEquals(image.argb[y * 8 + x + 2], part.argb[y * 4 + x])
    }

    @Test
    fun aSlantedPanelComesOutAsARectangle() {
        // A white trapezium, narrower at the top, on black: a wall seen from below.
        val size = 200
        val quad = CropQuad(PointF(0.3f, 0.1f), PointF(0.7f, 0.1f), PointF(0.9f, 0.9f), PointF(0.1f, 0.9f))
        val argb = IntArray(size * size) { i ->
            val x = (i % size + 0.5f) / size
            val y = (i / size + 0.5f) / size
            val t = (y - 0.1f) / 0.8f
            val inside = y in 0.1f..0.9f && x in (0.3f - 0.2f * t)..(0.7f + 0.2f * t)
            if (inside) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }
        val straight = quad.straighten(PixelImage(size, size, argb))
        // Away from the very edge, where the outline is blended with the black, all of it is wall.
        var white = 0
        var total = 0
        for (y in 3 until straight.height - 3) for (x in 3 until straight.width - 3) {
            total++
            if (straight.argb[y * straight.width + x] == 0xFFFFFFFF.toInt()) white++
        }
        assertEquals(total, white)
    }
}
