package com.climbtracker.ui

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.climbtracker.ClimbApp
import com.climbtracker.Locator
import com.climbtracker.core.tracker.BoulderStatus
import com.climbtracker.core.tracker.GeoPoint
import com.climbtracker.core.tracker.GymLocator
import com.climbtracker.data.BoulderSummary
import com.climbtracker.data.GymEntity
import com.climbtracker.data.GymSearch
import com.climbtracker.data.Place
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.climbtracker.R
import androidx.compose.ui.platform.LocalResources

class GymsViewModel(app: Application) : AndroidViewModel(app) {
    private val climb = app as ClimbApp
    val gyms = climb.repository.gyms().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val boulders = climb.summaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var message by mutableStateOf<String?>(null)

    /** Where the phone is, when that is allowed and known; used for distances and to centre the map. */
    var here by mutableStateOf<GeoPoint?>(null)
        private set

    /** Gyms found on the map by the last search. */
    var results by mutableStateOf<List<Place>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set

    /** True while looking gyms up by name, which is shown in the search field rather than on the map. */
    var searchingName by mutableStateOf(false)
        private set

    val canLocate: Boolean get() = climb.locator.permitted

    init {
        viewModelScope.launch { here = climb.locator.current()?.point }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { climb.repository.renameGym(id, name) }
    }

    /** Makes where the phone is now the position of the gym. */
    fun saveLocation(id: Long) {
        viewModelScope.launch {
            val fix = climb.locator.current(timeoutMillis = 8000)
            message = if (fix == null) {
                climb.getString(R.string.location_failed)
            } else {
                here = fix.point
                climb.repository.setGymLocation(id, fix.point)
                climb.getString(R.string.location_saved)
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { climb.repository.deleteGym(id) }
    }

    /** Looks for sports centres called like [query], those around [near] first. */
    fun search(query: String, near: GeoPoint?) {
        if (query.isBlank() || searchingName) return
        val name = query.trim()
        val around = near ?: here
        viewModelScope.launch {
            searchingName = true
            results = emptyList()
            var failures = 0
            // Both services are asked at once and each adds what it finds as it answers: one is
            // quick but only knows whole names, the other finds part of a name but can be slow.
            val sources = listOf<suspend () -> List<Place>>(
                { climb.gymSearch.byExactName(name, around) },
                { climb.gymSearch.byName(name, around) },
            )
            coroutineScope {
                for (source in sources) {
                    launch {
                        try {
                            val joined = GymSearch.merge(results, source())
                            results = if (around == null) joined else joined.sortedBy { GymLocator.distanceMeters(around, it.point) }
                        } catch (e: IOException) {
                            failures++
                        }
                    }
                }
            }
            searchingName = false
            if (results.isEmpty()) {
                message = if (failures == sources.size) climb.getString(R.string.search_failed) else climb.getString(R.string.no_gym_found)
            }
        }
    }

    fun searchArea(bounds: MapBounds?) {
        if (bounds == null) return
        if (bounds.north - bounds.south > MAX_AREA_DEGREES) {
            message = climb.getString(R.string.zoom_in)
            return
        }
        find { climb.gymSearch.inArea(bounds.south, bounds.west, bounds.north, bounds.east) }
    }

    private fun find(lookUp: suspend () -> List<Place>) {
        if (searching) return
        viewModelScope.launch {
            searching = true
            try {
                results = lookUp()
                if (results.isEmpty()) message = climb.getString(R.string.no_gym_found)
            } catch (e: IOException) {
                message = climb.getString(R.string.search_failed)
            } finally {
                searching = false
            }
        }
    }

    fun clearResults() {
        results = emptyList()
    }

    /** Adds the place to the user's gyms, or moves the gym of that name there. */
    fun add(place: Place) {
        viewModelScope.launch {
            climb.repository.addGym(place.name, place.point)
            results = results - place
            message = climb.getString(R.string.gym_added, place.name)
        }
    }

    /** Makes the place the position of an existing gym, whatever it is called. */
    fun useFor(gym: GymEntity, place: Place) {
        viewModelScope.launch {
            climb.repository.setGymLocation(gym.id, place.point)
            message = climb.getString(R.string.gym_location_saved, gym.name)
        }
    }

    private companion object {
        /** Beyond this the search would cover whole regions and take too long. */
        const val MAX_AREA_DEGREES = 2.0
    }
}

/** Name of a gym, with the names already in use as suggestions. Used to put a wall in a gym. */
@Composable
fun GymDialog(
    title: String,
    initial: String,
    suggestions: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.gym)) }, singleLine = true)
                for (suggestion in suggestions.filter { it != name }.take(4)) {
                    TextButton(onClick = { name = suggestion }) { Text(suggestion) }
                }
                if (note != null) Text(note, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun GymEntity.point(): GeoPoint? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    return GeoPoint(lat, lon)
}

