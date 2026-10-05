package com.climbtracker.ui

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.climbtracker.ClimbApp
import com.climbtracker.core.editor.EditorHold
import com.climbtracker.core.editor.HitTest
import com.climbtracker.core.editor.HoldRole
import com.climbtracker.core.editor.SelectedHold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PILL_BACKGROUND = 0xE61C1917.toInt()

/**
 * Draws the wall photo over [area] (from the origin), darkens everything outside the circuit and
 * outlines the holds. [stroke] is the base line width in pixels. When [progressY] is given
 * (normalised height), the photo below it is tinted with [tint] and a line marks the height reached.
 * [paths] are outlines built beforehand with [holdPaths] for this same [area]. [dim] is how much
 * the rest of the photo is darkened.
 */
fun DrawScope.drawCircuit(
    image: ImageBitmap,
    holds: List<EditorHold>,
    selection: Map<Long, SelectedHold>,
    showUnselected: Boolean,
    stroke: Float,
    area: Size = size,
    labels: Boolean = true,
    progressY: Float? = null,
    tint: Color = Color.Transparent,
    paths: Map<Long, Path>? = null,
    dim: Float = 0.6f,
) {
    drawImage(image, dstSize = IntSize(area.width.roundToInt(), area.height.roundToInt()))
    val outlines = paths ?: holdPaths(holds, area, stroke)

    if (selection.isNotEmpty()) {
        // Everything but the circuit is darkened. The holds are cut out as a single region, so
        // two selected holds that overlap stay bright where they do.
        val circuit = Path()
        for (hold in holds) if (hold.id in selection) circuit.addPath(outlines.getValue(hold.id))
        clipPath(circuit, ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = dim), Offset.Zero, area)
        }
    }

    if (progressY != null) {
        val y = progressY.coerceIn(0f, 1f) * area.height
        drawRect(tint.copy(alpha = 0.3f), Offset(0f, y), Size(area.width, area.height - y))
        drawLine(Color.White, Offset(0f, y), Offset(area.width, y), strokeWidth = stroke * 1.5f)
    }

    for (hold in holds) {
        val path = outlines.getValue(hold.id)
        val role = selection[hold.id]?.role
        // A thick line would cover a small hold altogether, so it thins with the hold.
        val widest = if (role == null) stroke else path.getBounds().let { min(it.width, it.height) * MAX_OUTLINE }.coerceAtLeast(stroke * 0.8f)
        when (role) {
            null -> if (showUnselected) drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(stroke))
            HoldRole.NORMAL -> drawPath(path, Color.White, style = Stroke(min(stroke * 2.5f, widest)))
            HoldRole.START, HoldRole.TOP -> drawPath(path, Color.White, style = Stroke(min(stroke * 3.5f, widest * 1.3f)))
            // Dashed: part of the circuit, but only for the feet.
            HoldRole.FOOT -> drawPath(
                path, Color.White,
                style = Stroke(min(stroke * 2.5f, widest), pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 5f, stroke * 4f))),
            )
        }
    }
    if (labels) {
        // Drawn last so no outline crosses a label.
        for (hold in holds) {
            when (selection[hold.id]?.role) {
                HoldRole.START -> drawPillLabel("START", hold, area, stroke, above = false)
                HoldRole.TOP -> drawPillLabel("TOP", hold, area, stroke, above = true)
                else -> Unit
            }
        }
    }
}

/** Widest outline of a selected hold, as a share of its shorter side. */
private const val MAX_OUTLINE = 0.2f

/**
 * Outline of every hold for a photo drawn over [area]. Pass the result to [drawCircuit] to
 * avoid rebuilding it on each frame; [stroke] only sizes the dot of a contour-less hold.
 */
fun holdPaths(holds: List<EditorHold>, area: Size, stroke: Float): Map<Long, Path> =
    holds.associate { it.id to holdPath(it, area, stroke) }

private fun holdPath(hold: EditorHold, area: Size, stroke: Float): Path = Path().apply {
    val contour = hold.contour
    if (contour.size >= 3) {
        moveTo(contour[0].x * area.width, contour[0].y * area.height)
        for (i in 1 until contour.size) lineTo(contour[i].x * area.width, contour[i].y * area.height)
        close()
    } else if (contour.isNotEmpty()) {
        addOval(Rect(Offset(contour[0].x * area.width, contour[0].y * area.height), stroke * 6f))
    }
}

