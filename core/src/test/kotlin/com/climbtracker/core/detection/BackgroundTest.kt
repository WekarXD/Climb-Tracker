package com.climbtracker.core.detection

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackgroundTest {

    private val side = 120

    private fun surroundings(image: PixelImage) = Background.surroundings(Filters.split(image), 3, 12, side, side)

    private fun chroma(lab: LabImage, x: Int, y: Int) = hypot(lab.a[y * side + x], lab.b[y * side + x])

    @Test
    fun plainWallIsItsOwnSurroundings() {
        val lab = surroundings(TestImages.solid(side, side, TestImages.BEIGE))
        val beige = ColorSpace.toLab(TestImages.BEIGE)
        for (i in intArrayOf(0, 60 * side + 60, side * side - 1)) {
            assertEquals(beige.l, lab.l[i], 1f)
            assertEquals(beige.a, lab.a[i], 1f)
            assertEquals(beige.b, lab.b[i], 1f)
        }
    }

    @Test
    fun largeHoldDoesNotColourItsSurroundings() {
        // 40 px across: a median of radius 12 on the image itself would be red at its centre.
        val image = TestImages.solid(side, side, TestImages.BEIGE)
        TestImages.rect(image, 40, 40, 40, 40, TestImages.RED)
        val lab = surroundings(image)
        val beige = ColorSpace.toLab(TestImages.BEIGE)
        assertEquals(beige.l, lab.l[60 * side + 60], 1f)
        assertTrue(chroma(lab, 60, 60) < 12f, "chroma ${chroma(lab, 60, 60)}")
    }

    @Test
    fun matWallAndHoldDoNotMixIntoAColour() {
        // Dark below, pale above and a red hold between them: the middle red, green and blue
        // of such a window are those of a red.
        val image = TestImages.solid(side, side, TestImages.BEIGE)
        TestImages.rect(image, 0, 66, side, 54, TestImages.DARK)
        TestImages.rect(image, 30, 36, 60, 30, TestImages.RED)
        val lab = surroundings(image)
        for (y in 30..90 step 10) assertTrue(chroma(lab, 60, y) < 12f, "chroma ${chroma(lab, 60, y)} at $y")
    }

    @Test
    fun panelsOfDifferentLightnessAreKept() {
        val image = TestImages.solid(side, side, TestImages.BEIGE)
        TestImages.rect(image, 0, 0, 60, side, TestImages.DARK)
        val lab = surroundings(image)
        assertEquals(ColorSpace.toLab(TestImages.DARK).l, lab.l[60 * side + 10], 1f)
        assertEquals(ColorSpace.toLab(TestImages.BEIGE).l, lab.l[60 * side + 110], 1f)
    }
}
