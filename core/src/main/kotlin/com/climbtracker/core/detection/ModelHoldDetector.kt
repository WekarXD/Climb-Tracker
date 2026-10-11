package com.climbtracker.core.detection

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Finds holds with a trained model. [model] says where the holds are, as boxes; [silhouettes]
 * draws the outline of each inside its box; the colour of a hold is that of its own pixels, and
 * the palette is built from those colours.
 *
 * [fallback] finds the holds when the model cannot run, and grows the holds added by hand.
 */
class ModelHoldDetector(
    private val model: HoldModelRunner,
    private val silhouettes: SilhouetteRunner,
    private val fallback: HoldDetector,
    private val log: (String) -> Unit = {},
) : HoldDetector {

    override fun detect(image: PixelImage, sensitivity: Float): DetectionResult {
        val boxes = try {
            model.find(image, sensitivity)
        } catch (e: Throwable) {
            log("Hold model unavailable, using the colour detector: $e")
            return withoutModel(image, sensitivity)
        }
        if (boxes.isEmpty()) return withoutModel(image, sensitivity)
        val drawn = boxes.map { rough(it, image.width, image.height) }
        val outlined = try {
            silhouettes.refine(image, drawn)
        } catch (e: Throwable) {
            log("Silhouettes unavailable, keeping the boxes of the model: $e")
            drawn
        }
        val lab = Filters.toLab(Filters.split(image))
        return Palette.group(outlined.map { it.copy(argb = HoldColour.of(lab, it)) })
    }

    override fun growRegion(image: PixelImage, x: Int, y: Int): DetectedHold? = fallback.growRegion(image, x, y)

    /** As the app worked before the model: holds and colours from [fallback], outlines redrawn. */
    private fun withoutModel(image: PixelImage, sensitivity: Float): DetectionResult {
        val found = fallback.detect(image, sensitivity)
        val holds = try {
            silhouettes.refine(image, found.holds)
        } catch (e: Throwable) {
            log("Silhouettes unavailable, keeping the detector's outlines: $e")
            return found
        }
        val groups = found.groups.map { group -> group.copy(holdCount = holds.count { it.colorGroup == group.index }) }
        return DetectionResult(holds, groups.filter { it.holdCount > 0 })
    }

    /** The hold of [box] before its outline is known: the ellipse that fills the box. */
    private fun rough(box: HoldBox, width: Int, height: Int): DetectedHold {
        val cx = (box.left + box.right) / 2
        val cy = (box.top + box.bottom) / 2
        val rx = (box.right - box.left) / 2
        val ry = (box.bottom - box.top) / 2
        val contour = List(ROUGH_POINTS) { i ->
            val angle = 2 * PI * i / ROUGH_POINTS
            PointF(
                ((cx + rx * cos(angle).toFloat()) / width).coerceIn(0f, 1f),
                ((cy + ry * sin(angle).toFloat()) / height).coerceIn(0f, 1f),
            )
        }
        return DetectedHold(
            contour = contour,
            bounds = Bounds(box.left / width, box.top / height, box.right / width, box.bottom / height),
            center = PointF(cx / width, cy / height),
            argb = 0,
            colorGroup = 0,
            area = max(1, (PI * rx * ry).roundToInt()),
        )
    }

    private companion object {
        const val ROUGH_POINTS = 24
    }
}

/** The colour of a hold, from the pixels inside its outline. */
object HoldColour {
    /** How much of the outline is looked at, from its centre: the rim is shadow and wall. */
    private const val INNER = 0.8f

    /** Most pixels read from one hold; larger holds are read every few pixels. */
    private const val SAMPLES = 4000

    /** Chroma from which the more vivid half of a hold is its colour. */
    private const val VIVID = 20f

    /**
     * The colour of [hold] in [lab]. A hold with a colour is seldom all of that colour: chalk
     * and shade take it away in places. So when the more vivid half of its pixels has a clear
     * colour, that is the hold's; otherwise it is the mean of all of them.
     */
    fun of(lab: LabImage, hold: DetectedHold): Int {
        val w = lab.width
        val h = lab.height
        val cx = hold.center.x * w
        val cy = hold.center.y * h
        val xs = FloatArray(hold.contour.size) { cx + (hold.contour[it].x * w - cx) * INNER }
        val ys = FloatArray(hold.contour.size) { cy + (hold.contour[it].y * h - cy) * INNER }
        val top = ys.min().toInt().coerceIn(0, h - 1)
        val bottom = ceil(ys.max()).toInt().coerceIn(top, h - 1)
        val step = max(1, sqrt(hold.area.toFloat() / SAMPLES).toInt())
        val picked = ArrayList<Int>()
        val crossings = FloatArray(xs.size)
        var y = top
        while (y <= bottom) {
            val line = y + 0.5f
            var n = 0
            var j = xs.size - 1
            for (i in xs.indices) {
                if ((ys[i] > line) != (ys[j] > line)) crossings[n++] = (xs[j] - xs[i]) * (line - ys[i]) / (ys[j] - ys[i]) + xs[i]
                j = i
            }
            crossings.sort(0, n)
            var c = 0
            while (c + 1 < n) {
                var x = ceil(crossings[c] - 0.5f).toInt().coerceAtLeast(0)
                val to = ceil(crossings[c + 1] - 0.5f).toInt().coerceAtMost(w)
                while (x < to) {
                    picked += y * w + x
                    x += step
                }
                c += 2
            }
            y += step
        }
        // Too small to have an inside: the pixel at its centre.
        if (picked.isEmpty()) picked += cy.toInt().coerceIn(0, h - 1) * w + cx.toInt().coerceIn(0, w - 1)

        val all = mean(lab, picked)
        picked.sortByDescending { hypot(lab.a[it], lab.b[it]) }
        val vivid = mean(lab, picked.subList(0, (picked.size + 1) / 2))
        return ColorSpace.toArgb(if (hypot(vivid.a, vivid.b) >= VIVID) vivid else all)
    }

    private fun mean(lab: LabImage, pixels: List<Int>): Lab {
        var l = 0f
        var a = 0f
        var b = 0f
        for (p in pixels) {
            l += lab.l[p]
            a += lab.a[p]
            b += lab.b[p]
        }
        return Lab(l / pixels.size, a / pixels.size, b / pixels.size)
    }
}