/** Dark rounded label next to a hold: above it for the top, below it for the start. */
private fun DrawScope.drawPillLabel(text: String, hold: EditorHold, area: Size, stroke: Float, above: Boolean) {
    if (hold.contour.isEmpty()) return
    val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = stroke * 11f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    val background = Paint().apply {
        color = PILL_BACKGROUND
        isAntiAlias = true
    }
    val height = stroke * 18f
    val half = textPaint.measureText(text) / 2f + stroke * 7f
    val gap = stroke * 6f
    val cx = (hold.contour.map { it.x }.average().toFloat() * area.width).coerceIn(half, max(half, area.width - half))
    val over = hold.contour.minOf { it.y } * area.height - gap - height / 2f
    val under = hold.contour.maxOf { it.y } * area.height + gap + height / 2f
    // Flips to the other side when the preferred one would fall outside the photo.
    val cy = if (above) {
        if (over - height / 2f < 0f) under else over
    } else {
        if (under + height / 2f > area.height) over else under
    }
    val canvas = drawContext.canvas.nativeCanvas
    canvas.drawRoundRect(cx - half, cy - height / 2f, cx + half, cy + height / 2f, height / 2f, height / 2f, background)
    canvas.drawText(text, cx, cy - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
}

private class Loaded(val bitmap: Bitmap?)

/**
 * Wall photo with a circuit highlighted. Shows a placeholder when the photo file is missing.
 * With [cropToFill] the photo fills whatever size [modifier] gives it; otherwise the composable
 * takes the photo's aspect ratio. [onHoldTap] receives the id of a tapped circuit hold.
 */
@Composable
fun WallPreview(
    photoPath: String?,
    holds: List<EditorHold>,
    selection: Map<Long, SelectedHold>,
    modifier: Modifier = Modifier,
    maxSide: Int = 1024,
    cropToFill: Boolean = false,
    labels: Boolean = true,
    progressY: Float? = null,
    tint: Color = Color.Transparent,
    onHoldTap: ((Long) -> Unit)? = null,
) {
    val photos = (LocalContext.current.applicationContext as ClimbApp).photos
    val loaded by produceState<Loaded?>(initialValue = null, photoPath, maxSide) {
        value = Loaded(photoPath?.let { path -> withContext(Dispatchers.IO) { photos.load(path, maxSide) } })
    }
    val result = loaded
    val bitmap = result?.bitmap
    if (bitmap == null) {
        Box(
            (if (cropToFill) modifier else modifier.aspectRatio(4f / 3f)).background(Color(0xFF2A2725)),
            contentAlignment = Alignment.Center,
        ) {
            // result == null means still loading; an empty box avoids a flash of the error text.
            if (result != null) Text("Imagen no disponible", color = Color.White)
        }
        return
    }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    if (cropToFill) {
        Canvas(modifier.clipToBounds()) {
            val scale = max(size.width / bitmap.width, size.height / bitmap.height)
            val area = Size(bitmap.width * scale, bitmap.height * scale)
            translate((size.width - area.width) / 2f, (size.height - area.height) / 2f) {
                // Thinner outlines: these are small thumbnails.
                drawCircuit(image, holds, selection, false, 0.5.dp.toPx(), area, labels, progressY, tint)
            }
        }
        return
    }
    val tap by rememberUpdatedState(onHoldTap)
    val currentHolds by rememberUpdatedState(holds)
    // Taps are only listened for while a hold can be picked. A tap detector consumes every tap,
    // so attaching it always would swallow the clicks of a parent that makes the photo clickable.
    val picking = if (onHoldTap == null) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            detectTapGestures { point ->
                val handler = tap ?: return@detectTapGestures
                val hit = HitTest.find(
                    currentHolds,
                    point.x / size.width, point.y / size.height,
                    24.dp.toPx(), size.width.toFloat(), size.height.toFloat(),
                )
                if (hit != null) handler(hit.id)
            }
        }
    }
    Canvas(modifier.aspectRatio(bitmap.width.toFloat() / bitmap.height).then(picking)) {
        drawCircuit(image, holds, selection, false, 1.dp.toPx(), size, labels, progressY, tint)
    }
}
