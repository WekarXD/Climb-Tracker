package com.climbtracker.ui

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.climbtracker.ClimbApp
import com.climbtracker.core.detection.Bounds
import com.climbtracker.core.detection.DetectedHold
import com.climbtracker.core.detection.PixelImage
import com.climbtracker.core.detection.PointF
import com.climbtracker.core.editor.Editor
import com.climbtracker.core.editor.EditorHold
import com.climbtracker.core.editor.EditorState
import com.climbtracker.core.editor.HitTest
import com.climbtracker.core.editor.PaletteEntry
import com.climbtracker.core.editor.SelectedHold
import com.climbtracker.core.editor.Tool
import com.climbtracker.core.tracker.ColorNames
import com.climbtracker.core.tracker.GradeScale
import com.climbtracker.core.tracker.Progress
import com.climbtracker.data.WallEntity
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val climb = app as ClimbApp
    private val wallId: Long = checkNotNull(handle["wallId"])
    private val boulderId: Long? = handle.get<Long>("boulderId")?.takeIf { it >= 0 }

    var state by mutableStateOf(EditorState(emptyList()))
        private set
    var wall by mutableStateOf<WallEntity?>(null)
        private set
    var bitmap by mutableStateOf<Bitmap?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var busy by mutableStateOf(false)
        private set

    /** True once the photo is loaded; saved boulders follow their holds to the new detection. */
    var canRedetect by mutableStateOf(false)
        private set
    var initialName by mutableStateOf("")
        private set
    var initialGrade by mutableStateOf("")
        private set

    val gradeScale: GradeScale get() = climb.prefs.gradeScale
    val isExisting: Boolean get() = boulderId != null

    private var pixels: PixelImage? = null

    /** A manual hold is being created; a second long press must not create a duplicate. */
    private var growing = false

    /** The circuit as it is stored, to tell whether leaving would lose changes. */
    private var savedSelection: Map<Long, SelectedHold> = emptyMap()

    val hasUnsavedChanges: Boolean get() = !loading && state.selection != savedSelection

    /** Next temporary id for a manual hold not stored yet; real ids are positive. */
    private var nextManualId = -1L

    val lastSensitivity: Float get() = climb.prefs.lastSensitivity

    init {
        viewModelScope.launch {
            val loadedWall = climb.repository.wall(wallId)
            val photo = loadedWall?.let { withContext(Dispatchers.IO) { climb.photos.load(it.photoPath, 2048) } }
            pixels = photo?.let { withContext(Dispatchers.Default) { climb.photos.pixels(it) } }
            val selection = boulderId?.let { climb.repository.selection(it) }.orEmpty()
            boulderId?.let { id ->
                climb.repository.boulderOnce(id)?.let {
                    initialName = it.name
                    initialGrade = it.grade
                }
            }
            // Clears manual holds that earlier versions stored and no boulder ended up using.
            climb.repository.removeUnusedManualHolds(wallId)
            wall = loadedWall
            bitmap = photo
            state = EditorState(
                holds = climb.repository.holds(wallId),
                selection = selection,
                nextOrder = (selection.values.maxOfOrNull { it.order } ?: -1) + 1,
            )
            canRedetect = photo != null
            savedSelection = selection
            loading = false
        }
    }

    fun setTool(tool: Tool) {
        state = Editor.setTool(state, tool)
    }

    fun togglePalette(group: Int) {
        if (busy) return
        state = Editor.toggleColorGroup(state, group)
    }

    fun undo() {
        if (busy) return
        state = Editor.undo(state)
    }

    /** [x], [y] normalised to the image; [tolerance] in pixels of the detection image. */
    fun tap(x: Float, y: Float, tolerance: Float) {
        if (busy) return
        val current = wall ?: return
        val hit = HitTest.find(state.holds, x, y, tolerance, current.width.toFloat(), current.height.toFloat())
        if (hit != null) state = Editor.tap(state, hit.id)
    }

    /** Toggles the hold under the finger, or creates a manual hold where there is none. */
    fun longPress(x: Float, y: Float, tolerance: Float) {
        if (busy || growing) return
        val current = wall ?: return
        val hit = HitTest.find(state.holds, x, y, tolerance, current.width.toFloat(), current.height.toFloat())
        if (hit != null) {
            state = Editor.toggleHold(state, hit.id)
            return
        }
        if (x !in 0f..1f || y !in 0f..1f) return
        growing = true
        viewModelScope.launch {
            val image = pixels
            val grown = image?.let {
                withContext(Dispatchers.Default) {
                    climb.detector.growRegion(
                        it,
                        (x * it.width).toInt().coerceIn(0, it.width - 1),
                        (y * it.height).toInt().coerceIn(0, it.height - 1),
                    )
                }
            }
            val hold = grown ?: circleHold(x, y, current, image)
            // Kept in memory under a temporary negative id; it is stored when the boulder is saved.
            val manual = EditorHold(nextManualId--, hold.contour, Editor.nearestGroup(state, hold.argb), hold.argb)
            state = Editor.addManualHold(state, manual)
            growing = false
        }
    }

    fun redetect(sensitivity: Float) {
        val image = pixels ?: return
        if (!canRedetect || busy) return
        viewModelScope.launch {
            busy = true
            climb.prefs.lastSensitivity = sensitivity
            try {
                val detection = withContext(Dispatchers.Default) { climb.detector.detect(image, sensitivity) }
                climb.repository.redetect(wallId, detection)
                // An existing boulder comes back with its saved circuit, now on the new holds.
                val selection = boulderId?.let { climb.repository.selection(it) }.orEmpty()
                savedSelection = selection
                state = EditorState(
                    holds = climb.repository.holds(wallId),
                    selection = selection,
                    nextOrder = (selection.values.maxOfOrNull { it.order } ?: -1) + 1,
                )
            } finally {
                busy = false
            }
        }
    }

    fun save(name: String, grade: String, onSaved: (Long) -> Unit) {
        // Saving while holds are being replaced would reference holds that no longer exist.
        if (state.selection.isEmpty() || busy) return
        viewModelScope.launch {
            // A boulder saved without a name is called after its colour, e.g. "Amarillo".
            val finalName = if (name.isBlank() && boulderId == null) {
                ColorNames.nameOf(Progress.circuitColor(state.holds, state.selection))
            } else {
                name
            }
            val pending = state.holds.filter { it.id < 0 }
            onSaved(climb.repository.saveBoulder(boulderId, wallId, finalName, grade, state.selection, pending))
        }
    }

    /** Fallback when region growing finds nothing usable: a fixed-radius circle at the touch point. */
    private fun circleHold(x: Float, y: Float, wall: WallEntity, image: PixelImage?): DetectedHold {
        // The same size on the photo whatever resolution the wall was detected at.
        val rx = MANUAL_RADIUS
        val ry = MANUAL_RADIUS * wall.width / wall.height
        val contour = (0 until 12).map { i ->
            val angle = i * (2.0 * Math.PI / 12)
            PointF(
                (x + rx * cos(angle).toFloat()).coerceIn(0f, 1f),
                (y + ry * sin(angle).toFloat()).coerceIn(0f, 1f),
            )
        }
        val argb = image?.let {
            val px = (x * it.width).toInt().coerceIn(0, it.width - 1)
            val py = (y * it.height).toInt().coerceIn(0, it.height - 1)
            it.argb[py * it.width + px]
        } ?: 0xFF808080.toInt()
        return DetectedHold(
            contour = contour,
            bounds = Bounds(
                (x - rx).coerceIn(0f, 1f), (y - ry).coerceIn(0f, 1f),
                (x + rx).coerceIn(0f, 1f), (y + ry).coerceIn(0f, 1f),
            ),
            center = PointF(x, y),
            argb = argb,
            colorGroup = -1,
            area = (Math.PI * rx * wall.width * ry * wall.height).toInt(),
        )
    }

    private companion object {
        /** Radius of a fallback manual hold, as a fraction of the photo width. */
        const val MANUAL_RADIUS = 0.019f
    }
}

