package com.climbtracker.core.detection

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class KMeansResult(val labels: IntArray, val centers: List<FloatArray>)

/** K-means over 3-component samples, followed by merging of clusters whose centres are close. */
object KMeans {

    fun cluster(
        samples: FloatArray,
        k: Int,
        mergeDistance: Float,
        seed: Long = 42L,
        maxIterations: Int = 30,
        maxFit: Int = 20_000,
    ): KMeansResult {
        val n = samples.size / 3
        if (n == 0 || k <= 0) return KMeansResult(IntArray(0), emptyList())

        // Fit on an evenly spaced subset to bound the cost on large masks.
        val step = max(1, n / maxFit)
        val fit = IntArray((n + step - 1) / step) { it * step }
        val kk = min(k, fit.size)
        val centers = initialCenters(samples, fit, kk, Random(seed))

        val assign = IntArray(fit.size) { -1 }
        for (iteration in 0 until maxIterations) {
            var changed = false
            for (j in fit.indices) {
                val c = nearest(samples, fit[j], centers)
                if (c != assign[j]) {
                    assign[j] = c
                    changed = true
                }
            }
            if (!changed) break
            val sums = Array(kk) { FloatArray(3) }
            val counts = IntArray(kk)
            for (j in fit.indices) {
                val c = assign[j]
                val o = fit[j] * 3
                sums[c][0] += samples[o]
                sums[c][1] += samples[o + 1]
                sums[c][2] += samples[o + 2]
                counts[c]++
            }
            for (c in 0 until kk) {
                if (counts[c] == 0) continue
                for (d in 0..2) centers[c][d] = sums[c][d] / counts[c]
            }
        }

        // Merge each centre into the first earlier, unmerged centre that is close enough.
        val remap = IntArray(kk) { it }
        for (i in 0 until kk) {
            for (j in 0 until i) {
                if (remap[j] == j && distance(centers[i], centers[j]) < mergeDistance) {
                    remap[i] = j
                    break
                }
            }
        }
        val compact = IntArray(kk) { -1 }
        val kept = ArrayList<FloatArray>()
        for (i in 0 until kk) {
            if (remap[i] == i) {
                compact[i] = kept.size
                kept += centers[i]
            }
        }
        val labels = IntArray(n) { compact[remap[nearest(samples, it, centers)]] }
        return KMeansResult(labels, kept)
    }

    /** k-means++ seeding. */
    private fun initialCenters(samples: FloatArray, fit: IntArray, k: Int, random: Random): Array<FloatArray> {
        val centers = Array(k) { FloatArray(3) }
        copy(samples, fit[random.nextInt(fit.size)], centers[0])
        val nearestSq = FloatArray(fit.size) { Float.MAX_VALUE }
        for (c in 1 until k) {
            var total = 0.0
            for (j in fit.indices) {
                val d = distanceSq(samples, fit[j], centers[c - 1])
                if (d < nearestSq[j]) nearestSq[j] = d
                total += nearestSq[j]
            }
            var pick = fit.size - 1
            if (total > 0.0) {
                var r = random.nextDouble() * total
                for (j in fit.indices) {
                    r -= nearestSq[j]
                    if (r <= 0.0) {
                        pick = j
                        break
                    }
                }
            } else {
                pick = random.nextInt(fit.size)
            }
            copy(samples, fit[pick], centers[c])
        }
        return centers
    }

    private fun copy(samples: FloatArray, index: Int, into: FloatArray) {
        val o = index * 3
        into[0] = samples[o]
        into[1] = samples[o + 1]
        into[2] = samples[o + 2]
    }

    private fun nearest(samples: FloatArray, index: Int, centers: Array<FloatArray>): Int {
        var best = 0
        var bestD = Float.MAX_VALUE
        for (c in centers.indices) {
            val d = distanceSq(samples, index, centers[c])
            if (d < bestD) {
                bestD = d
                best = c
            }
        }
        return best
    }

    private fun distanceSq(samples: FloatArray, index: Int, center: FloatArray): Float {
        val o = index * 3
        val d0 = samples[o] - center[0]
        val d1 = samples[o + 1] - center[1]
        val d2 = samples[o + 2] - center[2]
        return d0 * d0 + d1 * d1 + d2 * d2
    }

    private fun distance(a: FloatArray, b: FloatArray): Float {
        val d0 = a[0] - b[0]
        val d1 = a[1] - b[1]
        val d2 = a[2] - b[2]
        return sqrt(d0 * d0 + d1 * d1 + d2 * d2)
    }
}
