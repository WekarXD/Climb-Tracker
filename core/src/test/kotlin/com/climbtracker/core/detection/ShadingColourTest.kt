package com.climbtracker.core.detection

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShadingColourTest {

    private val yellow = floatArrayOf(75f, 5f, 55f)
    private val ochre = floatArrayOf(50f, 12f, 38f)
    private val beige = floatArrayOf(80f, 2f, 12f)
    private val grey = floatArrayOf(35f, 0f, 1f)
    private val red = floatArrayOf(42f, 40f, 24f)

    @Test
    fun aDarkerPieceOfTheSameHueIsTheSameColour() {
        assertTrue(Shading.sameColour(yellow, ochre))
    }

    @Test
    fun aPaleTintOfTheSameHueIsNotTheSameColour() {
        assertFalse(Shading.sameColour(yellow, beige))
    }

    @Test
    fun twoFaintTintsAreTheSameGrey() {
        assertTrue(Shading.sameColour(beige, grey))
        assertTrue(Shading.sameColour(floatArrayOf(60f, -10f, 12f), floatArrayOf(40f, 8f, -6f)))
    }

    @Test
    fun differentHuesAreDifferentColours() {
        assertFalse(Shading.sameColour(yellow, red))
    }

    /** A row of pixels, all foreground, with the given colours and clusters. */
    private fun spread(colours: List<FloatArray>, labels: IntArray, centers: List<FloatArray>, foreground: BooleanArray? = null): IntArray {
        val n = colours.size
        val lab = LabImage(n, 1, FloatArray(n) { colours[it][0] }, FloatArray(n) { colours[it][1] }, FloatArray(n) { colours[it][2] })
        val mask = foreground ?: BooleanArray(n) { true }
        val index = (0 until n).filter { mask[it] }.toIntArray()
        val own = index.map { labels[it] }.toIntArray()
        Shading.spreadColour(own, index, centers, lab, mask, n, 1)
        return own
    }

    @Test
    fun aColouredHoldTakesInItsDullerPixels() {
        val dull = floatArrayOf(60f, 3f, 24f)
        val result = spread(listOf(yellow, dull, dull, grey, grey), intArrayOf(0, 1, 1, 1, 1), listOf(yellow, grey))
        assertContentEquals(intArrayOf(0, 0, 0, 1, 1), result)
    }

    @Test
    fun aPixelWithADefiniteColourJoinsTheClusterOfItsHueOnItsOwn() {
        // An ochre hold in the shade: no pixel of it is near enough to the vivid yellow.
        val result = spread(listOf(grey, ochre, ochre, grey), intArrayOf(1, 1, 1, 1), listOf(yellow, grey))
        assertContentEquals(intArrayOf(1, 0, 0, 1), result)
    }

    @Test
    fun itDoesNotTakePixelsOfAnotherHueOrWithHardlyAnyColour() {
        val bluish = floatArrayOf(60f, -5f, -25f)
        val faint = floatArrayOf(60f, 1f, 6f)
        assertContentEquals(intArrayOf(0, 1, 1), spread(listOf(yellow, bluish, bluish), intArrayOf(0, 1, 1), listOf(yellow, grey)))
        assertContentEquals(intArrayOf(0, 1, 1), spread(listOf(yellow, faint, faint), intArrayOf(0, 1, 1), listOf(yellow, grey)))
    }

    @Test
    fun itDoesNotTakePixelsFromAnotherColouredHold() {
        val dull = floatArrayOf(60f, 3f, 24f)
        assertContentEquals(intArrayOf(0, 1, 1), spread(listOf(yellow, dull, dull), intArrayOf(0, 1, 1), listOf(yellow, red)))
    }
}
