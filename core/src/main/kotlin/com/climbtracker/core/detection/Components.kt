package com.climbtracker.core.detection

/** A connected region. [pixels] are indices y * width + x; pixels[0] is the first in raster order. */
class Component(val pixels: IntArray, val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
    val area: Int get() = pixels.size
}

object Components {

    /** 8-connected components of [mask], in raster order of their first pixel. */
    fun find(mask: BooleanArray, width: Int, height: Int): List<Component> {
        val seen = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        val out = ArrayList<Component>()
        for (start in mask.indices) {
            if (!mask[start] || seen[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1
            while (head < tail) {
                val p = queue[head++]
                val x = p % width
                val y = p / width
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= height) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= width) continue
                        val q = ny * width + nx
                        if (mask[q] && !seen[q]) {
                            seen[q] = true
                            queue[tail++] = q
                        }
                    }
                }
            }
            out += Component(queue.copyOfRange(0, tail), minX, minY, maxX, maxY)
        }
        return out
    }
}
