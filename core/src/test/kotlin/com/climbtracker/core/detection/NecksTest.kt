package com.climbtracker.core.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NecksTest {

    private val width = 120
    private val height = 80

    private fun region(inside: (Int, Int) -> Boolean): Component {
        val mask = BooleanArray(width * height) { inside(it % width, it / width) }
        return Components.find(mask, width, height).single()
    }

    private fun disc(cx: Int, cy: Int, r: Int) = { x: Int, y: Int -> (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r }

    private fun parts(c: Component) = Necks.split(c, width, minRadius = 3f)

    @Test
    fun aRoundHoldIsLeftWhole() {
        val c = region(disc(60, 40, 20))
        assertEquals(listOf(c), parts(c))
    }

    @Test
    fun aLongHoldIsLeftWhole() {
        val c = region { x, y -> x in 10..110 && y in 30..50 }
        assertEquals(listOf(c), parts(c))
    }

    @Test
    fun aBentHoldAsThickAllAlongIsLeftWhole() {
        val c = region { x, y -> (x in 20..40 && y in 10..70) || (x in 20..90 && y in 50..70) }
        assertEquals(listOf(c), parts(c))
    }

    @Test
    fun twoHoldsThatTouchAreToldApart() {
        val left = disc(40, 40, 18)
        val right = disc(74, 40, 18)
        val c = region { x, y -> left(x, y) || right(x, y) }
        val found = parts(c)
        assertEquals(2, found.size)
        assertEquals(c.area, found.sumOf { it.area })
        // Each takes about half, and they meet near the middle.
        assertTrue(found.all { it.area > c.area * 0.4f })
        val (a, b) = found.sortedBy { it.minX }
        assertTrue(a.maxX in 54..60 && b.minX in 54..60, "met at ${a.maxX} and ${b.minX}")
    }

    @Test
    fun aSmallHoldAgainstALargeOneIsToldApart() {
        val large = disc(45, 40, 28)
        val small = disc(85, 40, 12)
        val c = region { x, y -> large(x, y) || small(x, y) }
        assertEquals(2, parts(c).size)
    }

    @Test
    fun aBumpOnAHoldIsNotAHoldOfItsOwn() {
        val body = disc(50, 40, 28)
        val bump = disc(80, 40, 5)
        val c = region { x, y -> body(x, y) || bump(x, y) }
        assertEquals(listOf(c), parts(c))
    }

    @Test
    fun threeHoldsInARowAreToldApart() {
        val c = region { x, y -> disc(22, 40, 14)(x, y) || disc(48, 40, 14)(x, y) || disc(74, 40, 14)(x, y) }
        assertEquals(3, parts(c).size)
    }
}
