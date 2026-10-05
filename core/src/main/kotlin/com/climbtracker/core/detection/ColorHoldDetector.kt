package com.climbtracker.core.detection

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Sizes are given for an image whose long side is 640 px; the detector scales them up for
 * larger images, so the same parameters serve any resolution.
 */
data class DetectorParams(
    val smoothRadius: Int = 2,
    val backgroundScale: Int = 4,
    val backgroundRadius: Int = 12,
    val chromaThreshold: Float = 10f,
    /** Lightness difference needed for a pixel lighter than its background. */
    val lightThreshold: Float = 38f,
    /** Lightness difference needed for a pixel darker than its background. */
    val darkThreshold: Float = 27f,
    val clusters: Int = 8,
    val mergeDistance: Float = 16f,
    /** Smallest colourless hold, in pixels. Bolt holes and chalk specks fall below it. */
    val minArea: Int = 14,
    /**
     * Smallest coloured hold, in pixels. Much lower than [minArea]: tiny footholds are only a
     * few pixels across, and what tells them from noise is that they have a colour.
     */
    val minChromaticArea: Int = 2,
    val maxAreaFraction: Float = 0.06f,
    val ringDistance: Float = 14f,
    val ringFraction: Float = 0.25f,
    /** Share of the smaller part's perimeter two parts must share to count as one hold. */
    val minContact: Float = 0.12f,
    /**
     * Largest perimeter squared over area, relative to a circle, a hold may have. Grilles and
     * the slivers between wall panels have far more outline than a solid shape of their area.
     */
    val maxCompactness: Float = 6f,
    val growTolerance: Float = 18f,
    /** Take small regions inside a hold as part of it. */
    val dropNested: Boolean = true,
    /** Drop rows of alike colourless shapes: lettering and grilles. */
    val dropRuns: Boolean = true,
    /** Let a coloured hold take in the duller pixels of its own hue around it. */
    val spreadColour: Boolean = true,
    /** How far from its vivid part a hold may take in duller pixels. */
    val spreadReach: Float = 12f,
    /** Look for holds of the colour of the wall by their outline. */
    val outlines: Boolean = true,
    /** Change in lightness across two pixels that makes an edge, in a 640 px image. */
    val outlineGradient: Float = 5f,
    /** Margin kept around the holds already found, where edges are theirs. */
    val outlineClear: Float = 0.5f,
    /** Share of an outline already taken by other holds from which it is just their rim. */
    val outlineOverlap: Float = 0.35f,
    val outline: OutlineParams = OutlineParams(),
)

/**
 * Finds holds as regions that differ from their local background, then groups them by colour.
 * The background is a large-window median computed on a reduced copy of the image, so walls
 * made of panels of different colours are handled.
 */
class ColorHoldDetector(private val params: DetectorParams = DetectorParams()) : HoldDetector {

    @Volatile
    private var lastSmoothed: Pair<PixelImage, LabImage>? = null

