package com.climbtracker.core.detection

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Joins regions that are the same object under different light. Colour clustering splits a
 * volume into its faces and a hold into its lit and shaded sides, because they differ in
 * lightness; they keep the same hue, or are all colourless.
 */
object Shading {
    /** Below this chroma a colour counts as colourless (black, grey, white). */
    private const val NEUTRAL_CHROMA = 12f
    private const val MAX_HUE_GAP = 25.0

    /** Chroma under which a region is only tinted, not coloured. */
    private const val DULL_CHROMA = 20f

    /** Least ratio between the chroma of two regions of one colour. */
    private const val ALIKE_CHROMA = 0.45f

    /** Regions this many pixels apart still touch: opening each cluster leaves thin gaps. */
    private const val REACH = 2

    /** Pixels of [c] with a 4-neighbour outside it: an estimate of its perimeter. */
    fun perimeter(c: Component, member: BooleanArray, width: Int, height: Int): Int {
        for (p in c.pixels) member[p] = true
        var edge = 0
        for (p in c.pixels) {
            val x = p % width
            val y = p / width
            if (x == 0 || x == width - 1 || y == 0 || y == height - 1 ||
                !member[p - 1] || !member[p + 1] || !member[p - width] || !member[p + width]
            ) {
                edge++
            }
        }
        for (p in c.pixels) member[p] = false
        return edge
    }

    /** Share of a part's perimeter that must border another part to count as surrounded by it. */
    private const val SURROUNDED = 0.6f

    /** True for a colour with too little chroma to have a hue: black, grey or white. */
    fun isNeutral(lab: FloatArray): Boolean = hypot(lab[1], lab[2]) < NEUTRAL_CHROMA

    /**
     * Gives the pixels of a colourless cluster to a coloured one when they touch it, have its hue
     * and clearly more colour than their own cluster. A hold in the shade, or dusted with chalk,
     * keeps its hue but loses so much colour that most of it is clustered with the grey volume it
     * is mounted on, leaving only its most vivid speck as the hold.
     *
     * [labels] holds the cluster of each foreground pixel and [index] its position in the image.
     * No pixel further than [reach] from the vivid part is taken: the dull side of a hold is next
     * to its vivid side, while a shadow or a frame of the same tint runs on.
     */
    fun spreadColour(
        labels: IntArray,
        index: IntArray,
        centers: List<FloatArray>,
        lab: LabImage,
        foreground: BooleanArray,
        width: Int,
        height: Int,
        reach: Int = Int.MAX_VALUE,
        background: LabImage? = null,
    ) {
        val at = IntArray(width * height) { -1 }
        for (i in index.indices) at[index[i]] = i
        // First, by itself: a pixel with a definite colour belongs with the cluster of that hue
        // even if a grey one is nearer, as happens when it is much darker or lighter than
        // the vivid holds of its colour. Some holds have no pixel that falls in their own.
        val coloured = centers.indices.filter { !isNeutral(centers[it]) }
        if (coloured.isNotEmpty()) {
            for (i in index.indices) {
                if (!isNeutral(centers[labels[i]])) continue
                val q = index[i]
                val colour = floatArrayOf(lab.l[q], lab.a[q], lab.b[q])
                val wall = if (background == null) 0f else hypot(background.a[q], background.b[q])
                if (hypot(colour[1], colour[2]) < max(CLAIM_CHROMA, wall + CLAIM_OVER_WALL)) continue
                val nearest = coloured.minBy { hueGap(colour, centers[it]) }
                if (hueGap(colour, centers[nearest]) <= SPREAD_HUE_GAP) labels[i] = nearest
            }
        }

        val queue = IntArray(index.size)
        // How far each pixel is from the vivid part it was reached from.
        val steps = IntArray(index.size)
        for (k in centers.indices) {
            val centre = centers[k]
            if (isNeutral(centre)) continue
            var head = 0
            var tail = 0
            for (i in index.indices) if (labels[i] == k) {
                steps[i] = 0
                queue[tail++] = i
            }
            while (head < tail) {
                val from = queue[head++]
                if (steps[from] >= reach) continue
                val p = index[from]
                val x = p % width
                val y = p / width
                for (d in 0..3) {
                    val nx = x + DX[d]
                    val ny = y + DY[d]
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue
                    val q = ny * width + nx
                    val i = at[q]
                    if (i < 0 || !foreground[q] || labels[i] == k || !isNeutral(centers[labels[i]])) continue
                    val colour = floatArrayOf(lab.l[q], lab.a[q], lab.b[q])
                    // Against something dark, a volume or deep shade, a little colour is enough
                    // to tell the hold. Pale things are often tinted like the wall itself, so
                    // among them it must have clearly more colour than the wall around it.
                    val own = centers[labels[i]]
                    val least = if (own[0] < SPREAD_DARK || background == null) {
                        SPREAD_MIN_CHROMA
                    } else {
                        max(SPREAD_MIN_CHROMA, hypot(background.a[q], background.b[q]) + SPREAD_OVER_WALL)
                    }
                    if (hypot(colour[1], colour[2]) < least || hueGap(colour, centre) > SPREAD_HUE_GAP) continue
                    labels[i] = k
                    steps[i] = steps[from] + 1
                    queue[tail++] = i
                }
            }
        }
    }

