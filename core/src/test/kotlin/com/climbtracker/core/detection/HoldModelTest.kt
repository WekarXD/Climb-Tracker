package com.climbtracker.core.detection

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HoldModelTest {

    /** One candidate of the model: centre, size, objectness, hold and volume scores. */
    private fun candidate(cx: Float, cy: Float, w: Float, h: Float, objectness: Float, hold: Float, volume: Float) =
        floatArrayOf(cx, cy, w, h, objectness, hold, volume)

    @Test
    fun decodePlacesBoxesOnThePhoto() {
        // A photo of 1920 x 1440 fills the frame of 960 at half its size.
        val output = candidate(480f, 240f, 100f, 60f, 0.9f, 0.8f, 0.1f)
        val box = HoldBoxes.decode(output, 0.5f, 0.4f, 1920, 1440).single()
        assertEquals(860f, box.left, 0.01f)
        assertEquals(420f, box.top, 0.01f)
        assertEquals(1060f, box.right, 0.01f)
        assertEquals(540f, box.bottom, 0.01f)
        assertEquals(0.72f, box.score, 0.001f)
        assertFalse(box.volume)
    }

    @Test
    fun decodeDropsWhatTheModelIsNotSureOf() {
        val output = candidate(100f, 100f, 40f, 40f, 0.5f, 0.6f, 0f) + candidate(300f, 300f, 40f, 40f, 0.9f, 0.1f, 0.9f)
        val boxes = HoldBoxes.decode(output, 1f, 0.4f, 960, 960)
        assertEquals(1, boxes.size)
        assertTrue(boxes[0].volume)
    }

    @Test
    fun decodeKeepsBoxesInsideThePhoto() {
        val box = HoldBoxes.decode(candidate(10f, 700f, 60f, 60f, 1f, 1f, 0f), 1f, 0.4f, 960, 720).single()
        assertEquals(0f, box.left)
        assertEquals(720f, box.bottom)
    }

    @Test
    fun suppressKeepsTheSurerOfTwoBoxesOnOneHold() {
        val boxes = listOf(
            HoldBox(0f, 0f, 100f, 100f, 0.6f, false),
            HoldBox(5f, 5f, 105f, 105f, 0.9f, false),
            HoldBox(300f, 300f, 340f, 340f, 0.5f, false),
        )
        val kept = HoldBoxes.suppress(boxes)
        assertEquals(listOf(0.9f, 0.5f), kept.map { it.score })
    }

    @Test
    fun suppressKeepsAHoldMountedOnAVolume() {
        val boxes = listOf(HoldBox(0f, 0f, 400f, 400f, 0.9f, true), HoldBox(150f, 150f, 200f, 200f, 0.8f, false))
        assertEquals(2, HoldBoxes.suppress(boxes).size)
    }

    @Test
    fun higherSensitivityAcceptsFainterHolds() {
        assertEquals(0.4f, HoldBoxes.minScore(0.5f), 0.001f)
        assertTrue(HoldBoxes.minScore(0.9f) < HoldBoxes.minScore(0.5f))
        assertTrue(HoldBoxes.minScore(0.1f) > HoldBoxes.minScore(0.5f))
    }

    @Test
    fun framePutsBlueFirstAndPadsWithGrey() {
        val image = PixelImage(2, 1, intArrayOf(0xFF102030.toInt(), 0xFF102030.toInt()))
        val data = Frames.planes(image, 2, blueFirst = true, fill = 114f)
        assertEquals(0x30.toFloat(), data[0])
        assertEquals(0x20.toFloat(), data[4])
        assertEquals(0x10.toFloat(), data[8])
        // The second row of the frame is outside the photo.
        assertEquals(114f, data[2])
    }

    private fun square(x: Int, y: Int, side: Int, w: Int, h: Int, argb: Int = 0) = DetectedHold(
        contour = listOf(
            PointF(x / w.toFloat(), y / h.toFloat()), PointF((x + side) / w.toFloat(), y / h.toFloat()),
            PointF((x + side) / w.toFloat(), (y + side) / h.toFloat()), PointF(x / w.toFloat(), (y + side) / h.toFloat()),
        ),
        bounds = Bounds(x / w.toFloat(), y / h.toFloat(), (x + side) / w.toFloat(), (y + side) / h.toFloat()),
        center = PointF((x + side / 2f) / w, (y + side / 2f) / h),
        argb = argb,
        colorGroup = 0,
        area = side * side,
    )

    @Test
    fun colourOfAChalkedHoldIsItsOwn() {
        val image = TestImages.solid(100, 100, TestImages.BEIGE)
        TestImages.rect(image, 30, 30, 40, 40, TestImages.RED)
        // Chalk over a third of it.
        TestImages.rect(image, 30, 30, 40, 13, 0xFFE0E0E0.toInt())
        val lab = ColorSpace.toLab(HoldColour.of(Filters.toLab(Filters.split(image)), square(30, 30, 40, 100, 100)))
        assertTrue(lab.distance(ColorSpace.toLab(TestImages.RED)) < 5f, "colour $lab")
    }

    @Test
    fun colourOfAGreyHoldIsNotTheWallAroundIt() {
        val image = TestImages.solid(100, 100, TestImages.BEIGE)
        TestImages.rect(image, 30, 30, 40, 40, 0xFF808080.toInt())
        // An outline drawn too wide, taking in some wall.
        val lab = ColorSpace.toLab(HoldColour.of(Filters.toLab(Filters.split(image)), square(26, 26, 48, 100, 100)))
        assertTrue(hypot(lab.a, lab.b) < 3f, "colour $lab")
    }

    @Test
    fun paletteGroupsHoldsByTheirColour() {
        val holds = listOf(
            square(0, 0, 10, 100, 100, TestImages.RED), square(20, 0, 12, 100, 100, 0xFFB01818.toInt()),
            square(40, 0, 10, 100, 100, TestImages.BLUE), square(60, 0, 10, 100, 100, TestImages.BLACK),
            square(80, 0, 10, 100, 100, 0xFFF0F0F0.toInt()),
        )
        val result = Palette.group(holds)
        assertEquals(4, result.groups.size)
        assertEquals(listOf(1, 1, 1, 2), result.groups.map { it.holdCount }.sorted())
        val reds = result.holds.filter { it.center.x < 0.3f }.map { it.colorGroup }.toSet()
        assertEquals(1, reds.size)
        assertEquals(5, result.holds.size)
    }
}
