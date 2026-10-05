package com.climbtracker.core.detection

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FiltersTest {

    @Test
    fun splitExtractsChannels() {
        val p = Filters.split(PixelImage(2, 1, intArrayOf(0xFF102030.toInt(), 0xFFA0B0C0.toInt())))
        assertContentEquals(intArrayOf(0x10, 0xA0), p.r)
        assertContentEquals(intArrayOf(0x20, 0xB0), p.g)
        assertContentEquals(intArrayOf(0x30, 0xC0), p.b)
    }

    @Test
    fun medianRemovesIsolatedPixel() {
        val src = IntArray(25) { 10 }
        src[12] = 200
        assertContentEquals(IntArray(25) { 10 }, Filters.median(src, 5, 5, 1))
    }

    @Test
    fun medianKeepsConstantImage() {
        val src = IntArray(12) { 77 }
        assertContentEquals(src, Filters.median(src, 4, 3, 2))
    }

    @Test
    fun medianPreservesStepEdge() {
        val src = IntArray(6 * 4) { if (it % 6 < 3) 0 else 100 }
        assertContentEquals(src, Filters.median(src, 6, 4, 1))
    }

    @Test
    fun downscaleAveragesBlocks() {
        val v = intArrayOf(
            0, 4, 10, 10,
            8, 12, 10, 10,
            100, 100, 0, 0,
            100, 100, 0, 40,
        )
        val p = Filters.downscale(Planes(4, 4, v, v.copyOf(), v.copyOf()), 2)
        assertEquals(2, p.width)
        assertEquals(2, p.height)
        assertContentEquals(intArrayOf(6, 10, 100, 10), p.r)
    }

    @Test
    fun downscaleNeverProducesEmptyImage() {
        val p = Filters.downscale(Planes(3, 2, IntArray(6) { 50 }, IntArray(6) { 50 }, IntArray(6) { 50 }), 4)
        assertEquals(1, p.width)
        assertEquals(1, p.height)
        assertContentEquals(intArrayOf(50), p.g)
    }

    @Test
    fun upscaleRepeatsPixels() {
        val p = Filters.upscale(Planes(2, 1, intArrayOf(1, 2), intArrayOf(3, 4), intArrayOf(5, 6)), 4, 2)
        assertContentEquals(intArrayOf(1, 1, 2, 2, 1, 1, 2, 2), p.r)
        assertContentEquals(intArrayOf(5, 5, 6, 6, 5, 5, 6, 6), p.b)
    }

    @Test
    fun toLabConvertsEveryPixel() {
        val lab = Filters.toLab(Planes(2, 1, intArrayOf(255, 0), intArrayOf(255, 0), intArrayOf(255, 0)))
        assertEquals(100f, lab.l[0], 0.5f)
        assertEquals(0f, lab.l[1], 0.5f)
    }
}
