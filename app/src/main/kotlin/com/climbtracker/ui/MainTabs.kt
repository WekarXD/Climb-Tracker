package com.climbtracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The two top-level screens, switched with the bottom bar. */
@Composable
fun MainTabs(onNew: () -> Unit, onOpen: (Long) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Gym chosen in the gyms tab, shown as a filter on the projects.
    var gymId by rememberSaveable { mutableStateOf<Long?>(null) }
    var gymName by rememberSaveable { mutableStateOf<String?>(null) }
    val bar: @Composable () -> Unit = { MainBottomBar(tab, onSelect = { tab = it }) }
    when (tab) {
        0 -> HomeScreen(
            onNew = onNew, onOpen = onOpen, bottomBar = bar,
            gymId = gymId, gymName = gymName,
            onClearGym = {
                gymId = null
                gymName = null
            },
        )
        1 -> GymsScreen(
            onOpen = { gym ->
                gymId = gym.id
                gymName = gym.name
                tab = 0
            },
            bottomBar = bar,
        )
        else -> ProfileScreen(bottomBar = bar)
    }
}

@Composable
private fun MainBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            color = CardWhite,
            border = BorderStroke(1.dp, Hairline),
            shadowElevation = 2.dp,
        ) {
            Row(Modifier.padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Tab("Proyectos", Icons.AutoMirrored.Filled.Assignment, selected == 0, Modifier.weight(1f)) { onSelect(0) }
                Tab("Rocódromos", Icons.Default.Place, selected == 1, Modifier.weight(1f)) { onSelect(1) }
                Tab("Perfil", Icons.Default.Person, selected == 2, Modifier.weight(1f)) { onSelect(2) }
            }
        }
    }
}

@Composable
private fun Tab(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colour = if (selected) Terracotta else Muted
    Column(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) LocalPalette.current.terracottaTint else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = colour)
        Text(label, color = colour, style = MaterialTheme.typography.labelMedium)
    }
}
