package com.climbtracker.core.detection

import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Lab(val l: Float, val a: Float, val b: Float) {
    fun distance(o: Lab): Float {
        val dl = l - o.l
        val da = a - o.a
        val db = b - o.b
        return sqrt(dl * dl + da * da + db * db)
    }

    /** Distance ignoring lightness. */
    fun chromaDistance(o: Lab): Float = hypot(a - o.a, b - o.b)
}

class LabImage(val width: Int, val height: Int, val l: FloatArray, val a: FloatArray, val b: FloatArray) {
    fun at(i: Int): Lab = Lab(l[i], a[i], b[i])
}

/** sRGB (D65) <-> CIE Lab. */
object ColorSpace {
    private const val XN = 0.95047f
    private const val ZN = 1.08883f
    private const val EPS = 0.008856f
    private const val KAPPA = 7.787f

    private val LINEAR = FloatArray(256) { c ->
        val v = c / 255f
        if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    fun toLab(argb: Int): Lab = toLab((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    fun toLab(r: Int, g: Int, b: Int): Lab {
        val rl = LINEAR[r]
        val gl = LINEAR[g]
        val bl = LINEAR[b]
        val fx = f((0.4124564f * rl + 0.3575761f * gl + 0.1804375f * bl) / XN)
        val fy = f(0.2126729f * rl + 0.7151522f * gl + 0.0721750f * bl)
        val fz = f((0.0193339f * rl + 0.1191920f * gl + 0.9503041f * bl) / ZN)
        return Lab(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
    }

    fun toArgb(lab: Lab): Int {
        val fy = (lab.l + 16f) / 116f
        val x = XN * fInv(fy + lab.a / 500f)
        val y = fInv(fy)
        val z = ZN * fInv(fy - lab.b / 200f)
        val r = gamma(3.2404542f * x - 1.5371385f * y - 0.4985314f * z)
        val g = gamma(-0.9692660f * x + 1.8760108f * y + 0.0415560f * z)
        val b = gamma(0.0556434f * x - 0.2040259f * y + 1.0572252f * z)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun f(t: Float): Float = if (t > EPS) cbrt(t) else KAPPA * t + 16f / 116f

    private fun fInv(t: Float): Float {
        val cube = t * t * t
        return if (cube > EPS) cube else (t - 16f / 116f) / KAPPA
    }

    private fun gamma(linear: Float): Int {
        val v = if (linear <= 0.0031308f) 12.92f * linear else 1.055f * linear.pow(1f / 2.4f) - 0.055f
        return (v * 255f).roundToInt().coerceIn(0, 255)
    }
}
