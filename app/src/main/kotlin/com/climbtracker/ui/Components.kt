package com.climbtracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Round white button used for back and menu actions. */
@Composable
fun CircleButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = CardWhite,
        contentColor = Ink,
        border = BorderStroke(1.dp, Hairline),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Rounded filter chip; dark when selected. [description] is what a screen reader says instead of
 * the content, for chips whose content says little without seeing it.
 */
@Composable
fun Pill(selected: Boolean, onClick: () -> Unit, description: String? = null, content: @Composable () -> Unit) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Ink else CardWhite,
        contentColor = if (selected) Color.White else Ink,
        border = BorderStroke(1.dp, if (selected) Ink else Hairline),
    ) {
        Row(
            Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .then(if (description == null) Modifier else Modifier.clearAndSetSemantics { contentDescription = description }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
fun ColorDot(argb: Int, size: Dp = 14.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(argb))
            .border(1.dp, LocalContentColor.current.copy(alpha = 0.25f), CircleShape),
    )
}

/** Ring filled clockwise from the top up to [fraction], with the percentage in the middle. */
@Composable
fun ProgressRing(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = Hairline) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = size.minDimension * 0.1f
            val topLeft = Offset(width / 2, width / 2)
            val arc = Size(size.width - width, size.height - width)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(width))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft, arc, style = Stroke(width, cap = StrokeCap.Round))
        }
        Text("${(fraction * 100).roundToInt()}%", color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Green mark for a sent boulder: a filled disc on photo cards, a ring on light backgrounds. */
@Composable
fun SendBadge(text: String, filled: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .then(if (filled) Modifier.background(SendGreen) else Modifier.border(4.dp, SendGreen, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (filled) Color.White else SendGreen,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** "1.er", "2.º", "3.er", "4.º"... as used before "intento". */
fun ordinal(n: Int): String = if (n == 1 || n == 3) "$n.er" else "$n.º"

/**
 * Loading indicator: an arc that turns. It is timed by the frames themselves and not by an
 * animation, because the stock indicator stands still on phones with system animations turned
 * off, and a still indicator reads as the app having hung.
 */
@Composable
fun Spinner(modifier: Modifier = Modifier, color: Color = Terracotta, size: Dp = 40.dp, strokeWidth: Dp = 4.dp) {
    var millis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { now -> millis = (now - start) / 1_000_000L }
    }
    Canvas(modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        // One turn a second; the arc grows and shrinks over a slower beat so it looks alive.
        val turn = (millis % SPIN_MILLIS) / SPIN_MILLIS.toFloat()
        val beat = (millis % BREATH_MILLIS) / BREATH_MILLIS.toFloat()
        val sweep = 60f + 200f * (1f - abs(2f * beat - 1f))
        drawArc(
            color = color,
            startAngle = turn * 360f - 90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

private const val SPIN_MILLIS = 1000L
private const val BREATH_MILLIS = 1700L

