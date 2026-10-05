package com.climbtracker.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.climbtracker.core.detection.PointF
import com.climbtracker.core.editor.EditorHold
import com.climbtracker.core.editor.HoldRole
import com.climbtracker.core.editor.SelectedHold
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Taps on the wall photo. Regression test for saved walls not opening from the new wall screen:
 * the photo swallowed every tap, so a parent that made it clickable never heard of them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallPreviewTapTest {

    @get:Rule
    val compose = createComposeRule()

    /** A wide photo (4:1), so its height tells a loaded photo from the 4:3 placeholder. */
    private fun photo(): String {
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File(context.cacheDir, "wall-test.jpg")
        val bitmap = Bitmap.createBitmap(200, 50, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file.path
    }

    private fun waitForPhoto() {
        compose.waitUntil(10_000) {
            val bounds = compose.onNodeWithTag("wall").fetchSemanticsNode().boundsInRoot
            bounds.height > 0f && bounds.width / bounds.height > 3f
        }
    }

    private val hold = EditorHold(
        7L,
        listOf(PointF(0.3f, 0.1f), PointF(0.7f, 0.1f), PointF(0.7f, 0.9f), PointF(0.3f, 0.9f)),
        0,
        0xFFE8C020.toInt(),
    )

    @Test
    fun clickableParentHearsTapsWhenNoHoldCanBePicked() {
        val path = photo()
        var clicks = 0
        compose.setContent {
            WallPreview(path, emptyList(), emptyMap(), Modifier.width(200.dp).testTag("wall").clickable { clicks++ })
        }
        waitForPhoto()
        compose.onNodeWithTag("wall").performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun tappedHoldIsReportedWhilePicking() {
        val path = photo()
        var tapped = -1L
        compose.setContent {
            WallPreview(
                path, listOf(hold), mapOf(hold.id to SelectedHold(HoldRole.NORMAL, 0)),
                Modifier.width(200.dp).testTag("wall"),
                onHoldTap = { tapped = it },
            )
        }
        waitForPhoto()
        compose.onNodeWithTag("wall").performClick()
        compose.waitForIdle()
        assertEquals(7L, tapped)
    }
}
