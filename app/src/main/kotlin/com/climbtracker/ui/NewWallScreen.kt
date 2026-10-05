package com.climbtracker.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.climbtracker.ClimbApp
import com.climbtracker.data.WallEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewWallViewModel(app: Application) : AndroidViewModel(app) {
    private val climb = app as ClimbApp

    val walls: StateFlow<List<WallEntity>> =
        climb.repository.walls().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Wall awaiting delete confirmation, with the number of boulders that would go with it. */
    var pendingDelete by mutableStateOf<Pair<WallEntity, Int>?>(null)
        private set

    val cameraUri: Uri = climb.photos.cameraUri()

    fun import(uri: Uri, onReady: () -> Unit) {
        viewModelScope.launch {
            busy = true
            error = null
            val ok = withContext(Dispatchers.IO) { climb.photos.import(uri) }
            busy = false
            if (ok) onReady() else error = "No se ha podido leer la foto."
        }
    }

    fun reportNoCamera() {
        error = "Este dispositivo no tiene ninguna app de cámara. Elige la foto de la galería."
    }

    fun askDelete(wall: WallEntity) {
        viewModelScope.launch { pendingDelete = wall to climb.repository.boulderCount(wall.id) }
    }

    fun cancelDelete() {
        pendingDelete = null
    }

    fun confirmDelete() {
        val wall = pendingDelete?.first ?: return
        pendingDelete = null
        viewModelScope.launch {
            val path = climb.repository.deleteWall(wall.id)
            if (path != null) withContext(Dispatchers.IO) { climb.photos.delete(path) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewWallScreen(
    onImported: () -> Unit,
    onWall: (Long) -> Unit,
    onBack: () -> Unit,
    vm: NewWallViewModel = viewModel(),
) {
    val walls by vm.walls.collectAsStateWithLifecycle()
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) vm.import(vm.cameraUri, onImported)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.import(uri, onImported)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva pared") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Button(
                        onClick = {
                            try {
                                takePicture.launch(vm.cameraUri)
                            } catch (e: ActivityNotFoundException) {
                                vm.reportNoCamera()
                            }
                        },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Hacer foto") }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Elegir de la galería") }
                }
                vm.error?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                if (walls.isNotEmpty()) {
                    item { Text("Paredes guardadas", style = MaterialTheme.typography.titleMedium) }
                    items(walls, key = { it.id }) { wall ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WallPreview(
                                wall.photoPath, emptyList(), emptyMap(),
                                Modifier.weight(1f).clickable { onWall(wall.id) },
                                maxSide = 512,
                            )
                            IconButton(onClick = { vm.askDelete(wall) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Borrar pared")
                            }
                        }
                    }
                }
            }
            if (vm.busy) Spinner(Modifier.align(Alignment.Center))
        }
    }

    vm.pendingDelete?.let { (_, count) ->
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("Borrar pared") },
            text = {
                Text(
                    when (count) {
                        0 -> "Se borrará la pared y su foto."
                        1 -> "Se borrará la pared, su foto y 1 bloque con sus intentos."
                        else -> "Se borrará la pared, su foto y $count bloques con sus intentos."
                    },
                )
            },
            confirmButton = { TextButton(onClick = vm::confirmDelete) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("Cancelar") } },
        )
    }
}
