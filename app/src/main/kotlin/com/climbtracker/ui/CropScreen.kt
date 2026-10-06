package com.climbtracker.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.climbtracker.ClimbApp
import com.climbtracker.core.image.CropHandle
import com.climbtracker.core.image.CropQuad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.climbtracker.R

class CropViewModel(app: Application) : AndroidViewModel(app) {
    private val climb = app as ClimbApp

    var bitmap by mutableStateOf<Bitmap?>(null)
        private set
    var busy by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            bitmap = withContext(Dispatchers.IO) { climb.photos.imported(1280) }
            if (bitmap == null) error = climb.getString(R.string.photo_unreadable)
            busy = false
        }
    }

    /** Crops the photo, detects its holds and stores the wall. [onDone] receives the wall id. */
    fun confirm(crop: CropQuad, onDone: (Long) -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            error = null
            // Looked up while the photo is analysed, so it adds no waiting.
            val fix = async { climb.locator.current() }
            val wallId = withContext(Dispatchers.Default) {
                val path = climb.photos.cropAndSave(crop) ?: return@withContext null
                var created = false
                try {
                    val photo = climb.photos.load(path, 1280) ?: return@withContext null
                    val pixels = climb.photos.pixels(photo)
                    val detection = climb.detector.detect(pixels, 0.5f)
                    climb.repository.createWall(path, pixels.width, pixels.height, detection).also { wallId ->
                        created = true
                        // The gym the phone is at; failing that, most likely that of the previous wall.
                        val near = fix.await()?.let { climb.repository.gymNear(it.point, it.accuracy) }
                        (near?.name ?: climb.prefs.lastGym)?.let { climb.repository.assignGym(wallId, it) }
                    }
                } finally {
                    // Leaving the screen cancels this block. Without a wall, nothing would ever
                    // reference the cropped photo, so it is removed.
                    if (!created) climb.photos.delete(path)
                }
            }
            busy = false
            if (wallId != null) onDone(wallId) else error = climb.getString(R.string.photo_failed)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(onDone: (Long) -> Unit, onBack: () -> Unit, vm: CropViewModel = viewModel()) {
    var crop by remember { mutableStateOf(CropQuad.FULL) }
    // Off, the corners stay square and the photo is only cropped. On, each corner moves alone,
    // to mark a wall photographed from one side or from below, which is then straightened.
    var perspective by remember { mutableStateOf(false) }
    val bitmap = vm.bitmap

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crop_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            perspective = !perspective
                            if (!perspective) crop = CropQuad.of(crop.bounds().normalized())
                        },
                        enabled = !vm.busy && bitmap != null,
                    ) {
                        Text(ticked(stringResource(R.string.perspective), perspective))
                    }
                    TextButton(onClick = { vm.confirm(crop, onDone) }, enabled = !vm.busy && bitmap != null) {
                        Text(stringResource(R.string.detect))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.padding(padding).fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) CropCanvas(bitmap, crop, perspective, onChange = { crop = it })
            if (perspective && !vm.busy) {
                Text(
                    stringResource(R.string.perspective_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                )
            }
            if (vm.busy) Spinner()
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp)) }
        }
    }
}

@Composable
private fun CropCanvas(bitmap: Bitmap, crop: CropQuad, perspective: Boolean, onChange: (CropQuad) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val current by rememberUpdatedState(crop)
    val free by rememberUpdatedState(perspective)
    val reach = with(LocalDensity.current) { 40.dp.toPx() }
    val line = with(LocalDensity.current) { 2.dp.toPx() }

    Canvas(
        Modifier
            .padding(28.dp)
            .aspectRatio(bitmap.width.toFloat() / bitmap.height)
            // The corner handles sit near the screen edge, where a drag would otherwise be
            // taken by the system back gesture.
            .systemGestureExclusion()
            .pointerInput(Unit) {
                var grab: CropHandle? = null
                // Accumulated here, not read back from the state: several drag events can arrive
                // before the next recomposition and each must build on the previous one.
                var working = current
                detectDragGestures(
                    onDragStart = { point ->
                        working = current
                        grab = grabAt(working, point, size.width.toFloat(), size.height.toFloat(), reach)
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        grab?.let { handle ->
                            val dx = drag.x / size.width
                            val dy = drag.y / size.height
                            working = if (free) {
                                working.dragged(handle, dx, dy)
                            } else {
                                CropQuad.of((working.asRect() ?: working.bounds()).dragged(handle, dx, dy))
                            }
                            onChange(working)
                        }
                    },
                )
            },
    ) {
        drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
        val corners = crop.corners.map { Offset(it.x * size.width, it.y * size.height) }
        val shape = Path().apply {
            moveTo(corners[0].x, corners[0].y)
            for (corner in corners.drop(1)) lineTo(corner.x, corner.y)
            close()
        }
        val outside = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addPath(shape)
        }
        drawPath(outside, Color.Black.copy(alpha = 0.6f))
        drawPath(shape, Color.White, style = Stroke(line))
        for (corner in corners) drawCircle(Color.White, radius = line * 5f, center = corner)
    }
}

/** The corner within [reach] pixels of [point], else MOVE when inside the shape, else null. */
private fun grabAt(crop: CropQuad, point: Offset, width: Float, height: Float, reach: Float): CropHandle? {
    val handles = listOf(CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT, CropHandle.BOTTOM_LEFT)
    val corners = crop.corners.map { Offset(it.x * width, it.y * height) }
    val nearest = corners.indices.minBy { hypot(corners[it].x - point.x, corners[it].y - point.y) }
    if (hypot(corners[nearest].x - point.x, corners[nearest].y - point.y) <= reach) return handles[nearest]
    // Inside a shape that bulges outwards, the point is on the same side of all four edges.
    val inside = corners.indices.all { i ->
        val a = corners[i]
        val b = corners[(i + 1) % 4]
        (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x) >= 0f
    }
    return if (inside) CropHandle.MOVE else null
}