@Composable
fun EditorScreen(onSaved: (Long) -> Unit, onBack: () -> Unit, vm: EditorViewModel = viewModel()) {
    var showSave by remember { mutableStateOf(false) }
    var showSensitivity by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    val state = vm.state
    val leave = { if (vm.hasUnsavedChanges) showDiscard = true else onBack() }
    BackHandler(enabled = vm.hasUnsavedChanges) { showDiscard = true }

    // White system bar icons while the black editor is on screen; dark again on leaving.
    val view = LocalView.current
    DisposableEffect(view) {
        val controller = (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = true
            controller?.isAppearanceLightNavigationBars = true
        }
    }

    // Full screen on black: the photo gets all the room, with the controls floating over it
    // at the top and in a panel below, or beside it with the phone on its side.
    val wide = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    EditorFrame(photo = {
        // Below the status bar: the system keeps the touches on that strip for itself.
        Box(Modifier.statusBarsPadding().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))) {
            val bitmap = vm.bitmap
            val wall = vm.wall
            when {
                vm.loading -> Spinner(Modifier.align(Alignment.Center), color = Color.White)
                bitmap == null || wall == null ->
                    Text("Imagen no disponible", color = Color.White, modifier = Modifier.align(Alignment.Center))
                else -> EditorCanvas(bitmap, state, wall.width, vm::tap, vm::longPress)
            }
            Column(Modifier.align(Alignment.TopCenter)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircleButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                    Spacer(Modifier.weight(1f))
                    if (state.canUndo && !vm.busy) {
                        CircleButton(onClick = vm::undo) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Deshacer")
                        }
                    }
                    if (vm.canRedetect && !vm.busy) {
                        CircleButton(onClick = { showSensitivity = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Sensibilidad de detección")
                        }
                    }
                }
                if (!vm.loading && vm.bitmap != null) {
                    when {
                        state.holds.isEmpty() ->
                            Banner("No se han detectado presas. Ajusta la sensibilidad o añade presas con una pulsación larga.")
                        state.holds.size > MAX_HOLDS ->
                            Banner("Se han detectado muchas presas. Prueba a bajar la sensibilidad.")
                    }
                }
            }
            if (vm.busy) Spinner(Modifier.align(Alignment.Center), color = Color.White)
        }
    }, controls = {
        Column(
            Modifier
                .background(Color(0xFF1A1715))
                .then(if (wide) Modifier.statusBarsPadding() else Modifier)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            ToolRow(state.tool, vm::setTool)
            PaletteRow(Editor.palette(state), vm::togglePalette)
            Button(
                onClick = { showSave = true },
                enabled = state.selection.isNotEmpty() && !vm.busy,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Terracotta,
                    contentColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.12f),
                    disabledContentColor = Color.White.copy(alpha = 0.45f),
                ),
            ) {
                Text("Guardar", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    })

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("¿Salir sin guardar?") },
            text = { Text("Los cambios de este circuito se perderán.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    onBack()
                }) { Text("Salir") }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Seguir editando") } },
        )
    }

    if (showSave) {
        BoulderDialog(
            title = if (vm.isExisting) "Guardar cambios" else "Guardar bloque",
            initialName = vm.initialName,
            initialGrade = vm.initialGrade,
            scale = vm.gradeScale,
            initialNotes = null,
            onConfirm = { name, grade, _ ->
                showSave = false
                vm.save(name, grade, onSaved)
            },
            onDismiss = { showSave = false },
        )
    }

    if (showSensitivity) {
        SensitivityDialog(
            initial = vm.lastSensitivity,
            hasSelection = state.selection.isNotEmpty(),
            onConfirm = { value ->
                showSensitivity = false
                vm.redetect(value)
            },
            onDismiss = { showSensitivity = false },
        )
    }
}

