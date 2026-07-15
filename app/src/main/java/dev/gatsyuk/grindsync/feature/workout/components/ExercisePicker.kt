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

    fun createCustomExercise(form: ExerciseFormResult, onCreated: (Long) -> Unit) =
        viewModelScope.launch {
            val id = exerciseDao.insertExercise(
                ExerciseEntity(
                    name = form.name,
                    muscleGroupId = form.muscleGroupId,
                    exerciseType = form.type,
                    isUnilateral = form.unilateral,
                    isCustom = true,
                    defaultWarmupSets = form.defaultWarmupSets,
                ),
            )
            exerciseDao.insertExerciseMuscles(formMuscles(id, form))
            onCreated(id)
        }
}

internal fun formMuscles(exerciseId: Long, form: ExerciseFormResult): List<ExerciseMuscleEntity> =
    form.primary.map {
        ExerciseMuscleEntity(exerciseId, it, MuscleRole.PRIMARY, ContributionWeights.PRIMARY)
    } + form.secondary.map {
        ExerciseMuscleEntity(exerciseId, it, MuscleRole.SECONDARY, ContributionWeights.SECONDARY)
    }

/**
 * Searchable catalog sheet. "New exercise" hands the query back to the CALLER
 * via [onCreateNew] — the form dialog must be hosted at screen level, because
 * a modal sheet does not survive rotation and would take the form (and the
 * user's typing) down with it (bug report).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onCreateNew: (String) -> Unit,
    viewModel: ExerciseCatalogViewModel = hiltViewModel(),
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    // rememberSaveable: rotation mid-search must not reset the flow (bug report).
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }

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
                // Typed a search with no match? It becomes the new exercise's name.
                modifier = Modifier.clickable { onCreateNew(query) },
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

}