    override fun detect(image: PixelImage, sensitivity: Float): DetectionResult {
        val w = image.width
        val h = image.height
        if (w == 0 || h == 0) return EMPTY

        // How much larger the image is than the 640 px the sizes in the parameters refer to.
        val scale = max(1f, max(w, h) / REFERENCE_SIDE)
        val smooth = Filters.median(Filters.split(image), params.smoothRadius)
        val reduced = Filters.downscale(smooth, (params.backgroundScale * scale).roundToInt())
        val background = Filters.upscale(Filters.median(reduced, params.backgroundRadius), w, h)
        val lab = Filters.toLab(smooth)
        val labBackground = Filters.toLab(background)

        val factor = 1.5f - sensitivity.coerceIn(0f, 1f)
        val raw = Mask.foreground(
            lab, labBackground,
            params.chromaThreshold * factor, params.lightThreshold * factor, params.darkThreshold * factor,
        )
        val foreground = Mask.close(Mask.open(raw, w, h), w, h)

        val count = foreground.count { it }
        if (count < params.clusters) return EMPTY
        val index = IntArray(count)
        val samples = FloatArray(count * 3)
        var n = 0
        for (i in foreground.indices) {
            if (!foreground[i]) continue
            index[n] = i
            samples[n * 3] = lab.l[i]
            samples[n * 3 + 1] = lab.a[i]
            samples[n * 3 + 2] = lab.b[i]
            n++
        }
        val clusters = KMeans.cluster(samples, params.clusters, params.mergeDistance)
        if (params.spreadColour) Shading.spreadColour(clusters.labels, index, clusters.centers, lab, foreground, w, h, (params.spreadReach * scale).roundToInt(), labBackground)

        val maxArea = (params.maxAreaFraction * w * h).toInt()
        // Regions of each colour cluster, before any filtering.
        val parts = ArrayList<Component>()
        val partCluster = ArrayList<Int>()
        for (g in clusters.centers.indices) {
            val mask = BooleanArray(w * h)
            for (i in 0 until count) if (clusters.labels[i] == g) mask[index[i]] = true
            for (c in Components.find(Mask.open(mask, w, h), w, h)) {
                parts += c
                partCluster += g
            }
        }

        val member = BooleanArray(w * h)
        val found = BooleanArray(w * h)
        val holds = ArrayList<DetectedHold>()
        // Touching regions of the same material are one hold: the faces of a volume, or the lit
        // and shaded sides of a hold. Filters run afterwards, on the whole hold.
        val partColors = parts.map { mean(it, lab) }
        for ((c, cluster) in Shading.merge(parts, partCluster, clusters.centers, w, h, params.minContact, partColors)) {
            // The pieces of a hold may come from several clusters. It goes with the one of its
            // own hue, not with that of its largest piece.
            val colour = mean(c, lab)
            val g = if (Shading.isNeutral(colour)) {
                cluster
            } else {
                clusters.centers.indices.filter { !Shading.isNeutral(clusters.centers[it]) }
                    .minByOrNull { Shading.hueGap(colour, clusters.centers[it]) }
                    ?.takeIf { Shading.sameMaterial(colour, clusters.centers[it]) } ?: cluster
            }
            val smallest = if (Shading.isNeutral(clusters.centers[g])) params.minArea else params.minChromaticArea
            if (c.area < smallest * scale * scale || c.area > maxArea) continue
            val outline = Shading.perimeter(c, member, w, h).toFloat()
            if (outline * outline / (4f * PI.toFloat() * c.area) > params.maxCompactness) continue
            // Scaled with the thresholds so a faint hold found at high sensitivity is not
            // then mistaken for a continuation of the wall around it.
            if (Ring.continues(c, lab, params.ringDistance * factor, params.ringFraction, scale)) continue
            holds += buildHold(c, lab, member, g)
            for (p in c.pixels) found[p] = true
        }
        val kept = Clutter.remove(holds, w, h, params.dropNested, params.dropRuns)
        if (!params.outlines) return regroup(kept)

        // Second pass, for what has the colour of the wall and so was not foreground at all.
        var taken = found
        repeat(max(1, (params.outlineClear * scale).roundToInt())) { taken = Mask.dilate(taken, w, h) }
        // An edge spreads over more pixels in a larger image, so it is less steep.
        val steepness = params.outlineGradient / scale * factor
        val pale = clusters.centers.size
        val result = ArrayList(kept)
        for (o in Outlines.find(lab.l, labBackground.l, taken, w, h, scale, steepness, params.outline)) {
            // Mostly covered by holds already found: this is the shadow around one of them.
            if (Outlines.covered(o, found, w, h) > params.outlineOverlap) continue
            val hold = outlineHold(o, lab, pale)
            // The colour pass sees a pale hold only as its shadow, a dark sliver. Now that the
            // whole hold is known, those slivers are part of it.
            result.removeAll { other ->
                other.area < hold.area / 2 && Outlines.contains(o, other.center.x * w, other.center.y * h) &&
                    Shading.isNeutral(ColorSpace.toLab(other.argb).let { floatArrayOf(it.l, it.a, it.b) })
            }
            result += hold
        }
        return regroup(result)
    }

