package com.climbtracker.core.detection

import kotlin.math.max
import kotlin.math.min

/** Separate 0..255 colour channels. */
class Planes(val width: Int, val height: Int, val r: IntArray, val g: IntArray, val b: IntArray)

object Filters {

    fun split(image: PixelImage): Planes {
        val n = image.argb.size
        val r = IntArray(n)
        val g = IntArray(n)
        val b = IntArray(n)
        for (i in 0 until n) {
            val c = image.argb[i]
            r[i] = (c shr 16) and 0xFF
            g[i] = (c shr 8) and 0xFF
            b[i] = c and 0xFF
        }
        return Planes(image.width, image.height, r, g, b)
    }

    /** Median over a (2*radius+1)^2 window, clipped at the image border. Values must be 0..255. */
    fun median(src: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val out = IntArray(src.size)
        val hist = IntArray(256)
        for (y in 0 until height) {
            val y0 = max(0, y - radius)
            val y1 = min(height - 1, y + radius)
            for (x in 0 until width) {
                val x0 = max(0, x - radius)
                val x1 = min(width - 1, x + radius)
                hist.fill(0)
                for (yy in y0..y1) {
                    var i = yy * width + x0
                    for (xx in x0..x1) {
                        hist[src[i]]++
                        i++
                    }
                }
                val target = (y1 - y0 + 1) * (x1 - x0 + 1) / 2
                var acc = 0
                var v = 0
                while (true) {
                    acc += hist[v]
                    if (acc > target) break
                    v++
                }
                out[y * width + x] = v
            }
        }
        return out
    }

    fun median(p: Planes, radius: Int): Planes = Planes(
        p.width, p.height,
        median(p.r, p.width, p.height, radius),
        median(p.g, p.width, p.height, radius),
        median(p.b, p.width, p.height, radius),
    )

    /** Box-average reduction by an integer [factor]; the result is at least 1x1. */
    fun downscale(p: Planes, factor: Int): Planes {
        val w = max(1, p.width / factor)
        val h = max(1, p.height / factor)
        fun reduce(src: IntArray): IntArray {
            val out = IntArray(w * h)
            for (y in 0 until h) {
                val y0 = y * factor
                val y1 = min(p.height, y0 + factor)
                for (x in 0 until w) {
                    val x0 = x * factor
                    val x1 = min(p.width, x0 + factor)
                    var sum = 0
                    for (yy in y0 until y1) for (xx in x0 until x1) sum += src[yy * p.width + xx]
                    out[y * w + x] = sum / ((y1 - y0) * (x1 - x0))
                }
            }
            return out
        }
        return Planes(w, h, reduce(p.r), reduce(p.g), reduce(p.b))
    }

    /** Nearest-neighbour enlargement to exactly [width] x [height]. */
    fun upscale(p: Planes, width: Int, height: Int): Planes {
        fun enlarge(src: IntArray): IntArray {
            val out = IntArray(width * height)
            for (y in 0 until height) {
                val sy = min(p.height - 1, y * p.height / height)
                for (x in 0 until width) {
                    val sx = min(p.width - 1, x * p.width / width)
                    out[y * width + x] = src[sy * p.width + sx]
                }
            }
            return out
        }
        return Planes(width, height, enlarge(p.r), enlarge(p.g), enlarge(p.b))
    }

    fun toLab(p: Planes): LabImage {
        val n = p.width * p.height
        val l = FloatArray(n)
        val a = FloatArray(n)
        val b = FloatArray(n)
        for (i in 0 until n) {
            val lab = ColorSpace.toLab(p.r[i], p.g[i], p.b[i])
            l[i] = lab.l
            a[i] = lab.a
            b[i] = lab.b
        }
        return LabImage(p.width, p.height, l, a, b)
    }
}
