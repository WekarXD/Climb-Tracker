package com.climbtracker.core.detection

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RingTest {

    private val size = 20

    private fun image(fill: (Int, Int) -> Lab): LabImage {
        val n = size * size
        val l = FloatArray(n)
        val a = FloatArray(n)
        val b = FloatArray(n)
        for (y in 0 until size) for (x in 0 until size) {
            val c = fill(x, y)
            l[y * size + x] = c.l
            a[y * size + x] = c.a
            b[y * size + x] = c.b
        }
        return LabImage(size, size, l, a, b)
    }

    private fun centreBlock(): Component {
        val px = ArrayList<Int>()
        for (y in 8..11) for (x in 8..11) px += y * size + x
        return Component(px.toIntArray(), 8, 8, 11, 11)
    }

    @Test
    fun blockOfSameColourAsSurroundingsContinues() {
        val lab = image { _, _ -> Lab(70f, 2f, 8f) }
        assertTrue(Ring.continues(centreBlock(), lab, 14f, 0.25f))
    }

    @Test
    fun distinctBlockDoesNotContinue() {
        val lab = image { x, y -> if (x in 8..11 && y in 8..11) Lab(45f, 60f, 40f) else Lab(70f, 2f, 8f) }
        assertFalse(Ring.continues(centreBlock(), lab, 14f, 0.25f))
    }

    @Test
    fun blockOnPanelBoundaryContinuesIntoMatchingSide() {
        // Left of the block is the block's own colour; the rest is a dark panel.
        val lab = image { x, y -> if (x <= 11 && y in 6..13) Lab(70f, 2f, 8f) else Lab(20f, 0f, 0f) }
        assertTrue(Ring.continues(centreBlock(), lab, 14f, 0.25f))
    }

    @Test
    fun componentInImageCornerIsHandled() {
        val lab = image { x, y -> if (x < 3 && y < 3) Lab(45f, 60f, 40f) else Lab(70f, 2f, 8f) }
        val px = ArrayList<Int>()
        for (y in 0..2) for (x in 0..2) px += y * size + x
        assertFalse(Ring.continues(Component(px.toIntArray(), 0, 0, 2, 2), lab, 14f, 0.25f))
    }
}
