package com.climbtracker.core.detection

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SilhouettesTest {

    private val width = 1024
    private val height = 768

    /** What the detector found: a small square at ([cx], [cy]) pixels, [side] across. */
    private fun square(cx: Int, cy: Int, side: Int): DetectedHold {
        val x0 = (cx - side / 2f) / width; val x1 = (cx + side / 2f) / width
        val y0 = (cy - side / 2f) / height; val y1 = (cy + side / 2f) / height
        return DetectedHold(
            contour = listOf(PointF(x0, y0), PointF(x1, y0), PointF(x1, y1), PointF(x0, y1)),
            bounds = Bounds(x0, y0, x1, y1),
            center = PointF(cx / width.toFloat(), cy / height.toFloat()),
            argb = 0xFF2060C0.toInt(),
            colorGroup = 3,
            area = side * side,
        )
    }

    /** A mask as the model gives it: positive inside a disc at ([cx], [cy]) of the 1024 frame. */
    private fun disc(cx: Float, cy: Float, radius: Float): FloatArray {
        val size = Silhouettes.MASK
        return FloatArray(size * size) { i ->
            val x = (i % size + 0.5f) * 4f
            val y = (i / size + 0.5f) * 4f
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= radius * radius) 6f else -6f
        }
    }

    @Test
    fun theHoldTakesTheShapeOfTheMask() {
        val found = square(512, 400, 60)
        val refined = Silhouettes.refine(found, disc(512f, 400f, 80f), 0, width, height)

        assertEquals(PI * 80 * 80, refined.area.toDouble(), PI * 80 * 80 * 0.1)
        assertEquals((512 - 80) / width.toFloat(), refined.bounds.left, 0.01f)
        assertEquals((512 + 80) / width.toFloat(), refined.bounds.right, 0.01f)
        assertEquals((400 - 80) / height.toFloat(), refined.bounds.top, 0.012f)
        assertEquals((400 + 80) / height.toFloat(), refined.bounds.bottom, 0.012f)
        assertTrue(abs(refined.center.x - 0.5f) < 0.01f)
        assertTrue(refined.contour.size >= 12)
        // The colour is still the detector's.
        assertEquals(found.argb, refined.argb)
        assertEquals(found.colorGroup, refined.colorGroup)
    }

    @Test
    fun aMaskThatIsMostlyWallIsNotBelieved() {
        val found = square(512, 400, 30)
        assertSame(found, Silhouettes.refine(found, FloatArray(Silhouettes.MASK * Silhouettes.MASK) { 6f }, 0, width, height))
        assertSame(found, Silhouettes.refine(found, disc(512f, 400f, 300f), 0, width, height))
    }

    @Test
    fun aMaskOfSomethingElseIsNotBelieved() {
        val found = square(512, 400, 60)
        assertSame(found, Silhouettes.refine(found, disc(640f, 400f, 40f), 0, width, height))
        assertSame(found, Silhouettes.refine(found, FloatArray(Silhouettes.MASK * Silhouettes.MASK) { -6f }, 0, width, height))
    }

    @Test
    fun theBoxIsGivenInTheFrameOfTheModel() {
        // A photo twice the size of the frame: every pixel is half a frame unit.
        val hold = DetectedHold(emptyList(), Bounds(0.25f, 0.5f, 0.5f, 0.75f), PointF(0.4f, 0.6f), 0, 0, 10)
        val box = Silhouettes.box(hold, 2048, 1536)
        assertEquals(256f, box[0], 0.5f)
        assertEquals(384f, box[1], 0.5f)
        assertEquals(512f, box[2], 0.5f)
        assertEquals(576f, box[3], 0.5f)
    }

    @Test
    fun aHoldOfAFewPixelsIsNotWorthRedrawing() {
        assertTrue(Silhouettes.worthRefining(square(512, 400, 40), width, height))
        assertTrue(!Silhouettes.worthRefining(square(512, 400, 8), width, height))
    }

    @Test
    fun twoDetectionsOfOneHoldBecomeOne() {
        val mask = disc(512f, 400f, 80f)
        val a = Silhouettes.refine(square(500, 400, 60), mask, 0, width, height)
        val b = Silhouettes.refine(square(530, 410, 60), mask, 0, width, height)
        val other = square(100, 100, 40)
        assertEquals(2, Silhouettes.dedupe(listOf(a, b, other)).size)
    }
}