    override fun growRegion(image: PixelImage, x: Int, y: Int): DetectedHold? {
        val w = image.width
        val h = image.height
        if (x !in 0 until w || y !in 0 until h) return null

        val lab = smoothed(image)
        val seedIndex = y * w + x
        val seed = lab.at(seedIndex)
        val maxArea = (params.maxAreaFraction * w * h).toInt()

        val seen = BooleanArray(w * h)
        val queue = IntArray(maxArea + 8)
        var head = 0
        var tail = 0
        queue[tail++] = seedIndex
        seen[seedIndex] = true
        var minX = x
        var maxX = x
        var minY = y
        var maxY = y
        while (head < tail) {
            val p = queue[head++]
            val px = p % w
            val py = p / w
            if (px < minX) minX = px
            if (px > maxX) maxX = px
            if (py < minY) minY = py
            if (py > maxY) maxY = py
            for (d in 0..3) {
                val nx = px + NEIGHBOUR_DX[d]
                val ny = py + NEIGHBOUR_DY[d]
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                val q = ny * w + nx
                if (seen[q] || seed.distance(lab.at(q)) >= params.growTolerance) continue
                // Too large: the touch landed on wall, not on a hold.
                if (tail > maxArea) return null
                seen[q] = true
                queue[tail++] = q
            }
        }
        val scale = max(1f, max(w, h) / REFERENCE_SIDE)
        if (tail < params.minChromaticArea * scale * scale) return null
        val component = Component(queue.copyOfRange(0, tail), minX, minY, maxX, maxY)
        return buildHold(component, lab, BooleanArray(w * h), -1)
    }

    /** Smoothed Lab copy of [image], kept for repeated manual holds on the same wall. */
    private fun smoothed(image: PixelImage): LabImage {
        val cached = lastSmoothed
        if (cached != null && cached.first === image) return cached.second
        val lab = Filters.toLab(Filters.median(Filters.split(image), params.smoothRadius))
        lastSmoothed = image to lab
        return lab
    }

    private fun buildHold(c: Component, lab: LabImage, member: BooleanArray, group: Int): DetectedHold {
        val w = lab.width
        val h = lab.height
        var sl = 0f
        var sa = 0f
        var sb = 0f
        var sx = 0L
        var sy = 0L
        var start = Int.MAX_VALUE
        for (p in c.pixels) {
            member[p] = true
            sl += lab.l[p]
            sa += lab.a[p]
            sb += lab.b[p]
            sx += p % w
            sy += p / w
            if (p < start) start = p
        }
        // Averaged over a few pixels, more on larger images, so the outline is a curve and not
        // the staircase of the pixel grid.
        val radius = max(2, (1.5f * max(w, h) / REFERENCE_SIDE).roundToInt())
        val points = Contours.smooth(Contours.trace(member, w, h, start), radius)
        for (p in c.pixels) member[p] = false

        val contour = ArrayList<PointF>(points.size / 2)
        for (i in points.indices step 2) {
            contour += PointF(
                ((points[i] + 0.5f) / w).coerceIn(0f, 1f),
                ((points[i + 1] + 0.5f) / h).coerceIn(0f, 1f),
            )
        }
        return DetectedHold(
            contour = contour,
            bounds = Bounds(c.minX / w.toFloat(), c.minY / h.toFloat(), (c.maxX + 1) / w.toFloat(), (c.maxY + 1) / h.toFloat()),
            center = PointF((sx.toFloat() / c.area + 0.5f) / w, (sy.toFloat() / c.area + 0.5f) / h),
            argb = ColorSpace.toArgb(Lab(sl / c.area, sa / c.area, sb / c.area)),
            colorGroup = group,
            area = c.area,
        )
    }

    private fun mean(c: Component, lab: LabImage): FloatArray {
        var l = 0f
        var a = 0f
        var b = 0f
        for (p in c.pixels) {
            l += lab.l[p]
            a += lab.a[p]
            b += lab.b[p]
        }
        return floatArrayOf(l / c.area, a / c.area, b / c.area)
    }

