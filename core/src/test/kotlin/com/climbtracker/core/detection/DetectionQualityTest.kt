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
 */
open class DetectionQualityTest {

    /** What is measured, for the report and the pictures. */
    protected open val stage: String get() = "detector"

    /** Step applied to what the detector finds before measuring it; none here. */
    protected open fun refine(image: PixelImage, holds: List<DetectedHold>): List<DetectedHold> = holds

    protected open val minColourRecall: Float get() = MIN_COLOUR_RECALL
    protected open val minTotalRecall: Float get() = MIN_TOTAL_RECALL
    protected open val maxFalsePositives: Int get() = MAX_FALSE_POSITIVES

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
        val holds = refine(image, ColorHoldDetector().detect(image, 0.5f).holds)
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
            ImageIO.write(out.getSubimage(0, 0, image.width, (image.height * 0.72).toInt()), "png", File("build/quality/$stage/$photo.png"))
        }
        return Score(photo, truth, found, holds.size, holds.count { it.id !in hits }, hits.values.count { it > 1 })
    }

    private val photos = listOf(
        "pared-volumen-negro-amarillas",
        "pared-panel-negro-turquesas",
        "pared-triangulos-varios-colores",
    )

    // Measured once for all the tests of a class: with the model each photo takes seconds.
    private val scores: List<Score> get() = measured.getOrPut(stage) { photos.map { score(it) } }

    private fun percent(value: Float) = "${(value * 100).roundToInt()}%".padStart(4)

    @Test
    fun report() {
        println("QUALITY $stage: photo | holds found | by kind (color gris claro negro volumen) | detected | false positives | boxes with several holds")
        for (s in scores) {
            val kinds = listOf("color", "gris", "claro", "negro", "volumen").joinToString(" ") { percent(s.recall(it)) }
            println("QUALITY $stage: ${s.photo}: ${s.found.size}/${s.truth.size} ${percent(s.recall())} | $kinds | ${s.detected} | ${s.falsePositives} | ${s.merged}")
        }
        val all = scores.flatMap { it.truth }.size
        val found = scores.sumOf { it.found.size }
        println("QUALITY $stage: total: $found/$all ${percent(found.toFloat() / all)} | false positives ${scores.sumOf { it.falsePositives }} of ${scores.sumOf { it.detected }} detected")
    }

    @Test
    fun colouredHoldsAreMostlyFound() {
        for (s in scores) assertTrue(s.recall("color") >= minColourRecall, "${s.photo}: colour recall ${s.recall("color")}")
    }

    @Test
    fun overallRecallDoesNotDrop() {
        val all = scores.flatMap { it.truth }.size
        val found = scores.sumOf { it.found.size }
        assertTrue(found.toFloat() / all >= minTotalRecall, "total recall ${found.toFloat() / all}")
    }

    @Test
    fun falsePositivesStayBounded() {
        val falsePositives = scores.sumOf { it.falsePositives }
        assertTrue(falsePositives <= maxFalsePositives, "false positives: $falsePositives")
    }

    private companion object {
        val measured = HashMap<String, List<Score>>()

        /** Long side of the image handed to the detector, as the app does. */
        const val SIDE = 1280

        // Floors and ceiling set just under and over the measured results.
        const val MIN_COLOUR_RECALL = 0.80f
        const val MIN_TOTAL_RECALL = 0.87f
        const val MAX_FALSE_POSITIVES = 28
    }
}
