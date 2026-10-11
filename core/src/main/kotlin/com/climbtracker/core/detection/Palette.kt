package com.climbtracker.core.detection

import kotlin.math.atan2

/** The colours a circuit can be chosen by: the holds of a photo sorted into groups of one colour. */
object Palette {

    /**
     * Builds the palette. Colour clusters of the same hue become one group, so a route whose
     * holds look lighter in the open and darker in the shade is still a single colour. Black,
     * grey and white have no hue and stay apart. Groups are numbered by hue.
     */
    fun regroup(holds: List<DetectedHold>): DetectionResult {
        if (holds.isEmpty()) return DetectionResult(emptyList(), emptyList())
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

    /**
     * The palette of holds that come with a colour each and no group. Those with a hue are
     * grouped by it, the larger ones first, as the surest of their colour; those without go
     * to black, grey or white by how light they are.
     */
    fun group(holds: List<DetectedHold>): DetectionResult {
        val keyed = holds.sortedByDescending { it.area }.mapIndexed { i, hold ->
            val lab = ColorSpace.toLab(hold.argb)
            val key = when {
                !Shading.isNeutral(floatArrayOf(lab.l, lab.a, lab.b)) -> i
                lab.l < DARK -> BLACK
                lab.l > PALE -> WHITE
                else -> GREY
            }
            hold.copy(colorGroup = key)
        }
        return regroup(keyed)
    }

    /** Lightness under which a hold without hue is black, and over which it is white. */
    private const val DARK = 35f
    private const val PALE = 68f

    private const val BLACK = -1
    private const val GREY = -2
    private const val WHITE = -3
}