/**
 * Places the photo and the controls panel: the panel under the photo when the screen is tall,
 * and beside it when it is wide, where a panel underneath would leave the photo a thin strip.
 * Both stay the same two children either way, so turning the phone keeps the zoom.
 */
@Composable
private fun EditorFrame(photo: @Composable () -> Unit, controls: @Composable () -> Unit) {
    Layout(contents = listOf(photo, controls), modifier = Modifier.fillMaxSize().background(Color.Black)) { (photos, panels), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        if (width > height) {
            val panelWidth = minOf(PANEL_WIDTH.roundToPx(), width / 2)
            val panel = panels.first().measure(Constraints.fixed(panelWidth, height))
            val picture = photos.first().measure(Constraints.fixed(width - panelWidth, height))
            layout(width, height) {
                picture.place(0, 0)
                panel.place(picture.width, 0)
            }
        } else {
            val panel = panels.first().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
            val picture = photos.first().measure(Constraints.fixed(width, height - panel.height))
            layout(width, height) {
                picture.place(0, 0)
                panel.place(0, picture.height)
            }
        }
    }
}

private val PANEL_WIDTH = 360.dp
private const val MAX_HOLDS = 400
private const val MAX_ZOOM = 8f

@Composable
private fun EditorCanvas(
    bitmap: Bitmap,
    state: EditorState,
    imageWidth: Int,
    onTap: (Float, Float, Float) -> Unit,
    onLongPress: (Float, Float, Float) -> Unit,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val haptics = LocalHapticFeedback.current
    val touchSlop = with(LocalDensity.current) { 16.dp.toPx() }
    val line = with(LocalDensity.current) { 1.dp.toPx() }
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)

    var zoom by remember { mutableFloatStateOf(1f) }
    // Offset of the image's top-left corner from where it sits when fitted and not zoomed.
    var pan by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxWidth = constraints.maxWidth.toFloat()
        val boxHeight = constraints.maxHeight.toFloat()
        val fit = min(boxWidth / bitmap.width, boxHeight / bitmap.height)
        val drawn = Size(bitmap.width * fit, bitmap.height * fit)
        val origin = Offset((boxWidth - drawn.width) / 2f, (boxHeight - drawn.height) / 2f)

        // After a rotation the photo is laid out at another size: keep the same part of it in view.
        val lastDrawn = remember { arrayOf(drawn) }
        LaunchedEffect(drawn) {
            val before = lastDrawn[0]
            if (before != drawn && before.width > 0f && before.height > 0f) {
                pan = Offset(pan.x * drawn.width / before.width, pan.y * drawn.height / before.height)
            }
            lastDrawn[0] = drawn
        }
        // Built once per layout, not on every frame of a pinch or drag.
        val outlines = remember(state.holds, drawn) { holdPaths(state.holds, drawn, line) }

        fun toImage(point: Offset): Offset {
            val topLeft = origin + pan
            return Offset((point.x - topLeft.x) / (drawn.width * zoom), (point.y - topLeft.y) / (drawn.height * zoom))
        }

        // The finger tolerance expressed in pixels of the detection image.
        fun tolerance(): Float = touchSlop / (drawn.width * zoom / imageWidth)

        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(drawn, origin) {
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                        val applied = newZoom / zoom
                        val topLeft = origin + pan
                        // Keep the point under the fingers fixed while zooming.
                        val moved = centroid - (centroid - topLeft) * applied + panChange
                        zoom = newZoom
                        pan = Offset(
                            (moved.x - origin.x).coerceIn(drawn.width * (1f - newZoom), 0f),
                            (moved.y - origin.y).coerceIn(drawn.height * (1f - newZoom), 0f),
                        )
                    }
                }
                .pointerInput(drawn, origin, imageWidth) {
                    detectTapGestures(
                        onTap = { point ->
                            val p = toImage(point)
                            tap(p.x, p.y, tolerance())
                        },
                        onLongPress = { point ->
                            val p = toImage(point)
                            // No feedback in the margins around the photo, where nothing happens.
                            if (p.x in 0f..1f && p.y in 0f..1f) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            longPress(p.x, p.y, tolerance())
                        },
                    )
                },
        ) {
            withTransform({
                translate(origin.x + pan.x, origin.y + pan.y)
                scale(zoom, zoom, pivot = Offset.Zero)
            }) {
                drawCircuit(
                    image, state.holds, state.selection,
                    showUnselected = true, stroke = line / zoom, area = drawn, paths = outlines,
                )
            }
        }
    }
}

