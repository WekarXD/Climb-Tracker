package com.climbtracker.core.detection

import com.climbtracker.core.editor.EditorHold
import com.climbtracker.core.editor.HitTest
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Measures the detector against holds annotated by hand on the reference photos
 * (src/test/resources/holds). A real hold counts as found when its annotated point falls inside
 * the outline of a detected hold, or within the annotation error of it; a detected hold with no
 * annotated point is a false positive. The thresholds are a floor under the current results, to catch regressions: raise
 * them when the detector improves.
 *
 * Some photos are kept aside and measured apart. Change the detector looking only at the others,
 * so that what these say is how it does on a wall it was not adjusted for.
 */
open class DetectionQualityTest {

    /** What is measured, for the report and the pictures. */
    protected open val stage: String get() = "detector"

    /** What finds the holds; the colour detector here. */
    protected open fun detect(image: PixelImage): List<DetectedHold> = ColorHoldDetector().detect(image, 0.5f).holds

    /** Step applied to what the detector finds before measuring it; none here. */
    protected open fun refine(image: PixelImage, holds: List<DetectedHold>): List<DetectedHold> = holds

    protected open val minColourRecall: Float get() = MIN_COLOUR_RECALL
    protected open val minTotalRecall: Float get() = MIN_TOTAL_RECALL
    protected open val maxFalsePositives: Int get() = MAX_FALSE_POSITIVES
    protected open val minHeldOutRecall: Float get() = MIN_HELD_OUT_RECALL
    protected open val maxHeldOutFalsePositives: Int get() = MAX_HELD_OUT_FALSE_POSITIVES

    private class Truth(val x: Float, val y: Float, val kind: String)

    private class Score(val photo: String, val truth: List<Truth>, val found: Set<Truth>, val detected: Int, val falsePositives: Int, val merged: Int) {
        fun recall(kind: String? = null): Float {
            val wanted = truth.filter { kind == null || it.kind == kind }
            return if (wanted.isEmpty()) 1f else wanted.count { it in found }.toFloat() / wanted.size
        }
    }

    /** Annotation error allowed around a detected outline, as a fraction of the photo width. */
    private val margin = 0.015f