    private val DX = intArrayOf(-1, 1, 0, 0)
    private val DY = intArrayOf(0, 0, -1, 1)

    /** Chroma over that of the wall a pixel among pale things needs to be taken into a hold. */
    private const val SPREAD_OVER_WALL = 6f

    /** Chroma from which a pixel has a colour of its own, whatever cluster it fell into. */
    private const val CLAIM_CHROMA = 20f
    private const val CLAIM_OVER_WALL = 5f

    /** Lightness under which a colourless cluster is dark: volumes and shade. */
    private const val SPREAD_DARK = 50f
    private const val SPREAD_MIN_CHROMA = 7f
    private const val SPREAD_HUE_GAP = 30.0

    /**
     * As [sameMaterial], and also about as vivid. A beige wall or a pale hold has the hue of a
     * yellow hold, with a fraction of its colour: they are not the same thing.
     */
    fun sameColour(a: FloatArray, b: FloatArray): Boolean {
        val chromaA = hypot(a[1], a[2])
        val chromaB = hypot(b[1], b[2])
        // Two faint tints are the same grey seen in different light, whatever their hue.
        if (chromaA < DULL_CHROMA && chromaB < DULL_CHROMA) return true
        if (!sameMaterial(a, b)) return false
        return min(chromaA, chromaB) >= ALIKE_CHROMA * max(chromaA, chromaB)
    }

    /** Difference in hue between two Lab colours, in degrees (0..180). */
    fun hueGap(a: FloatArray, b: FloatArray): Double {
        var gap = abs(Math.toDegrees(atan2(a[2], a[1]).toDouble()) - Math.toDegrees(atan2(b[2], b[1]).toDouble()))
        if (gap > 180.0) gap = 360.0 - gap
        return gap
    }

    /** True when two Lab colours are both colourless, or share a hue. */
    fun sameMaterial(a: FloatArray, b: FloatArray): Boolean {
        val chromaA = hypot(a[1], a[2])
        val chromaB = hypot(b[1], b[2])
        if (chromaA < NEUTRAL_CHROMA && chromaB < NEUTRAL_CHROMA) return true
        if (chromaA < NEUTRAL_CHROMA || chromaB < NEUTRAL_CHROMA) return false
        var gap = abs(Math.toDegrees(atan2(a[2], a[1]).toDouble()) - Math.toDegrees(atan2(b[2], b[1]).toDouble()))
        if (gap > 180.0) gap = 360.0 - gap
        return gap < MAX_HUE_GAP
    }

