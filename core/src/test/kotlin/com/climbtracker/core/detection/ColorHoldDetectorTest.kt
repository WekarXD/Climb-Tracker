package com.climbtracker.core.detection

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ColorHoldDetectorTest {

    private val detector = ColorHoldDetector()
    private val w = 240
    private val h = 180

    private fun DetectionResult.near(px: Int, py: Int): DetectedHold? =
        holds.firstOrNull { abs(it.center.x - px / w.toFloat()) < 0.02f && abs(it.center.y - py / h.toFloat()) < 0.02f }

    private fun wallWithSixHolds(): PixelImage {
        val img = TestImages.solid(w, h, TestImages.BEIGE)
        TestImages.rect(img, 20, 20, 16, 16, TestImages.RED)
        TestImages.rect(img, 100, 40, 16, 16, TestImages.RED)
        TestImages.rect(img, 180, 120, 16, 16, TestImages.RED)
        TestImages.rect(img, 60, 100, 16, 16, TestImages.BLUE)
        TestImages.rect(img, 140, 20, 16, 16, TestImages.BLUE)
        TestImages.rect(img, 200, 60, 16, 16, TestImages.BLACK)
        return img
    }

    @Test
    fun detectsColouredHoldsOnPlainWall() {
        val r = detector.detect(wallWithSixHolds(), 0.5f)
        assertEquals(6, r.holds.size)
        assertEquals(3, r.groups.size)
        assertEquals(listOf(1, 2, 3), r.groups.map { it.holdCount }.sorted())
    }

    @Test
    fun holdsOfSameColourShareGroup() {
        val r = detector.detect(wallWithSixHolds(), 0.5f)
        val reds = listOf(r.near(28, 28), r.near(108, 48), r.near(188, 128))
        reds.forEach { assertNotNull(it) }
        assertEquals(1, reds.map { it!!.colorGroup }.toSet().size)
        val blue = assertNotNull(r.near(68, 108))
        assertTrue(blue.colorGroup != reds[0]!!.colorGroup)
    }

    @Test
    fun holdGeometryIsNormalised() {
        val r = detector.detect(wallWithSixHolds(), 0.5f)
        val hold = assertNotNull(r.near(28, 28))
        assertTrue(hold.contour.size >= 3)
        assertTrue(hold.contour.all { it.x in 0f..1f && it.y in 0f..1f })
        assertEquals(20f / w, hold.bounds.left, 0.01f)
        assertEquals(36f / h, hold.bounds.bottom, 0.01f)
        assertTrue(hold.area in 200..256)
    }

    @Test
    fun holdColourIsItsMeanColour() {
        val r = detector.detect(wallWithSixHolds(), 0.5f)
        val hold = assertNotNull(r.near(28, 28))
        assertTrue(ColorSpace.toLab(hold.argb).distance(ColorSpace.toLab(TestImages.RED)) < 3f)
    }

    @Test
    fun groupIndicesAreCompactAndMatchHolds() {
        val r = detector.detect(wallWithSixHolds(), 0.5f)
        assertEquals(listOf(0, 1, 2), r.groups.map { it.index })
        assertTrue(r.holds.all { it.colorGroup in 0..2 })
    }

    @Test
    fun handlesTwoBackgroundPanels() {
        val img = TestImages.solid(w, h, TestImages.BEIGE)
        TestImages.rect(img, 120, 0, 120, h, TestImages.DARK)
        TestImages.rect(img, 40, 80, 16, 16, TestImages.RED)
        TestImages.rect(img, 170, 80, 16, 16, TestImages.YELLOW)
        val r = detector.detect(img, 0.5f)
        assertEquals(2, r.holds.size)
        assertNotNull(r.near(48, 88))
        assertNotNull(r.near(178, 88))
    }

    @Test
    fun uniformImageHasNoHolds() {
        val r = detector.detect(TestImages.solid(w, h, TestImages.BEIGE), 0.5f)
        assertEquals(0, r.holds.size)
        assertEquals(0, r.groups.size)
    }

    @Test
    fun blackImageHasNoHolds() {
        val r = detector.detect(TestImages.solid(w, h, 0xFF000000.toInt()), 0.5f)
        assertEquals(0, r.holds.size)
    }

    @Test
    fun emptyImageHasNoHolds() {
        val r = detector.detect(PixelImage(0, 0, IntArray(0)), 0.5f)
        assertEquals(0, r.holds.size)
    }

    @Test
    fun detectsHoldTouchingImageCorner() {
        val img = TestImages.solid(w, h, TestImages.BEIGE)
        TestImages.rect(img, 0, 0, 16, 16, TestImages.RED)
        val r = detector.detect(img, 0.5f)
        assertEquals(1, r.holds.size)
        assertTrue(r.holds[0].contour.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test
    fun ignoresSpecksBelowMinimumArea() {
        val img = TestImages.solid(w, h, TestImages.BEIGE)
        TestImages.rect(img, 50, 50, 3, 3, TestImages.BLACK)
        assertEquals(0, detector.detect(img, 0.5f).holds.size)
    }

    @Test
    fun sensitivityRevealsFaintHolds() {
        val grey = 0xFF808080.toInt()
        val base = ColorSpace.toLab(grey)
        val faint = ColorSpace.toArgb(Lab(base.l, base.a + 8f, base.b))
        val img = TestImages.solid(w, h, grey)
        TestImages.rect(img, 100, 80, 20, 20, faint)
        assertEquals(0, detector.detect(img, 0.5f).holds.size)
        assertEquals(1, detector.detect(img, 1.0f).holds.size)
    }

    @Test
    fun facetsOfOneVolumeBecomeOneHold() {
        // Two faces of a grey volume, one in shade: same material, different lightness.
        val img = TestImages.solid(w, h, TestImages.BEIGE)
        TestImages.rect(img, 100, 60, 18, 30, 0xFF2C2C2C.toInt())
        TestImages.rect(img, 118, 60, 18, 30, 0xFF6E6E6E.toInt())
        val r = detector.detect(img, 0.5f)
        assertEquals(1, r.holds.size)
        assertTrue(r.holds[0].area > 900)
    }

    @Test
    fun litAndShadedSidesOfAColouredHoldBecomeOneHold() {
        val img = TestImages.solid(w, h, TestImages.BEIGE)
        TestImages.rect(img, 100, 60, 16, 24, 0xFFF0CC30.toInt())
        TestImages.rect(img, 116, 60, 16, 24, 0xFF9C8010.toInt())
        assertEquals(1, detector.detect(img, 0.5f).holds.size)
    }

    @Test
    fun touchingHoldsOfDifferentColourStaySeparate() {
        val img = TestImages.solid(w, h, TestImages.BEIGE)
        TestImages.rect(img, 100, 60, 18, 24, TestImages.RED)
        TestImages.rect(img, 118, 60, 18, 24, TestImages.BLUE)
        val r = detector.detect(img, 0.5f)
        assertEquals(2, r.holds.size)
        assertEquals(2, r.groups.size)
    }

    @Test
    fun greyHoldOnLightWallIsDetected() {
        // Measured on real photos: grey holds differ from the wall by about 30 in lightness only.
        val wall = 0xFFD6CCBB.toInt()
        val base = ColorSpace.toLab(wall)
        val grey = ColorSpace.toArgb(Lab(base.l - 31f, base.a, base.b - 3f))
        val img = TestImages.solid(w, h, wall)
        TestImages.rect(img, 100, 80, 20, 20, grey)
        assertEquals(1, detector.detect(img, 0.5f).holds.size)
    }

    @Test
    fun softShadowOnLightWallIsNotAHold() {
        val wall = 0xFFD6CCBB.toInt()
        val base = ColorSpace.toLab(wall)
        val shadow = ColorSpace.toArgb(Lab(base.l - 15f, base.a, base.b))
        val img = TestImages.solid(w, h, wall)
        TestImages.rect(img, 100, 80, 30, 14, shadow)
        assertEquals(0, detector.detect(img, 0.5f).holds.size)
    }

    @Test
    fun chalkOnDarkPanelIsNotAHold() {
        val panel = 0xFF323232.toInt()
        val base = ColorSpace.toLab(panel)
        val chalk = ColorSpace.toArgb(Lab(base.l + 25f, base.a, base.b))
        val img = TestImages.solid(w, h, panel)
        TestImages.rect(img, 100, 80, 30, 20, chalk)
        assertEquals(0, detector.detect(img, 0.5f).holds.size)
    }

    @Test
    fun darkColouredHoldOnDarkPanelIsDetected() {
        // Measured on real photos: a blue hold on the black panel differs by about 16 in colour.
        val panel = 0xFF323232.toInt()
        val base = ColorSpace.toLab(panel)
        val blue = ColorSpace.toArgb(Lab(base.l - 10f, base.a - 3f, base.b - 16f))
        val img = TestImages.solid(w, h, panel)
        TestImages.rect(img, 100, 80, 20, 20, blue)
        assertEquals(1, detector.detect(img, 0.5f).holds.size)
    }

    @Test
    fun colourlessHoldsThatBarelyTouchStaySeparate() {
        // A black hold and a grey hold meeting along a few pixels are two holds, unlike the two
        // faces of a volume, which share a whole edge.
        val img = TestImages.solid(w, h, TestImages.BEIGE)
        TestImages.rect(img, 100, 60, 20, 20, TestImages.BLACK)
        TestImages.rect(img, 120, 76, 20, 20, 0xFF707070.toInt())
        assertEquals(2, detector.detect(img, 0.5f).holds.size)
    }

    @Test
    fun thinStripIsNotAHold() {
        // The sliver left where two wall panels meet: long, a few pixels wide.
        val img = TestImages.solid(w, h, TestImages.BEIGE)
        TestImages.rect(img, 40, 90, 150, 4, TestImages.BLACK)
        assertEquals(0, detector.detect(img, 0.5f).holds.size)
    }

    @Test
    fun latticeIsNotAHold() {
        // A ventilation grille: bars with holes between them.
        val img = TestImages.solid(w, h, TestImages.BEIGE)
        for (x in 60 until 140 step 8) TestImages.rect(img, x, 60, 3, 40, TestImages.BLACK)
        for (y in 60 until 100 step 8) TestImages.rect(img, 60, y, 80, 3, TestImages.BLACK)
        assertEquals(0, detector.detect(img, 0.5f).holds.size)
    }

    @Test
    fun elongatedHoldIsStillDetected() {
        // A long rail, four times longer than wide, is a legitimate hold.
        val img = TestImages.solid(w, h, TestImages.BEIGE)
        TestImages.rect(img, 80, 80, 64, 16, TestImages.RED)
        assertEquals(1, detector.detect(img, 0.5f).holds.size)
    }

    @Test
    fun boltHoleInsideAHoldIsPartOfIt() {
        val img = TestImages.solid(w, h, TestImages.BEIGE)
        TestImages.rect(img, 100, 60, 40, 40, TestImages.YELLOW)
        TestImages.rect(img, 114, 74, 10, 10, TestImages.BLACK)
        val r = detector.detect(img, 0.5f)
        assertEquals(1, r.holds.size)
        assertTrue(r.holds[0].area > 1400)
    }

    @Test
    fun colouredHoldMountedOnAVolumeStaysSeparate() {
        val img = TestImages.solid(w, h, TestImages.BEIGE)
        TestImages.rect(img, 100, 60, 50, 50, 0xFF2C2C2C.toInt())
        TestImages.rect(img, 116, 76, 14, 14, TestImages.YELLOW)
        assertEquals(2, detector.detect(img, 0.5f).holds.size)
    }

    @Test
    fun lightAndDarkShadesOfAColourShareOnePaletteGroup() {
        // The same turquoise holds look lighter on a pale wall than in the shade of a dark panel.
        val img = TestImages.solid(w, h, TestImages.BEIGE)
        TestImages.rect(img, 40, 60, 20, 20, 0xFF7FD6CC.toInt())
        TestImages.rect(img, 100, 60, 20, 20, 0xFF3E8F86.toInt())
        TestImages.rect(img, 160, 60, 20, 20, TestImages.RED)
        val r = detector.detect(img, 0.5f)
        assertEquals(3, r.holds.size)
        assertEquals(2, r.groups.size)
        assertEquals(r.near(50, 70)!!.colorGroup, r.near(110, 70)!!.colorGroup)
        assertTrue(r.near(170, 70)!!.colorGroup != r.near(50, 70)!!.colorGroup)
    }

    @Test
    fun blackGreyAndWhiteStayInSeparatePaletteGroups() {
        val img = TestImages.solid(w, h, 0xFFB09880.toInt())
        TestImages.rect(img, 40, 60, 20, 20, TestImages.BLACK)
        TestImages.rect(img, 100, 60, 20, 20, 0xFFF4F4F4.toInt())
        val r = detector.detect(img, 0.5f)
        assertEquals(2, r.holds.size)
        assertEquals(2, r.groups.size)
    }
}
