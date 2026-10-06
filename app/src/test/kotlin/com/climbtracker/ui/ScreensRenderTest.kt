package com.climbtracker.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.climbtracker.ClimbApp
import com.climbtracker.R
import com.climbtracker.core.detection.Bounds
import com.climbtracker.core.detection.DetectedHold
import com.climbtracker.core.detection.DetectionResult
import com.climbtracker.core.detection.PointF
import com.climbtracker.core.editor.HoldRole
import com.climbtracker.core.editor.SelectedHold
import com.climbtracker.core.tracker.AttemptResult
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws the main screens with made-up data, in the light and the dark theme and in both languages. The pictures are
 * left in `app/build/screens/` to be looked at after any change to the look; the test itself
 * only checks that the screens can be drawn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ClimbApp::class, qualifiers = "es-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: ClimbApp get() = ApplicationProvider.getApplicationContext()

    private fun hold(x: Float, y: Float, argb: Int) = DetectedHold(
        contour = listOf(PointF(x, y), PointF(x + 0.06f, y + 0.01f), PointF(x + 0.07f, y + 0.05f), PointF(x + 0.01f, y + 0.06f)),
        bounds = Bounds(x, y, x + 0.07f, y + 0.06f), center = PointF(x + 0.03f, y + 0.03f),
        argb = argb, colorGroup = if (argb == YELLOW) 0 else 1, area = 100,
    )

    @Before
    fun seed() = runBlocking {
        val photo = File(app.filesDir, "walls/pared.jpg").apply { parentFile!!.mkdirs() }
        File("../fotos-referencia/pared-triangulos-varios-colores.jpg").copyTo(photo, overwrite = true)
        val repo = app.repository
        val wall = repo.createWall(
            photo.path, 960, 1280,
            DetectionResult(listOf(hold(0.3f, 0.8f, YELLOW), hold(0.4f, 0.5f, YELLOW), hold(0.5f, 0.2f, YELLOW), hold(0.6f, 0.7f, BLUE), hold(0.7f, 0.3f, BLUE)), emptyList()),
        )
        repo.assignGym(wall, "Roca Viva")
        val holds = repo.holds(wall)
        val yellow = repo.saveBoulder(
            null, wall, "Amarillo", "6A",
            mapOf(holds[0].id to SelectedHold(HoldRole.START, 0), holds[1].id to SelectedHold(HoldRole.NORMAL, 1), holds[2].id to SelectedHold(HoldRole.TOP, 2)),
        )
        repo.addAttempt(yellow, AttemptResult.FAIL, holds[1].id)
        val blue = repo.saveBoulder(
            null, wall, "Azul", "5+",
            mapOf(holds[3].id to SelectedHold(HoldRole.START, 0), holds[4].id to SelectedHold(HoldRole.TOP, 1)),
        )
        repo.addAttempt(blue, AttemptResult.SEND, holds[4].id)
        Unit
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val dir = File("build/screens").apply { mkdirs() }
        val picture = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun draw(dark: Boolean, suffix: String) {
        compose.setContent { ClimbTheme(dark = dark) { MainTabs(onNew = {}, onOpen = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Amarillo")).fetchSemanticsNodes().isNotEmpty() }
        save("proyectos-$suffix")
        compose.onNodeWithText(app.getString(R.string.tab_profile)).performClick()
        save("perfil-$suffix")
    }

    @Test
    fun drawsTheMainScreensInTheLightTheme() = draw(dark = false, "claro")

    @Test
    fun drawsTheMainScreensInTheDarkTheme() = draw(dark = true, "oscuro")

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun drawsTheMainScreensInEnglish() = draw(dark = false, "ingles")

    private companion object {
        const val YELLOW = 0xFFE8C020.toInt()
        const val BLUE = 0xFF2060D0.toInt()
    }
}
