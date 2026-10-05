package com.climbtracker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.climbtracker.core.editor.EditorHold
import com.climbtracker.core.editor.SelectedHold
import com.climbtracker.core.tracker.AttemptResult
import com.climbtracker.core.tracker.BoulderStatus
import com.climbtracker.core.tracker.Tracker
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

/** The words on the picture shared for a boulder. */
data class ShareCard(
    val date: String,
    val gym: String?,
    /** The big word: how it went. */
    val headline: String,
    /** A line under the headline, when it needs explaining. */
    val tagline: String?,
    val colour: Int,
    val title: String,
    val attempts: String,
)

/** How to place a photo so that it fills a card. */
class Cover(val scale: Float, val dx: Float, val dy: Float)

object ShareCards {
    private val DAY = DateTimeFormatter.ofPattern("d 'DE' MMMM", Locale.forLanguageTag("es-ES"))

    /**
     * [attempts] are the date and result of each one, oldest first. The date shown is that of the
     * send, or of the last attempt, or failing those the day the boulder was saved.
     */
    fun build(
        name: String,
        grade: String,
        gym: String?,
        colour: Int,
        best: Float,
        createdAt: Long,
        attempts: List<Pair<Long, AttemptResult>>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ShareCard {
        val status = Tracker.statusOf(attempts.map { it.second })
        val sendIndex = attempts.indexOfFirst { it.second == AttemptResult.SEND }
        val moment = when {
            sendIndex >= 0 -> attempts[sendIndex].first
            attempts.isNotEmpty() -> attempts.last().first
            else -> createdAt
        }
        return ShareCard(
            date = DAY.format(Instant.ofEpochMilli(moment).atZone(zone)).uppercase(Locale.forLanguageTag("es-ES")),
            gym = gym?.takeIf { it.isNotBlank() },
            headline = when (status) {
                BoulderStatus.FLASH -> "FLASH"
                BoulderStatus.SENT -> "ENCADENADO"
                BoulderStatus.PROJECT -> "${(best * 100).roundToInt()} %"
            },
            tagline = if (status == BoulderStatus.PROJECT) "EN PROYECTO" else null,
            colour = colour,
            title = listOf(name, grade).filter { it.isNotBlank() }.joinToString(" \u00b7 "),
            attempts = if (sendIndex >= 0) {
                "${ordinal(sendIndex + 1)} intento"
            } else {
                "${attempts.size} ${if (attempts.size == 1) "intento" else "intentos"}"
            },
        )
    }

    /**
     * Scale and offset that make a photo fill the card, cutting off what does not fit, with the
     * point ([focusX], [focusY]) of the photo (0..1) as near as possible to ([atX], [atY]) of the card.
     */
    fun cover(
        photoWidth: Float,
        photoHeight: Float,
        cardWidth: Float,
        cardHeight: Float,
        focusX: Float,
        focusY: Float,
        atX: Float = 0.5f,
        atY: Float = 0.5f,
    ): Cover {
        val scale = maxOf(cardWidth / photoWidth, cardHeight / photoHeight)
        val dx = (cardWidth * atX - focusX * photoWidth * scale).coerceIn(cardWidth - photoWidth * scale, 0f)
        val dy = (cardHeight * atY - focusY * photoHeight * scale).coerceIn(cardHeight - photoHeight * scale, 0f)
        return Cover(scale, dx, dy)
    }
}

private const val CARD_WIDTH = 1080
private const val CARD_HEIGHT = 1920
private const val MARGIN = 84f

/** Where on the card the circuit is centred: above the words. */
private const val CIRCUIT_AT = 0.36f

/**
 * Picture of a boulder for sharing, in the tall format of a story: [photo] filling the card, with
 * the circuit outlined when [holds] are given, and [card] written over its lower half.
 */
fun renderShareCard(
    photo: Bitmap,
    holds: List<EditorHold>,
    selection: Map<Long, SelectedHold>,
    card: ShareCard,
    centred: Boolean,
): Bitmap {
    val layer = if (holds.isEmpty()) photo else withCircuit(photo, holds, selection)
    val points = holds.filter { it.id in selection }.flatMap { it.contour }
    val focusX = if (points.isEmpty()) 0.5f else points.map { it.x }.average().toFloat()
    val focusY = if (points.isEmpty()) 0.5f else points.map { it.y }.average().toFloat()
    val cover = ShareCards.cover(
        layer.width.toFloat(), layer.height.toFloat(), CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(),
        focusX, focusY, atY = if (points.isEmpty()) 0.5f else CIRCUIT_AT,
    )

    val out = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val matrix = Matrix().apply {
        postScale(cover.scale, cover.scale)
        postTranslate(cover.dx, cover.dy)
    }
    canvas.drawBitmap(layer, matrix, Paint(Paint.FILTER_BITMAP_FLAG))

    val heavy = heavyTypeface()
    val regular = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    val usable = CARD_WIDTH - 2 * MARGIN
    fun paint(size: Float, face: Typeface, colour: Int = Color.WHITE, spacing: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = face
        color = colour
        letterSpacing = spacing
        setShadowLayer(10f, 0f, 3f, 0x99000000.toInt())
    }

    // The words are stacked from the bottom up, so they take no more of the photo than they need.
    val brandY = CARD_HEIGHT - 120f
    val rowY = brandY - 170f
    val taglineY = rowY - 105f
    val headline = paint(300f, heavy)
    while (headline.measureText(card.headline) > usable && headline.textSize > 60f) headline.textSize -= 4f
    val headlineY = if (card.tagline != null) taglineY - 75f else rowY - 105f
    val gymY = headlineY - headline.textSize * 0.8f - 28f
    val dateY = if (card.gym != null) gymY - 78f else gymY

    // The photo fades to dark behind the words so they read on any photo.
    val shadeTop = dateY - 320f
    val shade = Paint().apply {
        shader = LinearGradient(
            0f, shadeTop, 0f, CARD_HEIGHT.toFloat(),
            intArrayOf(Color.TRANSPARENT, 0xB3000000.toInt(), 0xF2000000.toInt()),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, shadeTop, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), shade)

    /** Draws [text] at baseline [y], against the left margin or centred. */
    fun line(text: String, y: Float, paint: Paint) {
        val x = if (centred) (CARD_WIDTH - paint.measureText(text)) / 2 else MARGIN
        canvas.drawText(text, x, y, paint)
    }

    line(card.date, dateY, paint(36f, regular, 0xCCFFFFFF.toInt(), spacing = 0.18f))
    if (card.gym != null) {
        val gym = paint(58f, regular)
        while (gym.measureText(card.gym) > usable && gym.textSize > 30f) gym.textSize -= 2f
        line(card.gym, gymY, gym)
    }
    line(card.headline, headlineY, headline)
    if (card.tagline != null) line(card.tagline, taglineY, paint(46f, heavy, Terracotta.toArgb().brighter(), spacing = 0.2f))

    // One row: the colour of the circuit, what it is called, and how many attempts it took.
    val y = rowY
    val title = paint(50f, regular)
    val attempts = paint(50f, heavy)
    val dot = 34f
    val gap = 26f
    val rowWidth = dot * 2 + gap + title.measureText(card.title) + gap * 2 + attempts.measureText(card.attempts)
    var x = if (centred) ((CARD_WIDTH - rowWidth) / 2).coerceAtLeast(MARGIN) else MARGIN
    canvas.drawCircle(x + dot, y - 17f, dot, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    canvas.drawCircle(x + dot, y - 17f, dot - 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card.colour })
    x += dot * 2 + gap
    canvas.drawText(card.title, x, y, title)
    x += title.measureText(card.title) + gap * 2
    canvas.drawText(card.attempts, x, y, attempts)

    // The app's name, in its two colours.
    val first = "Climb"
    val second = "Tracker"
    val brand = paint(84f, heavy)
    val accent = paint(84f, heavy, Terracotta.toArgb().brighter())
    val brandX = if (centred) (CARD_WIDTH - brand.measureText(first + second)) / 2 else MARGIN
    canvas.drawText(first, brandX, brandY, brand)
    canvas.drawText(second, brandX + brand.measureText(first), brandY, accent)
    return out
}

/** The system font at its heaviest: plain "bold" comes out thin with some phones' fonts. */
private fun heavyTypeface(): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, 800, false) else Typeface.DEFAULT_BOLD

/** The terracotta of the app, lifted so it reads on a dark photo. */
private fun Int.brighter(): Int = Color.rgb(
    (Color.red(this) * 1.25f).roundToInt().coerceAtMost(255),
    (Color.green(this) * 1.25f).roundToInt().coerceAtMost(255),
    (Color.blue(this) * 1.25f).roundToInt().coerceAtMost(255),
)

/** [photo] with the circuit outlined as on screen, the rest only slightly darkened. */
private fun withCircuit(photo: Bitmap, holds: List<EditorHold>, selection: Map<Long, SelectedHold>): Bitmap {
    val out = ImageBitmap(photo.width, photo.height)
    val size = Size(photo.width.toFloat(), photo.height.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, ComposeCanvas(out), size) {
        drawCircuit(photo.asImageBitmap(), holds, selection, showUnselected = false, stroke = photo.width / 400f, labels = false, dim = 0.3f)
    }
    return out.asAndroidBitmap()
}
