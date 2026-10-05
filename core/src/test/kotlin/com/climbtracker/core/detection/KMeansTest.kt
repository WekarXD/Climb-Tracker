package com.climbtracker.core.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KMeansTest {

    private fun blobs(): FloatArray {
        val centers = listOf(floatArrayOf(50f, 60f, 40f), floatArrayOf(30f, -10f, -50f), floatArrayOf(90f, 0f, 80f))
        val out = FloatArray(300 * 3)
        for (i in 0 until 300) {
            val c = centers[i % 3]
            val jitter = (i / 3 % 3 - 1).toFloat()
            out[i * 3] = c[0] + jitter
            out[i * 3 + 1] = c[1] - jitter
            out[i * 3 + 2] = c[2] + jitter
        }
        return out
    }

    @Test
    fun threeSeparatedBlobsBecomeThreeGroups() {
        val r = KMeans.cluster(blobs(), 8, 16f)
        assertEquals(3, r.centers.size)
        assertEquals(300, r.labels.size)
        for (i in 3 until 300) assertEquals(r.labels[i % 3], r.labels[i], "sample $i")
        assertEquals(3, setOf(r.labels[0], r.labels[1], r.labels[2]).size)
    }

    @Test
    fun labelsAreCompactIndices() {
        val r = KMeans.cluster(blobs(), 8, 16f)
        assertTrue(r.labels.all { it in r.centers.indices })
    }

    @Test
    fun identicalSamplesBecomeOneGroup() {
        val r = KMeans.cluster(FloatArray(60) { 5f }, 8, 16f)
        assertEquals(1, r.centers.size)
        assertTrue(r.labels.all { it == 0 })
    }

    @Test
    fun emptyInputGivesEmptyResult() {
        val r = KMeans.cluster(FloatArray(0), 8, 16f)
        assertEquals(0, r.labels.size)
        assertEquals(0, r.centers.size)
    }

    @Test
    fun fewerSamplesThanClusters() {
        val r = KMeans.cluster(floatArrayOf(0f, 0f, 0f, 100f, 50f, 50f), 8, 16f)
        assertEquals(2, r.centers.size)
        assertTrue(r.labels[0] != r.labels[1])
    }

    @Test
    fun sameInputGivesSameResult() {
        val a = KMeans.cluster(blobs(), 8, 16f)
        val b = KMeans.cluster(blobs(), 8, 16f)
        assertTrue(a.labels.contentEquals(b.labels))
    }
}
