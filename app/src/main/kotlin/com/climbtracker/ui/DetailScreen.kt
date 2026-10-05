package com.climbtracker.ui

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.climbtracker.ClimbApp
import com.climbtracker.Locator
import com.climbtracker.core.editor.EditorHold
import com.climbtracker.core.editor.SelectedHold
import com.climbtracker.core.tracker.AttemptResult
import com.climbtracker.core.tracker.BoulderStatus
import com.climbtracker.core.tracker.GradeScale
import com.climbtracker.core.tracker.Progress
import com.climbtracker.core.tracker.Tracker
import com.climbtracker.data.AttemptEntity
import com.climbtracker.data.BoulderEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val climb = app as ClimbApp
    private val boulderId: Long = checkNotNull(handle["boulderId"])

    val boulder: StateFlow<BoulderEntity?> =
        climb.repository.boulder(boulderId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Chronological order, oldest first. */
    val attempts: StateFlow<List<AttemptEntity>> =
        climb.repository.attempts(boulderId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var photoPath by mutableStateOf<String?>(null)
        private set

    /** Holds of the circuit only. */
    var holds by mutableStateOf<List<EditorHold>>(emptyList())
        private set
    var selection by mutableStateOf<Map<Long, SelectedHold>>(emptyMap())
        private set

    /** False until the circuit has been read, so the photo is not drawn half-loaded. */
    var loaded by mutableStateOf(false)
        private set

    /** True while the user is choosing the last hold reached in a new attempt. */
    var picking by mutableStateOf(false)

    val gradeScale: GradeScale get() = climb.prefs.gradeScale

    private var lastAttemptId: Long? = null

    var gymName by mutableStateOf<String?>(null)
        private set
    val gyms = climb.repository.gyms().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val found = climb.repository.boulderOnce(boulderId)
            if (found != null) {
                val circuit = climb.repository.selection(boulderId)
                photoPath = climb.repository.wall(found.wallId)?.photoPath
                gymName = climb.repository.gymNameOfWall(found.wallId)
                holds = climb.repository.holds(found.wallId).filter { it.id in circuit }
                selection = circuit
            }
            loaded = true
        }
    }

    /** Logs an attempt that ended on [holdId]; reaching the top hold makes it a send. */
    fun addAttempt(holdId: Long, onAdded: (AttemptResult) -> Unit) {
        if (!picking) return
        picking = false
        val result = if (Progress.isTop(holds, selection, holdId)) AttemptResult.SEND else AttemptResult.FAIL
        viewModelScope.launch {
            lastAttemptId = climb.repository.addAttempt(boulderId, result, holdId)
            onAdded(result)
        }
    }

    fun undoLastAttempt() {
        val id = lastAttemptId ?: return
        lastAttemptId = null
        deleteAttempt(id)
    }

    fun deleteAttempt(id: Long) {
        viewModelScope.launch { climb.repository.deleteAttempt(id) }
    }

    /** What the picture to share says; null while the share screen is closed. */
    var shareCard by mutableStateOf<ShareCard?>(null)
        private set

    /** The picture as it would be shared now, or null while it is being drawn. */
    var sharePreview by mutableStateOf<Bitmap?>(null)
        private set
    var shareCentred by mutableStateOf(false)
        private set

    /** A photo of the user's own, shown instead of the wall and without the circuit. */
    private var shareOwnPhoto by mutableStateOf<Bitmap?>(null)
    val shareHasOwnPhoto: Boolean get() = shareOwnPhoto != null
    private var shareWall: Bitmap? = null
    private var shareJob: Job? = null

    fun openShare(card: ShareCard) {
        shareCard = card
        drawShare()
    }

    fun closeShare() {
        shareJob?.cancel()
        shareCard = null
        sharePreview = null
        shareOwnPhoto = null
        shareWall = null
    }

    fun centreShare(value: Boolean) {
        shareCentred = value
        drawShare()
    }

    /** Uses the photo at [uri] instead of the wall, or goes back to the wall with null. */
    fun useOwnPhoto(uri: Uri?) {
        viewModelScope.launch {
            shareOwnPhoto = if (uri == null) null else (withContext(Dispatchers.IO) { climb.photos.read(uri) } ?: shareOwnPhoto)
            drawShare()
        }
    }

    private fun drawShare() {
        val card = shareCard ?: return
        val path = photoPath ?: return
        val own = shareOwnPhoto
        val centred = shareCentred
        shareJob?.cancel()
        shareJob = viewModelScope.launch {
            sharePreview = withContext(Dispatchers.Default) {
                if (own != null) {
                    renderShareCard(own, emptyList(), emptyMap(), card, centred)
                } else {
                    val wall = shareWall ?: climb.photos.load(path, 1600)?.also { shareWall = it } ?: return@withContext null
                    renderShareCard(wall, holds, selection, card, centred)
                }
            }
        }
    }

    /** Stores the picture on screen where other apps can read it and hands its address to [onReady]. */
    fun share(onReady: (Uri) -> Unit) {
        val picture = sharePreview ?: return
        viewModelScope.launch {
            onReady(withContext(Dispatchers.IO) { climb.photos.shareUri(picture) })
        }
    }

    /** True the first time, when the permission to tell gyms apart by position is still to be asked. */
    fun shouldAskLocation(): Boolean {
        if (climb.locator.permitted || climb.prefs.locationAsked) return false
        climb.prefs.locationAsked = true
        return true
    }

    fun assignGym(name: String) {
        viewModelScope.launch {
            val wallId = climb.repository.boulderOnce(boulderId)?.wallId ?: return@launch
            climb.repository.assignGym(wallId, name)
            gymName = climb.repository.gymNameOfWall(wallId)
            if (gymName != null) climb.prefs.lastGym = gymName
            // The name is shown at once; where the gym is follows when the phone has a position.
            if (gymName != null) climb.locator.current()?.let { climb.repository.assignGym(wallId, name, it.point) }
        }
    }

    fun setTakenDown(value: Boolean) {
        viewModelScope.launch { climb.repository.setArchived(boulderId, value) }
    }

    fun update(name: String, grade: String, notes: String) {
        viewModelScope.launch { climb.repository.updateBoulderInfo(boulderId, name, grade, notes) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            climb.repository.deleteBoulder(boulderId)
            onDeleted()
        }
    }
}

