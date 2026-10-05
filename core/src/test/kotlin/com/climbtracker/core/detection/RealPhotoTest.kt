package com.climbtracker.core.detection

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

class RealPhotoTest {

    private val photos = File("../fotos-referencia")
    private val output = File("build/detection").apply { mkdirs() }

    private fun load(name: String): Pair<BufferedImage, PixelImage> {
        val source = ImageIO.read(File(photos, name))
        val scale = 1280.0 / max(source.width, source.height)
        val w = (source.width * scale).roundToInt()
        val h = (source.height * scale).roundToInt()
        val scaled = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = scaled.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(source, 0, 0, w, h, null)
        g.dispose()
        return scaled to PixelImage(w, h, scaled.getRGB(0, 0, w, h, null, 0, w))
    }

    private fun check(name: String, holds: IntRange) {
        val (picture, pixels) = load(name)
        val started = System.currentTimeMillis()
        val result = ColorHoldDetector().detect(pixels, 0.5f)
        val elapsed = System.currentTimeMillis() - started

        val g = picture.createGraphics()
        g.stroke = BasicStroke(1.5f)
        for (hold in result.holds) {
            g.color = Color(hold.argb).let { Color(255 - it.red, 255 - it.green, 255 - it.blue) }
            val xs = hold.contour.map { (it.x * pixels.width).roundToInt() }.toIntArray()
            val ys = hold.contour.map { (it.y * pixels.height).roundToInt() }.toIntArray()
            g.drawPolygon(xs, ys, xs.size)
        }
        g.dispose()
        ImageIO.write(picture, "png", File(output, name.substringBeforeLast('.') + ".png"))

        println("$name: ${result.holds.size} holds, ${result.groups.size} groups, $elapsed ms")
        assertTrue(result.holds.size in holds, "$name: ${result.holds.size} holds, expected $holds")
        assertTrue(result.groups.size in 3..8, "$name: ${result.groups.size} groups")
        assertTrue(result.holds.all { h -> h.contour.all { it.x in 0f..1f && it.y in 0f..1f } })
    }

    @Test
    fun yellowHoldsOnPlainWallAndOnBlackVolume() = check("pared-volumen-negro-amarillas.jpg", 20..60)

    @Test
    fun blackPanelWithTurquoiseHoldsAndPerson() = check("pared-panel-negro-turquesas.jpg", 20..65)

    @Test
    fun trianglesAndMixedColours() = check("pared-triangulos-varios-colores.jpg", 30..90)
}
