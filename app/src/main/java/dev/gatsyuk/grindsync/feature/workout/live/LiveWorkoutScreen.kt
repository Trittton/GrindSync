package dev.gatsyuk.grindsync.feature.workout.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gatsyuk.grindsync.core.database.dao.WorkoutExerciseWithSets
import dev.gatsyuk.grindsync.core.database.entity.SetEntryEntity
import dev.gatsyuk.grindsync.core.model.SetField
import dev.gatsyuk.grindsync.core.model.SetKind
import dev.gatsyuk.grindsync.core.model.WeightUnit
import dev.gatsyuk.grindsync.core.model.Weights
import dev.gatsyuk.grindsync.core.model.formatDurationMillis
import dev.gatsyuk.grindsync.core.model.formatSeconds
import dev.gatsyuk.grindsync.core.ui.exerciseTypeLabel
import dev.gatsyuk.grindsync.feature.workout.components.ExercisePickerSheet
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Live logging + completed-workout detail (same surface; a finished workout
 * stays editable — it's the user's own data). Set rows render ONLY the fields
 * valid for the exercise's type (SPEC §6.3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveWorkoutScreen(
    onBack: () -> Unit,
    viewModel: LiveWorkoutViewModel = hiltViewModel(),
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    val unit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val lastSets by viewModel.lastSessionSets.collectAsStateWithLifecycle()
    val restRemaining by viewModel.restRemaining.collectAsStateWithLifecycle()
    val restDefault by viewModel.restDefaultSeconds.collectAsStateWithLifecycle()

    var showPicker by remember { mutableStateOf(false) }
    var showRestDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val workout = content?.workout

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workout?.name ?: "…") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val remaining = restRemaining
                    if (remaining != null) {
                        AssistChip(
                            onClick = { showRestDialog = true },
                            label = { Text(formatSeconds(remaining)) },
                            leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                        )
                    } else {
                        // One tap = start resting with the Settings default.
                        // The running chip opens stop/adjust options.
                        IconButton(onClick = { viewModel.startRest(restDefault) }) {
                            Icon(Icons.Default.Timer, contentDescription = "Start rest timer")
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete workout") },
                            onClick = { menuOpen = false; showDeleteConfirm = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        val current = content
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                WorkoutHeader(
                    workout = current.workout,
                    unit = unit,
                    onUpdate = viewModel::updateWorkout,
                    onFinish = { viewModel.finish(onBack) },
                )
            }

            current.exercises.sortedBy { it.workoutExercise.position }.forEach { entry ->
                item(key = "exercise-${entry.workoutExercise.id}") {
                    ExerciseSection(
                        entry = entry,
                        unit = unit,
                        lastSessionSets = lastSets[entry.exercise.id].orEmpty(),
                        onAddSet = { viewModel.addSet(entry) },
                        onUpdateSet = { viewModel.updateSet(entry, it) },
                        onDeleteSet = viewModel::deleteSet,
                        onRemoveExercise = { viewModel.removeExercise(entry) },
                    )
                }
            }

            item(key = "add-exercise") {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                ) { Text("+ Add exercise") }
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            onDismiss = { showPicker = false },
            onPick = { id ->
                showPicker = false
                viewModel.addExercise(id)
            },
        )
    }

    if (showRestDialog) {
        RestTimerDialog(
            running = restRemaining != null,
            defaultSeconds = restDefault,
            onStart = { viewModel.startRest(it); showRestDialog = false },
            onStop = { viewModel.stopRest(); showRestDialog = false },
            onDismiss = { showRestDialog = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete workout?") },
            text = { Text("This removes the session and all its sets. There is no undo yet.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteWorkout(onBack) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun WorkoutHeader(
    workout: dev.gatsyuk.grindsync.core.database.entity.WorkoutEntity,
    unit: WeightUnit,
    onUpdate: (dev.gatsyuk.grindsync.core.database.entity.WorkoutEntity) -> Unit,
    onFinish: () -> Unit,
) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEE, MMM d yyyy", Locale.ENGLISH) }
    var name by remember(workout.id) { mutableStateOf(workout.name) }
    var bodyweight by remember(workout.id) {
        mutableStateOf(workout.bodyweightKg?.let { Weights.formatKgAs(it, unit) } ?: "")
    }
    var notes by remember(workout.id) { mutableStateOf(workout.notes ?: "") }
    val inProgress = workout.endTimeEpochMillis == null

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (it.isNotBlank()) onUpdate(workout.copy(name = it.trim()))
                },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    workout.date.format(dateFormat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!inProgress) {
                    val duration = workout.startTimeEpochMillis?.let { s ->
                        workout.endTimeEpochMillis?.let { e -> formatDurationMillis(e - s) }
                    }
                    Text(
                        "Completed${duration?.let { " · $it" } ?: ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = bodyweight,
                    onValueChange = { text ->
                        bodyweight = text
                        val parsed = text.replace(',', '.').toDoubleOrNull()
                        onUpdate(workout.copy(bodyweightKg = parsed?.let { Weights.displayToKg(it, unit) }))
                    },
                    label = { Text("BW (${Weights.unitLabel(unit)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = {
                        notes = it
                        onUpdate(workout.copy(notes = it.ifBlank { null }))
                    },
                    label = { Text("Notes") },
                    modifier = Modifier.weight(2f),
                )
            }
            if (inProgress) {
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text("FINISH WORKOUT")
                }
            }
        }
    }
}

