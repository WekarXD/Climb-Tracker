package com.climbtracker.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.climbtracker.ClimbApp
import com.climbtracker.core.tracker.BoulderStatus
import com.climbtracker.core.tracker.ColorNames
import com.climbtracker.core.tracker.GradeScale
import com.climbtracker.data.BoulderSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.climbtracker.R
import androidx.compose.ui.platform.LocalResources
import com.climbtracker.core.tracker.ColorName

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val climb = app as ClimbApp

    val all: StateFlow<List<BoulderSummary>> =
        climb.summaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Null shows every status. */
    val status = MutableStateFlow<BoulderStatus?>(null)

    /** Colour name to show, or null for every colour. */
    val colour = MutableStateFlow<ColorName?>(null)

    /** Shows the boulders marked as taken down instead of the current ones. */
    val takenDown = MutableStateFlow(false)

    var gradeScale by mutableStateOf(climb.prefs.gradeScale)
        private set

    /** Outcome of the last export or import, to show once. */
    var message by mutableStateOf<String?>(null)

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    checkNotNull(climb.contentResolver.openOutputStream(uri)).use { climb.backup.export(it) }
                }
                climb.getString(R.string.backup_saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                climb.getString(R.string.backup_save_failed)
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    checkNotNull(climb.contentResolver.openInputStream(uri)).use { climb.backup.import(it) }
                }
                climb.getString(R.string.backup_restored)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                climb.getString(R.string.backup_not_ours)
            } catch (e: Exception) {
                climb.getString(R.string.file_unreadable)
            }
        }
    }

    fun changeGradeScale(scale: GradeScale) {
        climb.prefs.gradeScale = scale
        gradeScale = scale
    }
}

private fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
fun HomeScreen(
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    gymId: Long? = null,
    gymName: String? = null,
    onClearGym: () -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val unfiltered by vm.all.collectAsStateWithLifecycle()
    val everything = if (gymId == null) unfiltered else unfiltered.filter { it.gymId == gymId }
    val takenDown by vm.takenDown.collectAsStateWithLifecycle()
    // Taken-down boulders live in their own list so the project list shows what is on the wall now.
    val all = everything.filter { it.boulder.archived == takenDown }
    val anyTakenDown = everything.any { it.boulder.archived }
    // Restoring the last taken-down boulder would otherwise leave an empty list on screen.
    LaunchedEffect(takenDown, anyTakenDown) {
        if (takenDown && !anyTakenDown) vm.takenDown.value = false
    }
    val status by vm.status.collectAsStateWithLifecycle()
    val colour by vm.colour.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val backupName = stringResource(R.string.backup_file_name)
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // Importing replaces everything, so it is confirmed first.
        if (uri != null) pendingImport = uri
    }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.message = null
        }
    }
    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.restore_backup)) },
            text = { Text(stringResource(R.string.restore_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    vm.importFrom(uri)
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    val colours = all.groupBy { ColorNames.of(it.color) }.map { (name, list) -> Triple(name, list.first().color, list.size) }
    val shown = all.filter {
        (status == null || it.status == status) && (colour == null || ColorNames.of(it.color) == colour)
    }
    val sessions = shown.groupBy { dayOf(it.boulder.createdAt) }.toSortedMap(compareByDescending { it })
    val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }

    Scaffold(
        containerColor = Cream,
        bottomBar = bottomBar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                containerColor = Terracotta,
                contentColor = Color.White,
                shape = RoundedCornerShape(50),
                // The button takes its name for screen readers from the icon: its text is not read.
                icon = { Icon(Icons.Default.PhotoCamera, contentDescription = stringResource(R.string.scan_route)) },
                text = { Text(stringResource(R.string.scan_route), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium) },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = full) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        "Climb Tracker",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        CircleButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.settings))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_backup)) },
                                onClick = {
                                    menuOpen = false
                                    exportBackup.launch(backupName)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.restore_backup)) },
                                onClick = {
                                    menuOpen = false
                                    pickBackup.launch(arrayOf("application/zip", "application/octet-stream"))
                                },
                            )
                            for (scale in GradeScale.entries) {
                                val name = if (scale == GradeScale.FONT) stringResource(R.string.scale_font) else stringResource(R.string.scale_v)
                                DropdownMenuItem(
                                    text = { Text(ticked(name, vm.gradeScale == scale)) },
                                    onClick = {
                                        vm.changeGradeScale(scale)
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item(span = full) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in BoulderStatus.entries) {
                        Pill(selected = status == option, onClick = { vm.status.value = if (status == option) null else option }) {
                            Text(stringResource(option.label()), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    if (gymId != null) {
                        Pill(selected = true, onClick = onClearGym) {
                            Text("✕ ${gymName.orEmpty()}", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    if (anyTakenDown || takenDown) {
                        Pill(selected = takenDown, onClick = { vm.takenDown.value = !takenDown }) {
                            Text(stringResource(R.string.taken_down_filter), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            if (colours.size > 1 || colour != null) {
                item(span = full) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((name, argb, count) in colours) {
                            Pill(
                                selected = colour == name,
                                onClick = { vm.colour.value = if (colour == name) null else name },
                                description = pluralStringResource(R.plurals.colour_boulders, count, stringResource(name.label()), count),
                            ) {
                                ColorDot(argb, 16.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(count.toString(), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item(span = full) {
                    Text(
                        when {
                            takenDown -> stringResource(R.string.none_taken_down)
                            everything.isEmpty() -> stringResource(R.string.no_boulders_yet)
                            else -> stringResource(R.string.none_with_filters)
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        textAlign = TextAlign.Center,
                        color = Muted,
                    )
                }
            }
            for ((day, boulders) in sessions) {
                item(span = full, key = "day-$day") {
                    Text(
                        stringResource(R.string.session_of, LocalResources.current.dayAndMonth(day)),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(boulders, key = { it.boulder.id }) { summary ->
                    BoulderCard(summary, onClick = { onOpen(summary.boulder.id) })
                }
            }
        }
    }
}

@Composable
private fun BoulderCard(summary: BoulderSummary, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF2A2725))
            .clickable(onClick = onClick),
    ) {
        WallPreview(
            summary.photoPath, summary.holds, summary.selection,
            Modifier.fillMaxSize(), maxSide = 512, cropToFill = true, labels = false,
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xF0141210))))
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(summary.color, 14.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        summary.boulder.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    listOfNotNull(summary.boulder.grade, summary.gymName, LocalResources.current.shortDay(dayOf(summary.boulder.createdAt))).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            when (summary.status) {
                BoulderStatus.FLASH -> SendBadge("FLASH", filled = true, modifier = Modifier.size(52.dp))
                BoulderStatus.SENT -> SendBadge("TOP", filled = true, modifier = Modifier.size(52.dp))
                BoulderStatus.PROJECT -> ProgressRing(
                    summary.progress,
                    Color(summary.color),
                    Modifier.size(52.dp).background(Color(0xB3141210), CircleShape),
                    track = Color.White.copy(alpha = 0.2f),
                )
            }
        }
    }
}