    private fun truth(photo: String): List<Truth> =
        checkNotNull(javaClass.getResourceAsStream("/holds/$photo.txt")) { "no annotations for $photo" }
            .bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val (x, y, kind) = line.trim().split(Regex("\\s+"))
                Truth(x.toFloat() / 100f, y.toFloat() / 100f, kind)
            }

    private fun pixels(photo: String): PixelImage {
        val source = ImageIO.read(File("../fotos-referencia/$photo.jpg"))
        val scale = SIDE.toDouble() / max(source.width, source.height)
        val w = (source.width * scale).roundToInt()
        val h = (source.height * scale).roundToInt()
        val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = scaled.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(source, 0, 0, w, h, null)
        g.dispose()
        return PixelImage(w, h, scaled.getRGB(0, 0, w, h, null, 0, w))
    }

    private fun score(photo: String): Score {
        val truth = truth(photo)
        val image = pixels(photo)
        val holds = refine(image, detect(image))
            .mapIndexed { i, hold -> EditorHold(i.toLong(), hold.contour, hold.colorGroup, hold.argb) }
        // Each annotated point is assigned to the hold whose outline contains it, or lies within
        // the annotation error of it: the same rule the editor uses for a finger.
        val hits = HashMap<Long, Int>()
        val found = HashSet<Truth>()
        for (t in truth) {
            val hit = HitTest.find(holds, t.x, t.y, margin * image.width, image.width.toFloat(), image.height.toFloat()) ?: continue
            found += t
            hits[hit.id] = (hits[hit.id] ?: 0) + 1
        }
        run {
            // Picture for inspection, in build/quality: found holds in green, false positives in
            // red, missed holds as crosses.
            val source = ImageIO.read(File("../fotos-referencia/$photo.jpg"))
            val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
            val g = out.createGraphics()
            g.drawImage(source, 0, 0, image.width, image.height, null)
            g.stroke = java.awt.BasicStroke(1.6f)
            for (hold in holds) {
                g.color = if (hold.id in hits) java.awt.Color(0, 255, 0) else java.awt.Color(255, 0, 0)
                g.drawPolygon(hold.contour.map { (it.x * image.width).roundToInt() }.toIntArray(), hold.contour.map { (it.y * image.height).roundToInt() }.toIntArray(), hold.contour.size)
            }
            g.color = java.awt.Color(255, 0, 255)
            for (t in truth) if (t !in found) {
                val x = (t.x * image.width).roundToInt(); val y = (t.y * image.height).roundToInt()
                g.drawLine(x - 5, y - 5, x + 5, y + 5); g.drawLine(x - 5, y + 5, x + 5, y - 5)
            }
            g.dispose()
            File("build/quality/$stage").mkdirs()
            ImageIO.write(out, "png", File("build/quality/$stage/$photo.png"))
        }
        return Score(photo, truth, found, holds.size, holds.count { it.id !in hits }, hits.values.count { it > 1 })
    }

    /** Photos the detector was adjusted with. */
    private val tuning = listOf(
        "pared-volumen-negro-amarillas",
        "pared-panel-negro-turquesas",
        "pared-triangulos-varios-colores",
        "pared-presas-rojas-grandes",
        "panel-negro-presas-blancas",
        "pared-macros-amarillas-azul",
    )

    /** Photos kept aside: nothing in the detector was adjusted while looking at them. */
    private val heldOut = listOf(
        "panel-negro-macros-colores",
        "pared-triangulos-negros-rojas",
        "pared-apaisada-cubos-rojos",
    )

    // Measured once for all the tests of a class: with the model each photo takes seconds.
    private val scores: List<Score> get() = measured.getOrPut(stage) { tuning.map { score(it) } }
    private val heldOutScores: List<Score> get() = measured.getOrPut("$stage/held out") { heldOut.map { score(it) } }

    private fun percent(value: Float) = "${(value * 100).roundToInt()}%".padStart(4)

    @Test
    fun report() {
        println("QUALITY $stage: photo | holds found | by kind (color gris claro negro volumen) | detected | false positives | boxes with several holds")
        for ((name, set) in listOf("total" to scores, "held out" to heldOutScores)) {
            for (s in set) {
                val kinds = kinds.joinToString(" ") { percent(s.recall(it)) }
                println("QUALITY $stage: ${s.photo}: ${s.found.size}/${s.truth.size} ${percent(s.recall())} | $kinds | ${s.detected} | ${s.falsePositives} | ${s.merged}")
            }
            val byKind = kinds.joinToString(" ") { kind ->
                val wanted = set.flatMap { s -> s.truth.filter { it.kind == kind }.map { it in s.found } }
                "${wanted.count { it }}/${wanted.size}"
            }
            println("QUALITY $stage: $name: ${found(set)}/${holds(set)} ${percent(recall(set))} | $byKind | false positives ${set.sumOf { it.falsePositives }} of ${set.sumOf { it.detected }} detected")
        }
    }

    private val kinds = listOf("color", "gris", "claro", "negro", "volumen")

    private fun holds(set: List<Score>) = set.sumOf { it.truth.size }
    private fun found(set: List<Score>) = set.sumOf { it.found.size }
    private fun recall(set: List<Score>) = found(set).toFloat() / holds(set)

    @Test
    fun colouredHoldsAreMostlyFound() {
        for (s in scores) assertTrue(s.recall("color") >= minColourRecall, "${s.photo}: colour recall ${s.recall("color")}")
    }

    @Test
    fun overallRecallDoesNotDrop() {
        assertTrue(recall(scores) >= minTotalRecall, "total recall ${recall(scores)}")
    }

    @Test
    fun falsePositivesStayBounded() {
        val falsePositives = scores.sumOf { it.falsePositives }
        assertTrue(falsePositives <= maxFalsePositives, "false positives: $falsePositives")
    }

    @Test
    fun heldOutPhotosDoNotGetWorse() {
        assertTrue(recall(heldOutScores) >= minHeldOutRecall, "held out recall ${recall(heldOutScores)}")
        val falsePositives = heldOutScores.sumOf { it.falsePositives }
        assertTrue(falsePositives <= maxHeldOutFalsePositives, "held out false positives: $falsePositives")
    }

    private companion object {
        val measured = HashMap<String, List<Score>>()

        /** Long side of the image handed to the detector, as the app does. */
        const val SIDE = 1280

        // Floors and ceilings set just under and over the measured results: 193 of 230 holds
        // and 59 false positives on the photos used for adjusting, 91 of 108 and 26 on the rest.
        const val MIN_COLOUR_RECALL = 0.80f
        const val MIN_TOTAL_RECALL = 0.82f
        const val MAX_FALSE_POSITIVES = 61
        const val MIN_HELD_OUT_RECALL = 0.82f
        const val MAX_HELD_OUT_FALSE_POSITIVES = 28
    }
}
