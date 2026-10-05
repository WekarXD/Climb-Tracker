package com.climbtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.climbtracker.core.tracker.GradeScale
import com.climbtracker.core.tracker.Grades

/** Name, grade and optional notes. The notes field is hidden when [initialNotes] is null. */
@Composable
fun BoulderDialog(
    title: String,
    initialName: String,
    initialGrade: String,
    scale: GradeScale,
    initialNotes: String?,
    onConfirm: (name: String, grade: String, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // A grade saved in the other scale is kept as an option rather than silently replaced.
    val grades = remember(scale, initialGrade) {
        val all = Grades.of(scale)
        if (initialGrade.isEmpty() || initialGrade in all) all else listOf(initialGrade) + all
    }
    var name by remember { mutableStateOf(initialName) }
    var grade by remember { mutableStateOf(initialGrade.ifEmpty { grades[4] }) }
    var notes by remember { mutableStateOf(initialNotes.orEmpty()) }
    var gradesOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
                Box {
                    OutlinedButton(onClick = { gradesOpen = true }) { Text("Grado: $grade") }
                    DropdownMenu(expanded = gradesOpen, onDismissRequest = { gradesOpen = false }) {
                        for (option in grades) {
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    grade = option
                                    gradesOpen = false
                                },
                            )
                        }
                    }
                }
                if (initialNotes != null) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notas") },
                        minLines = 3,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, grade, notes) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