@Composable
private fun ExerciseSection(
    entry: WorkoutExerciseWithSets,
    unit: WeightUnit,
    lastSessionSets: List<SetEntryEntity>,
    onAddSet: () -> Unit,
    onUpdateSet: (SetEntryEntity) -> Unit,
    onDeleteSet: (SetEntryEntity) -> Unit,
    onRemoveExercise: () -> Unit,
) {
    val fields = remember(entry.exercise.exerciseType) {
        entry.exercise.exerciseType.fields.sortedBy { it.ordinal }
    }
    var sectionMenu by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.exercise.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        exerciseTypeLabel(entry.exercise.exerciseType),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { sectionMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Exercise options")
                    }
                    DropdownMenu(expanded = sectionMenu, onDismissRequest = { sectionMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Remove exercise") },
                            onClick = { sectionMenu = false; onRemoveExercise() },
                        )
                    }
                }
            }

            if (entry.sets.isEmpty() && lastSessionSets.isNotEmpty()) {
                Text(
                    "Last time: " + lastSessionSets.joinToString { setSummary(it, unit) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (entry.sets.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
                    Spacer(Modifier.size(34.dp))
                    fields.forEach { field ->
                        Text(
                            fieldLabel(field, unit),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.size(40.dp))
                }
            }

            val ordered = entry.sets.sortedBy { it.position }
            var workingIndex = 0
            ordered.forEach { set ->
                if (set.setKind == SetKind.WORKING) workingIndex++
                SetRow(
                    set = set,
                    workingNumber = if (set.setKind == SetKind.WORKING) workingIndex else null,
                    fields = fields,
                    unit = unit,
                    onUpdate = onUpdateSet,
                    onDelete = { onDeleteSet(set) },
                )
            }

            TextButton(onClick = onAddSet) { Text("+ Add set") }
        }
    }
}