    /** A hold known only by its convex outline; its colour is that of the edges that drew it. */
    private fun outlineHold(o: Outline, lab: LabImage, group: Int): DetectedHold {
        val w = lab.width
        val h = lab.height
        val n = o.hull.size / 2
        val contour = List(n) { PointF(((o.hull[it * 2] + 0.5f) / w).coerceIn(0f, 1f), ((o.hull[it * 2 + 1] + 0.5f) / h).coerceIn(0f, 1f)) }
        var sl = 0f
        var sa = 0f
        var sb = 0f
        for (p in o.pixels) {
            sl += lab.l[p]
            sa += lab.a[p]
            sb += lab.b[p]
        }
        val count = o.pixels.size
        return DetectedHold(
            contour = contour,
            bounds = Bounds(contour.minOf { it.x }, contour.minOf { it.y }, contour.maxOf { it.x }, contour.maxOf { it.y }),
            center = PointF(contour.map { it.x }.average().toFloat(), contour.map { it.y }.average().toFloat()),
            argb = ColorSpace.toArgb(Lab(sl / count, sa / count, sb / count)),
            colorGroup = group,
            area = o.area.toInt(),
        )
    }

    /**
     * Builds the palette. Colour clusters of the same hue become one group, so a route whose
     * holds look lighter in the open and darker in the shade is still a single colour. Black,
     * grey and white have no hue and stay apart. Groups are numbered by hue.
     */
    private fun regroup(holds: List<DetectedHold>): DetectionResult {
        if (holds.isEmpty()) return EMPTY
        class Acc {
            var l = 0f
            var a = 0f
            var b = 0f
            var area = 0
            var count = 0
            fun mean() = floatArrayOf(l / area, a / area, b / area)
        }
        val raw = LinkedHashMap<Int, Acc>()
        for (hold in holds) {
            val acc = raw.getOrPut(hold.colorGroup) { Acc() }
            val lab = ColorSpace.toLab(hold.argb)
            acc.l += lab.l * hold.area
            acc.a += lab.a * hold.area
            acc.b += lab.b * hold.area
            acc.area += hold.area
            acc.count++
        }

        // Each cluster joins the first earlier one of its hue.
        val keys = raw.keys.toList()
        val family = HashMap<Int, Int>()
        for (i in keys.indices) {
            val mine = raw.getValue(keys[i]).mean()
            var head = keys[i]
            if (!Shading.isNeutral(mine)) {
                for (j in 0 until i) {
                    if (family.getValue(keys[j]) != keys[j]) continue
                    val other = raw.getValue(keys[j]).mean()
                    if (!Shading.isNeutral(other) && Shading.sameMaterial(mine, other)) {
                        head = keys[j]
                        break
                    }
                }
            }
            family[keys[i]] = head
        }
        val merged = LinkedHashMap<Int, Acc>()
        for (key in keys) {
            val from = raw.getValue(key)
            val into = merged.getOrPut(family.getValue(key)) { Acc() }
            into.l += from.l
            into.a += from.a
            into.b += from.b
            into.area += from.area
            into.count += from.count
        }

        val ordered = merged.entries.sortedBy { atan2(it.value.b / it.value.area, it.value.a / it.value.area) }
        val newIndex = HashMap<Int, Int>()
        val groups = ordered.mapIndexed { i, (head, acc) ->
            newIndex[head] = i
            ColorGroup(i, ColorSpace.toArgb(Lab(acc.l / acc.area, acc.a / acc.area, acc.b / acc.area)), acc.count)
        }
        return DetectionResult(
            holds.map { it.copy(colorGroup = newIndex.getValue(family.getValue(it.colorGroup))) },
            groups,
        )
    }

    private companion object {
        val EMPTY = DetectionResult(emptyList(), emptyList())
        const val REFERENCE_SIDE = 640f
        val NEIGHBOUR_DX = intArrayOf(-1, 1, 0, 0)
        val NEIGHBOUR_DY = intArrayOf(0, 0, -1, 1)
    }
}
