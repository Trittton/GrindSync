package dev.gatsyuk.grindsync.feature.workout.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gatsyuk.grindsync.core.database.dao.ExerciseDao
import dev.gatsyuk.grindsync.core.database.dao.RoutineDao
import dev.gatsyuk.grindsync.core.database.entity.RoutineEntity
import dev.gatsyuk.grindsync.core.database.entity.RoutineExerciseEntity
import dev.gatsyuk.grindsync.core.model.TargetMode
import dev.gatsyuk.grindsync.feature.workout.components.ExercisePickerSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorEntry(
    val exerciseId: Long,
    val name: String,
    val targetSets: Int = 3,
    val repMin: Int? = 8,
    val repMax: Int? = 12,
)

@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routineDao: RoutineDao,
    exerciseDao: ExerciseDao,
) : ViewModel() {

    private val routineId: Long = savedStateHandle["routineId"] ?: -1L
    val isNew get() = routineId < 0

    val exerciseNames: StateFlow<Map<Long, String>> = exerciseDao.observeExercisesWithMuscles()
        .map { list -> list.associate { it.exercise.id to it.exercise.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val name = MutableStateFlow("")
    val notes = MutableStateFlow("")
    val entries = MutableStateFlow<List<EditorEntry>>(emptyList())
    private var loaded = false

    init {
        if (!isNew) {
            viewModelScope.launch {
                val routine = routineDao.getRoutineWithExercises(routineId) ?: return@launch
                if (loaded) return@launch
                loaded = true
                name.value = routine.routine.name
                notes.value = routine.routine.notes.orEmpty()
                // Names may not be in the map yet; join lazily in UI via exerciseNames.
                entries.value = routine.exercises.sortedBy { it.position }.map {
                    EditorEntry(it.exerciseId, "", it.targetSets, it.repMin, it.repMax)
                }
            }
        }
    }

    fun addExercise(exerciseId: Long) {
        entries.value = entries.value + EditorEntry(exerciseId, "")
    }

    fun update(index: Int, entry: EditorEntry) {
        entries.value = entries.value.toMutableList().also { it[index] = entry }
    }

    fun remove(index: Int) {
        entries.value = entries.value.toMutableList().also { it.removeAt(index) }
    }

    fun move(index: Int, delta: Int) {
        val list = entries.value.toMutableList()
        val target = index + delta
        if (target !in list.indices) return
        val item = list.removeAt(index)
        list.add(target, item)
        entries.value = list
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        // Target mode is always LATEST — FIXED was removed from the UI as a
        // do-nothing option (user feedback); the DB column stays for compat.
        val routineName = name.value.ifBlank { "Routine" }
        val id = if (isNew) {
            routineDao.insertRoutine(
                RoutineEntity(name = routineName, targetMode = TargetMode.LATEST, notes = notes.value.ifBlank { null }),
            )
        } else {
            val existing = routineDao.getRoutineWithExercises(routineId)?.routine
            routineDao.updateRoutine(
                (existing ?: RoutineEntity(id = routineId, name = routineName))
                    .copy(name = routineName, targetMode = TargetMode.LATEST, notes = notes.value.ifBlank { null }),
            )
            routineDao.deleteRoutineExercisesFor(routineId)
            routineId
        }
        routineDao.insertRoutineExercises(
            entries.value.mapIndexed { index, e ->
                RoutineExerciseEntity(
                    routineId = id,
                    exerciseId = e.exerciseId,
                    position = index,
                    targetSets = e.targetSets,
                    repMin = e.repMin,
                    repMax = e.repMax,
                )
            },
        )
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        if (!isNew) routineDao.deleteRoutineById(routineId)
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    onBack: () -> Unit,
    viewModel: RoutineEditorViewModel = hiltViewModel(),
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val exerciseNames by viewModel.exerciseNames.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New routine" else "Edit routine") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!viewModel.isNew) {
                        IconButton(onClick = { viewModel.delete(onBack) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete routine")
                        }
                    }
                    IconButton(onClick = { viewModel.save(onBack) }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.name.value = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { viewModel.notes.value = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "Starting this routine prefills each exercise from its last performance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            itemsIndexed(entries) { index, entry ->
                RoutineExerciseRow(
                    entry = entry,
                    displayName = exerciseNames[entry.exerciseId] ?: entry.name,
                    onUpdate = { viewModel.update(index, it) },
                    onRemove = { viewModel.remove(index) },
                    onMoveUp = { viewModel.move(index, -1) },
                    onMoveDown = { viewModel.move(index, +1) },
                )
            }

            item {
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Add exercise")
                }
            }
            item {
                TextButton(onClick = { viewModel.save(onBack) }, modifier = Modifier.fillMaxWidth()) {
                    Text("SAVE ROUTINE")
                }
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
}

@Composable
private fun RoutineExerciseRow(
    entry: EditorEntry,
    displayName: String,
    onUpdate: (EditorEntry) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    label = "Sets",
                    value = entry.targetSets,
                    onChange = { onUpdate(entry.copy(targetSets = it ?: 1)) },
                    modifier = Modifier.width(90.dp),
                )
                NumberField(
                    label = "Rep min",
                    value = entry.repMin,
                    onChange = { onUpdate(entry.copy(repMin = it)) },
                    modifier = Modifier.width(110.dp),
                )
                NumberField(
                    label = "Rep max",
                    value = entry.repMax,
                    onChange = { onUpdate(entry.copy(repMax = it)) },
                    modifier = Modifier.width(110.dp),
                )
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(value?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            if (input.isBlank()) onChange(null) else input.toIntOrNull()?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
