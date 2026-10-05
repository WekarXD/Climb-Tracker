package com.climbtracker.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.climbtracker.core.detection.PointF
import com.climbtracker.core.editor.EditorHold
import com.climbtracker.core.editor.HoldRole
import com.climbtracker.core.editor.SelectedHold
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws the share card with a real photo. The pictures are left in `app/build/share/` to be
 * looked at after any change to the design.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareRenderTest {

    private fun blob(id: Long, x: Float, y: Float) = EditorHold(
        id,
        listOf(PointF(x, y), PointF(x + 0.05f, y + 0.01f), PointF(x + 0.06f, y + 0.05f), PointF(x + 0.01f, y + 0.06f)),
        0,
        0xFFE8C020.toInt(),
    )

    private val holds = listOf(blob(1, 0.70f, 0.20f), blob(2, 0.72f, 0.32f), blob(3, 0.60f, 0.48f), blob(4, 0.45f, 0.62f))
    private val selection = mapOf(
        1L to SelectedHold(HoldRole.TOP, 3), 2L to SelectedHold(HoldRole.NORMAL, 2),
        3L to SelectedHold(HoldRole.NORMAL, 1), 4L to SelectedHold(HoldRole.START, 0),
    )

    private fun save(bitmap: Bitmap, name: String) {
        val dir = File("build/share").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun drawsATallCardWhateverTheShapeOfThePhoto() {
        val photo = BitmapFactory.decodeFile("../fotos-referencia/pared-triangulos-varios-colores.jpg")
        val flash = ShareCard("5 DE OCTUBRE", "Roca Viva", "FLASH", null, 0xFFE8C020.toInt(), "Amarillo · 6A", "1.er intento")
        val sent = flash.copy(headline = "ENCADENADO", attempts = "3.er intento")
        val project = flash.copy(headline = "62 %", tagline = "EN PROYECTO", attempts = "2 intentos", gym = null)

        val left = renderShareCard(photo, holds, selection, flash, centred = false)
        assertEquals(1080, left.width)
        assertEquals(1920, left.height)
        save(left, "flash-izquierda.png")
        save(renderShareCard(photo, holds, selection, sent, centred = true), "encadenado-centrado.png")
        save(renderShareCard(photo, emptyList(), emptyMap(), project, centred = false), "proyecto-foto-propia.png")
    }
}
