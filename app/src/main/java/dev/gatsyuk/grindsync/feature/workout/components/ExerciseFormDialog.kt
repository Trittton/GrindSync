package dev.gatsyuk.grindsync.feature.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.gatsyuk.grindsync.core.database.dao.ExerciseWithMuscles
import dev.gatsyuk.grindsync.core.database.entity.MuscleGroupEntity
import dev.gatsyuk.grindsync.core.model.ExerciseType
import dev.gatsyuk.grindsync.core.model.Muscle
import dev.gatsyuk.grindsync.core.model.MuscleRole
import dev.gatsyuk.grindsync.core.ui.exerciseTypeLabel

/** What the form hands back on save. */
data class ExerciseFormResult(
    val name: String,
    val muscleGroupId: Long,
    val type: ExerciseType,
    val unilateral: Boolean,
    val defaultWarmupSets: Int,
    val primary: Set<Muscle>,
    val secondary: Set<Muscle>,
)

private val muscleSetSaver = listSaver<Set<Muscle>, String>(
    save = { set -> set.map { it.name } },
    restore = { names -> names.map(Muscle::valueOf).toSet() },
)

/**
 * Create OR edit an exercise. All state is rememberSaveable, so rotation
 * mid-typing keeps every field (user bug report). Muscle mapping is captured
 * here so the ExerciseMuscle join never lags the catalog (SPEC §7.7).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseFormDialog(
    muscleGroups: List<MuscleGroupEntity>,
    existing: ExerciseWithMuscles? = null,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (ExerciseFormResult) -> Unit,
) {
    val isEdit = existing != null
    var name by rememberSaveable { mutableStateOf(existing?.exercise?.name ?: initialName) }
    var groupId by rememberSaveable { mutableStateOf(existing?.exercise?.muscleGroupId ?: -1L) }
    var typeName by rememberSaveable {
        mutableStateOf((existing?.exercise?.exerciseType ?: ExerciseType.STRENGTH_WEIGHT_REPS).name)
    }
    var unilateral by rememberSaveable { mutableStateOf(existing?.exercise?.isUnilateral ?: false) }
    var warmups by rememberSaveable {
        mutableStateOf((existing?.exercise?.defaultWarmupSets ?: 0).toString())
    }
    var primary by rememberSaveable(stateSaver = muscleSetSaver) {
        mutableStateOf(
            existing?.muscles?.filter { it.role == MuscleRole.PRIMARY }?.map { it.muscle }?.toSet()
                ?: emptySet(),
        )
    }
    var secondary by rememberSaveable(stateSaver = muscleSetSaver) {
        mutableStateOf(
            existing?.muscles?.filter { it.role == MuscleRole.SECONDARY }?.map { it.muscle }?.toSet()
                ?: emptySet(),
        )
    }
    var groupMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }

    val type = ExerciseType.valueOf(typeName)
    val group = muscleGroups.firstOrNull { it.id == groupId }
    val warmupCount = warmups.toIntOrNull()
    val valid = name.isNotBlank() && group != null && warmupCount != null && warmupCount >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit exercise" else "New exercise") },
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
                                onClick = { groupId = g.id; groupMenu = false },
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
                                onClick = { typeName = t.name; typeMenu = false },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = warmups,
                    onValueChange = { warmups = it },
                    label = { Text("Warmup sets") },
                    supportingText = { Text("Pre-marked as W when you start training this exercise.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

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
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        ExerciseFormResult(
                            name = name.trim(),
                            muscleGroupId = group!!.id,
                            type = type,
                            unilateral = unilateral,
                            defaultWarmupSets = warmupCount ?: 0,
                            primary = primary,
                            secondary = secondary,
                        ),
                    )
                },
            ) { Text(if (isEdit) "Save" else "Create") }
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