/** One set. Marker tap cycles WORKING → WARMUP → DROPSET; menu has notes/delete. */
@Composable
private fun SetRow(
    set: SetEntryEntity,
    workingNumber: Int?,
    fields: List<SetField>,
    unit: WeightUnit,
    onUpdate: (SetEntryEntity) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var notesDialog by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        val markerColor = when (set.setKind) {
            SetKind.WORKING -> MaterialTheme.colorScheme.primary
            SetKind.WARMUP -> MaterialTheme.colorScheme.onSurfaceVariant
            SetKind.DROPSET -> MaterialTheme.colorScheme.tertiary
        }
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, markerColor),
            modifier = Modifier.size(28.dp),
            onClick = {
                val next = when (set.setKind) {
                    SetKind.WORKING -> SetKind.WARMUP
                    SetKind.WARMUP -> SetKind.DROPSET
                    SetKind.DROPSET -> SetKind.WORKING
                }
                onUpdate(set.copy(setKind = next))
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    when (set.setKind) {
                        SetKind.WORKING -> workingNumber?.toString() ?: ""
                        SetKind.WARMUP -> "W"
                        SetKind.DROPSET -> "D"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = markerColor,
                )
            }
        }

        fields.forEach { field ->
            MeasurementField(
                set = set,
                field = field,
                unit = unit,
                onUpdate = onUpdate,
                modifier = Modifier.weight(1f),
            )
        }

        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Set options",
                    tint = if (set.notes != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (set.notes == null) "Add note" else "Edit note") },
                    onClick = { menu = false; notesDialog = true },
                )
                DropdownMenuItem(text = { Text("Delete set") }, onClick = { menu = false; onDelete() })
            }
        }
    }

    if (notesDialog) {
        var text by remember { mutableStateOf(set.notes ?: "") }
        AlertDialog(
            onDismissRequest = { notesDialog = false },
            title = { Text("Set note") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(set.copy(notes = text.ifBlank { null }))
                    notesDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { notesDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MeasurementField(
    set: SetEntryEntity,
    field: SetField,
    unit: WeightUnit,
    onUpdate: (SetEntryEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(set.id, field) { mutableStateOf(initialText(set, field, unit)) }

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            val normalized = input.replace(',', '.')
            val updated: SetEntryEntity? = when (field) {
                SetField.WEIGHT -> {
                    val v = normalized.toDoubleOrNull()
                    if (normalized.isBlank() || v != null) {
                        set.copy(weightKg = v?.let { Weights.displayToKg(it, unit) })
                    } else null
                }
                SetField.REPS -> intUpdate(normalized) { set.copy(reps = it) }
                SetField.TIME -> intUpdate(normalized) { set.copy(timeSeconds = it) }
                SetField.DISTANCE -> {
                    val v = normalized.toDoubleOrNull()
                    if (normalized.isBlank() || v != null) set.copy(distanceMeters = v) else null
                }
                SetField.KCAL -> intUpdate(normalized) { set.copy(kcal = it) }
            }
            updated?.let(onUpdate)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (field == SetField.WEIGHT || field == SetField.DISTANCE) {
                KeyboardType.Decimal
            } else {
                KeyboardType.Number
            },
        ),
        modifier = modifier,
    )
}

private fun intUpdate(text: String, apply: (Int?) -> SetEntryEntity): SetEntryEntity? {
    val v = text.toIntOrNull()
    return if (text.isBlank() || v != null) apply(v) else null
}

private fun initialText(set: SetEntryEntity, field: SetField, unit: WeightUnit): String = when (field) {
    SetField.WEIGHT -> set.weightKg?.let { Weights.formatKgAs(it, unit) } ?: ""
    SetField.REPS -> set.reps?.toString() ?: ""
    SetField.TIME -> set.timeSeconds?.toString() ?: ""
    SetField.DISTANCE -> set.distanceMeters?.let { Weights.format(it) } ?: ""
    SetField.KCAL -> set.kcal?.toString() ?: ""
}

private fun fieldLabel(field: SetField, unit: WeightUnit): String = when (field) {
    SetField.WEIGHT -> Weights.unitLabel(unit).uppercase()
    SetField.REPS -> "REPS"
    SetField.TIME -> "TIME (s)"
    SetField.DISTANCE -> "DIST (m)"
    SetField.KCAL -> "KCAL"
}

private fun setSummary(set: SetEntryEntity, unit: WeightUnit): String {
    val parts = buildList {
        set.weightKg?.let { add(Weights.formatKgAs(it, unit)) }
        set.reps?.let { add("×$it") }
        set.timeSeconds?.let { add(formatSeconds(it)) }
        set.distanceMeters?.let { add("${Weights.format(it)}m") }
        set.kcal?.let { add("${it}kcal") }
    }
    return parts.joinToString("")
}

@Composable
private fun RestTimerDialog(
    running: Boolean,
    defaultSeconds: Int,
    onStart: (Int) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rest timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(60, 90, 120, 180).forEach { seconds ->
                    TextButton(onClick = { onStart(seconds) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            formatSeconds(seconds) + if (seconds == defaultSeconds) "  (default)" else "",
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (running) TextButton(onClick = onStop) { Text("Stop") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