@Composable
private fun ToolRow(tool: Tool, onSelect: (Tool) -> Unit) {
    // Light on the dark panel; the selected tool is the white one.
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = Color.White,
        selectedContainerColor = Color.White,
        selectedLabelColor = Ink,
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = tool == Tool.CIRCUIT, onClick = { onSelect(Tool.CIRCUIT) }, label = { Text("Circuito") }, colors = colors)
        FilterChip(selected = tool == Tool.START, onClick = { onSelect(Tool.START) }, label = { Text("Inicio") }, colors = colors)
        FilterChip(selected = tool == Tool.TOP, onClick = { onSelect(Tool.TOP) }, label = { Text("Top") }, colors = colors)
        FilterChip(selected = tool == Tool.FOOT, onClick = { onSelect(Tool.FOOT) }, label = { Text("Pies") }, colors = colors)
    }
}

@Composable
private fun PaletteRow(palette: List<PaletteEntry>, onToggle: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (entry in palette) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(entry.argb))
                        .border(
                            if (entry.allSelected) 4.dp else 1.dp,
                            if (entry.allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        )
                        .clickable { onToggle(entry.group) },
                )
                Text(entry.count.toString(), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@Composable
private fun Banner(text: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun SensitivityDialog(initial: Float, hasSelection: Boolean, onConfirm: (Float) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sensibilidad de detección") },
        text = {
            Column {
                Text("Más sensibilidad detecta presas menos visibles, pero también más ruido.")
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)
                if (hasSelection) {
                    Text(
                        "Repetir la detección descarta los cambios sin guardar de este circuito.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("Detectar de nuevo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
