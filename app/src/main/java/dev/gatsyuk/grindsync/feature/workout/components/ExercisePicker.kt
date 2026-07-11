package dev.gatsyuk.grindsync.feature.workout.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gatsyuk.grindsync.core.database.dao.ExerciseDao
import dev.gatsyuk.grindsync.core.database.entity.ExerciseEntity
import dev.gatsyuk.grindsync.core.database.entity.ExerciseMuscleEntity
import dev.gatsyuk.grindsync.core.model.ContributionWeights
import dev.gatsyuk.grindsync.core.model.ExerciseType
import dev.gatsyuk.grindsync.core.model.Muscle
import dev.gatsyuk.grindsync.core.model.MuscleRole
import dev.gatsyuk.grindsync.core.ui.exerciseTypeLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseCatalogViewModel @Inject constructor(
    private val exerciseDao: ExerciseDao,
) : ViewModel() {

    val exercises = exerciseDao.observeExercisesWithMuscles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val muscleGroups = exerciseDao.observeMuscleGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createCustomExercise(
        name: String,
        muscleGroupId: Long,
        type: ExerciseType,
        unilateral: Boolean,
        primary: Set<Muscle>,
        secondary: Set<Muscle>,
        onCreated: (Long) -> Unit,
    ) = viewModelScope.launch {
        val id = exerciseDao.insertExercise(
            ExerciseEntity(
                name = name.trim(),
                muscleGroupId = muscleGroupId,
                exerciseType = type,
                isUnilateral = unilateral,
                isCustom = true,
            ),
        )
        exerciseDao.insertExerciseMuscles(
            primary.map { ExerciseMuscleEntity(id, it, MuscleRole.PRIMARY, ContributionWeights.PRIMARY) } +
                secondary.map { ExerciseMuscleEntity(id, it, MuscleRole.SECONDARY, ContributionWeights.SECONDARY) },
        )
        onCreated(id)
    }
}

/** Searchable catalog sheet; "New exercise" opens the custom-exercise form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    viewModel: ExerciseCatalogViewModel = hiltViewModel(),
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val groups by viewModel.muscleGroups.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search exercises") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ListItem(
                headlineContent = { Text("New exercise") },
                leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                modifier = Modifier.clickable { showForm = true },
            )
            val filtered = exercises.filter {
                query.isBlank() || it.exercise.name.contains(query, ignoreCase = true)
            }
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(filtered, key = { it.exercise.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.exercise.name) },
                        supportingContent = {
                            Text(
                                exerciseTypeLabel(item.exercise.exerciseType),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        modifier = Modifier.clickable { onPick(item.exercise.id) },
                    )
                }
            }
        }
    }

    if (showForm) {
        ExerciseFormDialog(
            muscleGroups = groups,
            onDismiss = { showForm = false },
            onCreate = { name, groupId, type, unilateral, primary, secondary ->
                viewModel.createCustomExercise(name, groupId, type, unilateral, primary, secondary) {
                    showForm = false
                    onPick(it)
                }
            },
        )
    }
}
