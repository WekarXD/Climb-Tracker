package com.climbtracker.core.detection

object Contours {
    // Clockwise on screen (y grows downwards), starting at west.
    private val DX = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)
    private val DY = intArrayOf(0, -1, -1, -1, 0, 1, 1, 1)

    /**
     * Moore-neighbour boundary tracing. [member] marks the pixels of one component and [start] is
     * its lowest index, so the pixels west and above it are background.
     * Returns interleaved x, y pairs in clockwise order.
     */
    fun trace(member: BooleanArray, width: Int, height: Int, start: Int): IntArray {
        val sx = start % width
        val sy = start / width
        var out = IntArray(64)
        var size = 0
        var cx = sx
        var cy = sy
        var back = 0
        var secondX = -1
        var secondY = -1
        val limit = 4 * width * height + 8
        var steps = 0
        while (steps++ < limit) {
            var found = -1
            for (i in 1..8) {
                val d = (back + i) and 7
                val nx = cx + DX[d]
                val ny = cy + DY[d]
                if (nx in 0 until width && ny in 0 until height && member[ny * width + nx]) {
                    found = d
                    break
                }
            }
            if (found < 0) {
                // Isolated pixel.
                out[0] = cx
                out[1] = cy
                size = 2
                break
            }
            val nx = cx + DX[found]
            val ny = cy + DY[found]
            // Back at the start and about to repeat the first move: the loop is closed.
            if (cx == sx && cy == sy && secondX >= 0 && nx == secondX && ny == secondY) break
            if (size + 2 > out.size) out = out.copyOf(out.size * 2)
            out[size++] = cx
            out[size++] = cy
            if (secondX < 0) {
                secondX = nx
                secondY = ny
            }
            // The neighbour examined just before the hit is background; search resumes from it.
            val previous = (found + 7) and 7
            back = direction(cx + DX[previous] - nx, cy + DY[previous] - ny)
            cx = nx
            cy = ny
        }
        return out.copyOf(size)
    }

    private fun direction(dx: Int, dy: Int): Int {
        for (i in 0..7) if (DX[i] == dx && DY[i] == dy) return i
        return 0
    }

    /**
     * Rounds the pixel staircase of a traced outline. Each point becomes the mean of the points
     * within [radius] steps of it along the closed outline, and the result is thinned evenly to
     * at most [maxPoints]. Returns interleaved x, y. Outlines too short to average are returned
     * as they are.
     */
    fun smooth(points: IntArray, radius: Int = 2, maxPoints: Int = 96): FloatArray {
        val n = points.size / 2
        if (n < 2 * radius + 2) return FloatArray(points.size) { points[it].toFloat() }
        val count = if (n < maxPoints) n else maxPoints
        val window = 2 * radius + 1
        val out = FloatArray(count * 2)
        for (k in 0 until count) {
            val i = (k.toLong() * n / count).toInt()
            var sx = 0f
            var sy = 0f
            for (d in -radius..radius) {
                val j = ((i + d) % n + n) % n
                sx += points[j * 2]
                sy += points[j * 2 + 1]
            }
            out[k * 2] = sx / window
            out[k * 2 + 1] = sy / window
        }
        return out
    }
}
