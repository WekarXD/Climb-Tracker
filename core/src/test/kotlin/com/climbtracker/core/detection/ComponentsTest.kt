package com.climbtracker.core.detection

import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentsTest {

    private fun mask(width: Int, vararg rows: String): BooleanArray {
        val out = BooleanArray(width * rows.size)
        rows.forEachIndexed { y, row -> row.forEachIndexed { x, ch -> out[y * width + x] = ch == '#' } }
        return out
    }

    @Test
    fun findsSeparateBlocks() {
        val m = mask(
            7,
            "##.....",
            "##.....",
            ".....##",
            ".....##",
        )
        val c = Components.find(m, 7, 4)
        assertEquals(2, c.size)
        assertEquals(4, c[0].area)
        assertEquals(listOf(0, 0, 1, 1), listOf(c[0].minX, c[0].minY, c[0].maxX, c[0].maxY))
        assertEquals(listOf(5, 2, 6, 3), listOf(c[1].minX, c[1].minY, c[1].maxX, c[1].maxY))
    }

    @Test
    fun diagonalPixelsAreConnected() {
        val m = mask(
            3,
            "#..",
            ".#.",
            "..#",
        )
        val c = Components.find(m, 3, 3)
        assertEquals(1, c.size)
        assertEquals(3, c[0].area)
    }

    @Test
    fun firstPixelIsRasterFirst() {
        val m = mask(
            4,
            "....",
            "..#.",
            ".##.",
        )
        assertEquals(1 * 4 + 2, Components.find(m, 4, 3)[0].pixels[0])
    }

    @Test
    fun emptyMaskHasNoComponents() {
        assertEquals(0, Components.find(BooleanArray(12), 4, 3).size)
    }
}