private val ATTEMPT_DATE = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("es-ES"))

private fun zoned(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

@Composable
fun DetailScreen(
    onEdit: (wallId: Long, boulderId: Long) -> Unit,
    onScanAnother: (wallId: Long) -> Unit,
    onBack: () -> Unit,
    vm: DetailViewModel = viewModel(),
) {
    val boulder by vm.boulder.collectAsStateWithLifecycle()
    val attempts by vm.attempts.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showGym by remember { mutableStateOf(false) }
    val gyms by vm.gyms.collectAsStateWithLifecycle()
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { showGym = true }
    if (showGym) {
        // The gym belongs to the wall, so every boulder on this photo moves with it.
        GymDialog("Rocódromo de esta pared", vm.gymName.orEmpty(), gyms.map { it.name }, note = LOCATION_NOTE, onConfirm = {
            showGym = false
            vm.assignGym(it)
        }, onDismiss = { showGym = false })
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val current = boulder
    val context = LocalContext.current

    val status = Tracker.statusOf(attempts.map { it.result })
    val best = Progress.best(vm.holds, vm.selection, attempts.map { it.result to it.lastHoldId })
    val colour = Color(Progress.circuitColor(vm.holds, vm.selection))
    val lineY = if (attempts.isEmpty()) null else Progress.lineY(vm.holds, vm.selection, best)
    val sessions = attempts.map { zoned(it.date).toLocalDate() }.distinct().size

    if (vm.shareCard != null && current != null) {
        ShareScreen(
            title = current.name,
            preview = vm.sharePreview,
            centred = vm.shareCentred,
            ownPhoto = vm.shareHasOwnPhoto,
            onCentred = vm::centreShare,
            onPickPhoto = vm::useOwnPhoto,
            onWallPhoto = { vm.useOwnPhoto(null) },
            onShare = {
                vm.share { uri ->
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("image/jpeg")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    // The share sheet reads its thumbnail from the clip data, and only that carries
                    // the permission to read the picture over to it.
                    send.clipData = ClipData.newUri(context.contentResolver, current.name, uri)
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            },
            onClose = vm::closeShare,
        )
    }

    Scaffold(
        containerColor = Cream,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    data,
                    shape = RoundedCornerShape(50),
                    containerColor = CardWhite,
                    contentColor = Ink,
                    actionColor = Terracotta,
                )
            }
        },
        bottomBar = {
            Surface(color = CardWhite, border = BorderStroke(1.dp, Hairline)) {
                Button(
                    onClick = { vm.picking = !vm.picking },
                    enabled = current != null && vm.holds.isNotEmpty(),
                    modifier = Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (vm.picking) Ink else Terracotta,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        if (vm.picking) "Cancelar" else "+   ${ordinal(attempts.size + 1)} intento",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    ) { padding ->
        if (current == null) return@Scaffold
        LazyColumn(
            Modifier.padding(bottom = padding.calculateBottomPadding()).statusBarsPadding().fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CircleButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                    Box {
                        CircleButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más acciones")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Compartir") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    vm.openShare(
                                        ShareCards.build(
                                            current.name, current.grade, vm.gymName, colour.toArgb(), best,
                                            current.createdAt, attempts.map { it.date to it.result },
                                        ),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Corregir presas") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onEdit(current.wallId, current.id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Escanear otro bloque en esta foto") },
                                leadingIcon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onScanAnother(current.wallId)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Asignar rocódromo") },
                                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    if (vm.shouldAskLocation()) askLocation.launch(Locator.PERMISSIONS) else showGym = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Editar nombre, grado y notas") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    showEdit = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (current.archived) "Volver a mis proyectos" else "Marcar como desmontado") },
                                leadingIcon = {
                                    Icon(
                                        if (current.archived) Icons.Default.Refresh else Icons.Default.Construction,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    vm.setTakenDown(!current.archived)
                                },
                            )
                            HorizontalDivider(color = Hairline)
                            DropdownMenuItem(
                                text = { Text("Quitar de mis proyectos", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    menuOpen = false
                                    showDelete = true
                                },
                            )
                        }
                    }
                }
            }
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(Progress.circuitColor(vm.holds, vm.selection), 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(current.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(
                        listOfNotNull(current.grade, vm.gymName, current.notes.ifBlank { null }).joinToString(" · "),
                        color = Muted,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (current.archived) {
                        Text("Desmontado", color = Terracotta, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (vm.picking) {
                item {
                    Text(
                        "Toca la última presa que alcanzaste. Si llegaste arriba, toca la presa de top.",
                        color = Terracotta,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            item {
                Box(Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF2A2725))) {
                    if (vm.loaded) {
                        WallPreview(
                            vm.photoPath, vm.holds, vm.selection,
                            Modifier.fillMaxWidth(),
                            maxSide = 1280,
                            progressY = lineY,
                            tint = colour,
                            onHoldTap = if (!vm.picking) null else { holdId ->
                                val number = attempts.size + 1
                                vm.addAttempt(holdId) { result ->
                                    scope.launch {
                                        val outcome = if (result == AttemptResult.SEND) "encadenado" else "registrado"
                                        val pressed = snackbar.showSnackbar(
                                            "${ordinal(number)} intento · $outcome",
                                            actionLabel = "Deshacer",
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (pressed == SnackbarResult.ActionPerformed) vm.undoLastAttempt()
                                    }
                                }
                            },
                        )
                    }
                }
            }
            item { StatsCard(sessions, attempts.size, status, best, colour) }
            items(attempts.withIndex().reversed(), key = { it.value.id }) { (index, attempt) ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${ordinal(index + 1)} intento", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(12.dp))
                        Text(ATTEMPT_DATE.format(zoned(attempt.date)), color = Muted, modifier = Modifier.weight(1f))
                        if (attempt.result == AttemptResult.SEND) {
                            Text("encadenado", color = SendGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        } else {
                            val percent = (Progress.fraction(vm.holds, vm.selection, attempt.lastHoldId) * 100).roundToInt()
                            Text("$percent%", color = colour, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { vm.deleteAttempt(attempt.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Borrar intento", tint = Muted, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = Hairline)
                }
            }
        }
    }

    if (showEdit && current != null) {
        BoulderDialog(
            title = "Editar datos",
            initialName = current.name,
            initialGrade = current.grade,
            scale = vm.gradeScale,
            initialNotes = current.notes,
            onConfirm = { name, grade, notes ->
                showEdit = false
                vm.update(name, grade, notes)
            },
            onDismiss = { showEdit = false },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Quitar de mis proyectos") },
            text = { Text("Se borrará el bloque y su historial de intentos. La pared y sus presas se conservan.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.delete(onBack)
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun StatsCard(sessions: Int, attempts: Int, status: BoulderStatus, best: Float, colour: Color) {
    Surface(shape = RoundedCornerShape(28.dp), color = CardWhite, contentColor = Ink, border = BorderStroke(1.dp, Hairline)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Stat(if (sessions == 1) "SESIÓN" else "SESIONES", sessions, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(56.dp).background(Hairline))
            Stat(if (attempts == 1) "INTENTO" else "INTENTOS", attempts, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(56.dp).background(Hairline))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when (status) {
                    BoulderStatus.PROJECT -> ProgressRing(best, colour, Modifier.size(68.dp))
                    BoulderStatus.SENT -> SendBadge("TOP", filled = false, modifier = Modifier.size(68.dp))
                    BoulderStatus.FLASH -> SendBadge("FLASH", filled = false, modifier = Modifier.size(68.dp))
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
        Text(value.toString(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
    }
}
