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
import androidx.compose.ui.geometry.Size
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
import com.climbtracker.core.image.CropRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

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
            if (bitmap == null) error = "No se ha podido leer la foto."
            busy = false
        }
    }

    /** Crops the photo, detects its holds and stores the wall. [onDone] receives the wall id. */
    fun confirm(crop: CropRect, onDone: (Long) -> Unit) {
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
            if (wallId != null) onDone(wallId) else error = "No se ha podido procesar la foto."
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(onDone: (Long) -> Unit, onBack: () -> Unit, vm: CropViewModel = viewModel()) {
    var crop by remember { mutableStateOf(CropRect.FULL) }
    val bitmap = vm.bitmap

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Encuadra la pared") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.confirm(crop, onDone) }, enabled = !vm.busy && bitmap != null) {
                        Text("Detectar")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.padding(padding).fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) CropCanvas(bitmap, crop, onChange = { crop = it })
            if (vm.busy) Spinner()
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp)) }
        }
    }
}

@Composable
private fun CropCanvas(bitmap: Bitmap, crop: CropRect, onChange: (CropRect) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val current by rememberUpdatedState(crop)
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
                            working = working.dragged(handle, drag.x / size.width, drag.y / size.height)
                            onChange(working)
                        }
                    },
                )
            },
    ) {
        drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
        val rect = Rect(
            crop.left * size.width, crop.top * size.height,
            crop.right * size.width, crop.bottom * size.height,
        )
        val outside = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addRect(rect)
        }
        drawPath(outside, Color.Black.copy(alpha = 0.6f))
        drawRect(Color.White, rect.topLeft, Size(rect.width, rect.height), style = Stroke(line))
        for (corner in listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight)) {
            drawCircle(Color.White, radius = line * 5f, center = corner)
        }
    }
}

/** The corner within [reach] pixels of [point], else MOVE when inside the rectangle, else null. */
private fun grabAt(crop: CropRect, point: Offset, width: Float, height: Float, reach: Float): CropHandle? {
    val corners = listOf(
        CropHandle.TOP_LEFT to Offset(crop.left * width, crop.top * height),
        CropHandle.TOP_RIGHT to Offset(crop.right * width, crop.top * height),
        CropHandle.BOTTOM_LEFT to Offset(crop.left * width, crop.bottom * height),
        CropHandle.BOTTOM_RIGHT to Offset(crop.right * width, crop.bottom * height),
    )
    val nearest = corners.minBy { hypot(it.second.x - point.x, it.second.y - point.y) }
    if (hypot(nearest.second.x - point.x, nearest.second.y - point.y) <= reach) return nearest.first
    val inside = point.x in crop.left * width..crop.right * width && point.y in crop.top * height..crop.bottom * height
    return if (inside) CropHandle.MOVE else null
}
