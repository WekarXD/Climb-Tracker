package com.climbtracker.core.detection

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Gives the holds a detector found the outline a segmentation model draws for them. The model
 * works on a square frame of [FRAME] pixels, to which the photo is scaled by its longer side
 * and anchored at the top left, and answers each box with a mask of [MASK] x [MASK] values,
 * positive where the object is.
 */
object Silhouettes {
    const val FRAME = 1024
    const val MASK = 256

    /** Margin around the detected hold in which its silhouette is looked for, in times its size. */
    private const val MARGIN = 1.5f

    /** A silhouette this many times larger than what was detected is something else: the wall. */
    private const val MAX_GROWTH = 12

    /** Largest share of the photo a silhouette may cover. */
    private const val MAX_SHARE = 0.06f

    /** Least overlap between the silhouette and what was detected, as a share of the smaller. */
    private const val MIN_OVERLAP = 0.5f

    /** Overlap of the bounding boxes from which two holds are the same one. */
    private const val SAME_BOX = 0.8f

    private const val REFERENCE_SIDE = 640f

    /** Shortest longer side, as a share of the photo's, from which a hold has a shape worth drawing. */
    private const val MIN_SIDE = 0.012f

    /** False for holds too small for the model to improve on what the detector drew. */
    fun worthRefining(hold: DetectedHold, width: Int, height: Int): Boolean {
        val side = max((hold.bounds.right - hold.bounds.left) * width, (hold.bounds.bottom - hold.bounds.top) * height)
        return side >= MIN_SIDE * max(width, height)
    }

    private fun frameScale(width: Int, height: Int) = FRAME / max(width, height).toFloat()

    /** The bounds of [hold] as x0, y0, x1, y1 in the frame of the model. */
    fun box(hold: DetectedHold, width: Int, height: Int): FloatArray {
        val k = frameScale(width, height)
        return floatArrayOf(hold.bounds.left * width * k, hold.bounds.top * height * k, hold.bounds.right * width * k, hold.bounds.bottom * height * k)
    }

    /**
     * [hold] with the outline of the mask at [offset] in [logits], for a photo of [width] x
     * [height]. The hold comes back untouched when the mask cannot be what was detected: empty,
     * far larger, or somewhere else. Colour and group are always the detector's.
     */
    fun refine(hold: DetectedHold, logits: FloatArray, offset: Int, width: Int, height: Int): DetectedHold {
        val left = hold.bounds.left * width
        val top = hold.bounds.top * height
        val right = hold.bounds.right * width
        val bottom = hold.bounds.bottom * height
        val padX = (right - left) * MARGIN + 8f
        val padY = (bottom - top) * MARGIN + 8f
        val x0 = floor(left - padX).toInt().coerceIn(0, width - 1)
        val y0 = floor(top - padY).toInt().coerceIn(0, height - 1)
        val x1 = ceil(right + padX).toInt().coerceIn(x0 + 1, width)
        val y1 = ceil(bottom + padY).toInt().coerceIn(y0 + 1, height)
        val w = x1 - x0
        val h = y1 - y0

        // The mask, brought to the pixels of the photo inside the window.
        val toMask = frameScale(width, height) * MASK / FRAME
        val mask = BooleanArray(w * h)
        for (y in 0 until h) {
            val my = ((y0 + y + 0.5f) * toMask - 0.5f).coerceIn(0f, MASK - 1f)
            val ya = my.toInt().coerceAtMost(MASK - 2)
            val fy = my - ya
            for (x in 0 until w) {
                val mx = ((x0 + x + 0.5f) * toMask - 0.5f).coerceIn(0f, MASK - 1f)
                val xa = mx.toInt().coerceAtMost(MASK - 2)
                val fx = mx - xa
                val i = offset + ya * MASK + xa
                val value = (logits[i] * (1 - fx) + logits[i + 1] * fx) * (1 - fy) +
                    (logits[i + MASK] * (1 - fx) + logits[i + MASK + 1] * fx) * fy
                mask[y * w + x] = value > 0f
            }
        }
        val silhouette = Components.find(mask, w, h).maxByOrNull { it.area } ?: return hold
        if (silhouette.area > MAX_SHARE * width * height) return hold
        // Running off the window on both sides, it is larger than a hold of this size can be.
        val acrossX = silhouette.minX == 0 && x0 > 0 && silhouette.maxX == w - 1 && x1 < width
        val acrossY = silhouette.minY == 0 && y0 > 0 && silhouette.maxY == h - 1 && y1 < height
        if (acrossX || acrossY) return hold

        val member = BooleanArray(w * h)
        for (p in silhouette.pixels) member[p] = true
        var detected = 0
        var shared = 0
        // What the detector drew, filled row by row: between each pair of crossings of its outline.
        val xs = FloatArray(hold.contour.size) { hold.contour[it].x * width - x0 }
        val ys = FloatArray(hold.contour.size) { hold.contour[it].y * height - y0 }
        val crossings = FloatArray(hold.contour.size)
        for (y in 0 until h) {
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
                val from = ceil(crossings[c] - 0.5f).toInt().coerceAtLeast(0)
                val to = ceil(crossings[c + 1] - 0.5f).toInt().coerceAtMost(w)
                for (x in from until to) {
                    detected++
                    if (member[y * w + x]) shared++
                }
                c += 2
            }
        }
        // A hold reduced to a point by the detector has no area to compare with.
        val known = max(detected, hold.area.coerceAtMost(w * h))
        if (silhouette.area > MAX_GROWTH * max(known, 1)) return hold
        if (detected > 0 && shared < MIN_OVERLAP * min(detected, silhouette.area)) return hold

        var start = Int.MAX_VALUE
        var sx = 0L
        var sy = 0L
        for (p in silhouette.pixels) {
            if (p < start) start = p
            sx += p % w
            sy += p / w
        }
        val radius = max(2, (1.5f * max(width, height) / REFERENCE_SIDE).roundToInt())
        val points = Contours.smooth(Contours.trace(member, w, h, start), radius)
        val contour = ArrayList<PointF>(points.size / 2)
        for (i in points.indices step 2) {
            contour += PointF(
                ((x0 + points[i] + 0.5f) / width).coerceIn(0f, 1f),
                ((y0 + points[i + 1] + 0.5f) / height).coerceIn(0f, 1f),
            )
        }
        if (contour.size < 3) return hold
        return hold.copy(
            contour = contour,
            bounds = Bounds(
                (x0 + silhouette.minX) / width.toFloat(), (y0 + silhouette.minY) / height.toFloat(),
                (x0 + silhouette.maxX + 1) / width.toFloat(), (y0 + silhouette.maxY + 1) / height.toFloat(),
            ),
            center = PointF((x0 + sx.toFloat() / silhouette.area + 0.5f) / width, (y0 + sy.toFloat() / silhouette.area + 0.5f) / height),
            area = silhouette.area,
        )
    }

    /**
     * Two detections of the same hold end up with the same silhouette. Of holds whose bounds
     * all but coincide, only the first is kept.
     */
    fun dedupe(holds: List<DetectedHold>): List<DetectedHold> {
        val kept = ArrayList<DetectedHold>()
        for (hold in holds) {
            if (kept.none { overlap(it.bounds, hold.bounds) > SAME_BOX }) kept += hold
        }
        return kept
    }

    /** Intersection over union of two boxes. */
    private fun overlap(a: Bounds, b: Bounds): Float {
        val w = min(a.right, b.right) - max(a.left, b.left)
        val h = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (w <= 0f || h <= 0f) return 0f
        val both = w * h
        return both / ((a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - both)
    }
}
