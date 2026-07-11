package dev.gatsyuk.grindsync.feature.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gatsyuk.grindsync.core.database.entity.MuscleGroupEntity
import dev.gatsyuk.grindsync.core.model.ExerciseType
import dev.gatsyuk.grindsync.core.model.Muscle
import dev.gatsyuk.grindsync.core.ui.exerciseTypeLabel
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

/**
 * Custom exercise form. Muscle mapping is captured HERE, at creation time —
 * the ExerciseMuscle join must never lag the catalog (SPEC §7.7).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseFormDialog(
    muscleGroups: List<MuscleGroupEntity>,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        muscleGroupId: Long,
        type: ExerciseType,
        unilateral: Boolean,
        primary: Set<Muscle>,
        secondary: Set<Muscle>,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<MuscleGroupEntity?>(null) }
    var type by remember { mutableStateOf(ExerciseType.STRENGTH_WEIGHT_REPS) }
    var unilateral by remember { mutableStateOf(false) }
    var primary by remember { mutableStateOf(setOf<Muscle>()) }
    var secondary by remember { mutableStateOf(setOf<Muscle>()) }
    var groupMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New exercise") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                ExposedDropdownMenuBox(expanded = groupMenu, onExpandedChange = { groupMenu = it }) {
                    OutlinedTextField(
                        value = group?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(groupMenu) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                        muscleGroups.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g.name) },
                                onClick = { group = g; groupMenu = false },
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = typeMenu, onExpandedChange = { typeMenu = it }) {
                    OutlinedTextField(
                        value = exerciseTypeLabel(type),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Exercise type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenu) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        ExerciseType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(exerciseTypeLabel(t)) },
                                onClick = { type = t; typeMenu = false },
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = unilateral, onCheckedChange = { unilateral = it })
                    Text(
                        "Single arm / single leg",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Text("Primary muscles", style = MaterialTheme.typography.labelLarge)
                MuscleChips(
                    selected = primary,
                    onToggle = { m ->
                        primary = if (m in primary) primary - m else primary + m
                        secondary = secondary - m // a muscle can't be both roles
                    },
                )
                Text("Secondary muscles", style = MaterialTheme.typography.labelLarge)
                MuscleChips(
                    selected = secondary,
                    onToggle = { m ->
                        secondary = if (m in secondary) secondary - m else secondary + m
                        primary = primary - m
                    },
                )
            }
        },
        confirmButton = {
            val g = group
            TextButton(
                enabled = name.isNotBlank() && g != null,
                onClick = { onCreate(name, g!!.id, type, unilateral, primary, secondary) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuscleChips(selected: Set<Muscle>, onToggle: (Muscle) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Muscle.entries.forEach { muscle ->
            FilterChip(
                selected = muscle in selected,
                onClick = { onToggle(muscle) },
                label = { Text(muscle.name.lowercase().replace('_', ' ')) },
            )
        }
    }
}