    /**
     * Merges [parts] whose colour clusters are the same material and that share a long enough
     * border: at least [minContact] of the perimeter of the smaller one. Two faces of a volume
     * share a whole edge; two separate holds that happen to touch share only a few pixels.
     * [cluster] gives the cluster of each part and [centers] the Lab colour of each cluster.
     * Each result carries the cluster of its largest part.
     *
     * With [colors], the actual mean colour of each part, materials are compared by those and
     * not by the clusters. A hold of a colour that has no cluster of its own, like ochre between
     * yellow and red, is split among the nearest ones; its pieces still have its colour.
     */
    fun merge(
        parts: List<Component>,
        cluster: List<Int>,
        centers: List<FloatArray>,
        width: Int,
        height: Int,
        minContact: Float = 0.12f,
        colors: List<FloatArray>? = null,
    ): List<Pair<Component, Int>> {
        fun colorOf(part: Int) = colors?.get(part) ?: centers[cluster[part]]
        val label = IntArray(width * height) { -1 }
        parts.forEachIndexed { i, part -> for (p in part.pixels) label[p] = i }
        val member = BooleanArray(width * height)
        val perimeters = IntArray(parts.size) { perimeter(parts[it], member, width, height) }

        val parent = IntArray(parts.size) { it }
        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            var j = i
            while (parent[j] != r) {
                val next = parent[j]
                parent[j] = r
                j = next
            }
            return r
        }
        // Length of the border between each pair of parts, counted in pixels.
        val contact = HashMap<Long, Int>()
        fun touch(a: Int, b: Int): Boolean {
            if (b < 0 || a == b) return false
            val key = min(a, b).toLong() * parts.size + max(a, b)
            contact[key] = (contact[key] ?: 0) + 1
            return true
        }
        for (y in 0 until height) for (x in 0 until width) {
            val a = label[y * width + x]
            if (a < 0) continue
            // The nearest other part to the right and below, each counted once.
            // Only from the pixels on the edge of the part, so a border is not counted twice.
            for (d in 1..REACH) {
                if (x + d >= width) break
                val b = label[y * width + x + d]
                if (b == a || touch(a, b)) break
            }
            for (d in 1..REACH) {
                if (y + d >= height) break
                val b = label[(y + d) * width + x]
                if (b == a || touch(a, b)) break
            }
        }
        for ((key, length) in contact) {
            val a = (key / parts.size).toInt()
            val b = (key % parts.size).toInt()
            val small = if (parts[a].area <= parts[b].area) a else b
            val same = if (colors == null) sameMaterial(colorOf(a), colorOf(b)) else sameColour(colorOf(a), colorOf(b))
            // A colourless speck surrounded by a hold is its bolt hole, a shadow or a glint,
            // whatever the colour of the hold. A coloured one is a hold mounted on a volume.
            val speck = length >= SURROUNDED * perimeters[small] && isNeutral(colorOf(small))
            if (!speck) {
                if (!same) continue
                if (length < minContact * min(perimeters[a], perimeters[b])) continue
            }
            val ra = root(a)
            val rb = root(b)
            if (ra != rb) parent[max(ra, rb)] = min(ra, rb)
        }

        val groups = LinkedHashMap<Int, MutableList<Int>>()
        for (i in parts.indices) groups.getOrPut(root(i)) { ArrayList() } += i
        return groups.values.map { members ->
            if (members.size == 1) return@map parts[members[0]] to cluster[members[0]]
            val pixels = IntArray(members.sumOf { parts[it].area })
            var offset = 0
            for (m in members) {
                parts[m].pixels.copyInto(pixels, offset)
                offset += parts[m].area
            }
            val whole = Component(
                pixels,
                members.minOf { parts[it].minX }, members.minOf { parts[it].minY },
                members.maxOf { parts[it].maxX }, members.maxOf { parts[it].maxY },
            )
            whole to cluster[members.maxBy { parts[it].area }]
        }
    }
}