@Composable
private fun distanceLabel(from: GeoPoint?, to: GeoPoint?): String? {
    if (from == null || to == null) return null
    val metres = GymLocator.distanceMeters(from, to)
    return if (metres < 1000) "${metres.roundToInt()} m" else String.format(LocalResources.current.textLocale(), "%.1f km", metres / 1000)
}

@Composable
fun GymsScreen(onOpen: (GymEntity) -> Unit, bottomBar: @Composable () -> Unit = {}, vm: GymsViewModel = viewModel()) {
    val gyms by vm.gyms.collectAsStateWithLifecycle()
    val boulders by vm.boulders.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<GymEntity?>(null) }
    var deleting by remember { mutableStateOf<GymEntity?>(null) }
    var picked by remember { mutableStateOf<Place?>(null) }
    var locating by remember { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val map = remember { GymMapState() }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locating?.let(vm::saveLocation)
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    // The map opens where the user is; failing that, on one of their gyms.
    var centred by remember { mutableStateOf(false) }
    val startAt = vm.here ?: gyms.firstNotNullOfOrNull { it.point() }
    LaunchedEffect(startAt) {
        if (!centred && startAt != null) {
            centred = true
            map.moveTo(startAt, 11.0)
        }
    }
    // A search moves the map to what it found.
    LaunchedEffect(vm.results) {
        vm.results.firstOrNull()?.let { if (map.bounds()?.contains(it.point) != true) map.moveTo(it.point, 13.0) }
    }

    val pins = gyms.mapNotNull { gym -> gym.point()?.let { MapPin(it, gym.name, own = true, key = gym) } } +
        vm.results.map { MapPin(it.point, it.name, own = false, key = it) }

    Scaffold(containerColor = Cream, bottomBar = bottomBar, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // The map view draws past its edges unless clipped.
            Box(Modifier.fillMaxWidth().weight(0.42f).clipToBounds()) {
                GymMap(
                    map, pins,
                    onPin = { pin ->
                        when (val key = pin.key) {
                            is Place -> picked = key
                            is GymEntity -> vm.message = key.name
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(
                    onClick = { vm.searchArea(map.bounds()) },
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp),
                    shape = RoundedCornerShape(50),
                    color = CardWhite,
                    contentColor = Ink,
                    border = BorderStroke(1.dp, Hairline),
                    shadowElevation = 3.dp,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (vm.searching) {
                            Spinner(size = 18.dp, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Text(stringResource(R.string.search_area), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    shape = RoundedCornerShape(50),
                    color = CardWhite.copy(alpha = 0.9f),
                    contentColor = Ink,
                ) {
                    Text(MAP_CREDIT, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(0.58f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.search_gym)) },
                        leadingIcon = {
                            if (vm.searchingName) {
                                Spinner(size = 20.dp, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null)
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focus.clearFocus()
                            vm.search(query, map.center())
                        }),
                    )
                }
                if (vm.results.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel(stringResource(R.string.found), Modifier.weight(1f))
                            IconButton(onClick = vm::clearResults) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_results), tint = Muted)
                            }
                        }
                    }
                    items(vm.results) { place ->
                        PlaceRow(place, distanceLabel(vm.here, place.point)) {
                            map.moveTo(place.point, 15.0)
                            picked = place
                        }
                    }
                }
                item { SectionLabel(stringResource(R.string.your_gyms)) }
                if (gyms.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.no_gyms_yet),
                            color = Muted,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                items(gyms, key = { it.id }) { gym ->
                    GymCard(
                        gym, boulders.filter { it.gymId == gym.id }, distanceLabel(vm.here, gym.point()),
                        onOpen = { onOpen(gym) },
                        onLocate = {
                            if (vm.canLocate) {
                                vm.saveLocation(gym.id)
                            } else {
                                locating = gym.id
                                askLocation.launch(Locator.PERMISSIONS)
                            }
                        },
                        onRename = { renaming = gym },
                        onDelete = { deleting = gym },
                    )
                }
            }
        }
    }

    picked?.let { place ->
        val sameName = gyms.firstOrNull { it.name.equals(place.name.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(place.name) },
            text = {
                Column {
                    if (place.detail.isNotBlank()) Text(place.detail, color = Muted, modifier = Modifier.padding(bottom = 8.dp))
                    TextButton(onClick = {
                        vm.add(place)
                        picked = null
                    }) { Text(if (sameName == null) stringResource(R.string.add_to_gyms) else stringResource(R.string.use_as_location, sameName.name)) }
                    // A gym may be called something else on the map than in the app.
                    for (gym in gyms.filter { it.id != sameName?.id }.take(5)) {
                        TextButton(onClick = {
                            vm.useFor(gym, place)
                            picked = null
                        }) { Text(stringResource(R.string.use_as_location, gym.name)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picked = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    renaming?.let { gym ->
        GymDialog(stringResource(R.string.rename), gym.name, emptyList(), onConfirm = {
            vm.rename(gym.id, it)
            renaming = null
        }, onDismiss = { renaming = null })
    }
    deleting?.let { gym ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.remove_gym)) },
            text = { Text(stringResource(R.string.remove_gym_text, gym.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(gym.id)
                    deleting = null
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun MapBounds.contains(point: GeoPoint) = point.latitude in south..north && point.longitude in west..east

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, color = Muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun PlaceRow(place: Place, distance: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (place.detail.isNotBlank()) Text(place.detail, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (distance != null) Text(distance, Modifier.padding(start = 12.dp), color = Muted)
    }
}

@Composable
private fun GymCard(
    gym: GymEntity,
    boulders: List<BoulderSummary>,
    distance: String?,
    onOpen: () -> Unit,
    onLocate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val current = boulders.filter { !it.boulder.archived }
    val sent = boulders.count { it.status != BoulderStatus.PROJECT }
    val sessions = boulders.flatMap { b -> b.attempts.map { it.date / 86_400_000L } }.distinct().size
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = CardWhite,
        contentColor = Ink,
        border = BorderStroke(1.dp, Hairline),
    ) {
        // The actions go under the text: beside it they left the name too little room.
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 4.dp)) {
            Text(gym.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val takenDown = boulders.size - current.size
            Text(
                stringResource(R.string.joined, pluralStringResource(R.plurals.boulders_up, current.size, current.size), pluralStringResource(R.plurals.taken_down_count, takenDown, takenDown)),
                color = Muted,
            )
            Text(
                stringResource(R.string.gym_sent_of, pluralStringResource(R.plurals.sent_count, sent, sent), boulders.size, pluralStringResource(R.plurals.sessions_count, sessions, sessions)),
                color = Muted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        gym.latitude == null -> stringResource(R.string.gym_no_location)
                        distance != null -> stringResource(R.string.gym_at_distance, distance)
                        else -> stringResource(R.string.gym_auto)
                    },
                    Modifier.weight(1f),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                IconButton(onClick = onLocate) {
                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(R.string.save_current_location), tint = Muted)
                }
                IconButton(onClick = onRename) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename), tint = Muted) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_gym), tint = Muted) }
            }
        }
    }
}
