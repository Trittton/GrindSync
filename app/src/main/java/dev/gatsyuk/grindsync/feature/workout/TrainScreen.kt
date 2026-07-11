package dev.gatsyuk.grindsync.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gatsyuk.grindsync.core.database.dao.ExerciseDao
import dev.gatsyuk.grindsync.core.database.dao.ExerciseWithMuscles
import dev.gatsyuk.grindsync.core.database.dao.RoutineDao
import dev.gatsyuk.grindsync.core.database.dao.RoutineWithExercises
import dev.gatsyuk.grindsync.core.model.MuscleRole
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Phase 0 placeholder for the Train tab. The seed-catalog listing below is a
 * TEMPORARY debug surface proving the schema + seed are queryable end-to-end;
 * it is replaced by History ⇄ Routines in Phase 1.
 */
@HiltViewModel
class TrainDebugViewModel @Inject constructor(
    exerciseDao: ExerciseDao,
    routineDao: RoutineDao,
) : ViewModel() {
    val exercises = exerciseDao.observeExercisesWithMuscles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val routines = routineDao.observeRoutinesWithExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun TrainScreen(viewModel: TrainDebugViewModel = hiltViewModel()) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Train",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Text(
                "Phase 0 debug view — seeded catalog (${exercises.size} exercises, " +
                    "${routines.size} routine template).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(routines, key = { "routine-${it.routine.id}" }) { routine ->
            RoutineCard(routine)
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

        items(exercises, key = { "exercise-${it.exercise.id}" }) { exercise ->
            ExerciseCard(exercise)
        }
    }
}

@Composable
private fun RoutineCard(routine: RoutineWithExercises) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(routine.routine.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "Targets: ${routine.routine.targetMode} · ${routine.exercises.size} exercises",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            routine.exercises.sortedBy { it.position }.forEach { entry ->
                val range = if (entry.repMin != null && entry.repMax != null) {
                    "${entry.targetSets} sets, ${entry.repMin}–${entry.repMax} reps"
                } else {
                    "${entry.targetSets} sets"
                }
                Text("• exercise #${entry.exerciseId}: $range", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ExerciseCard(item: ExerciseWithMuscles) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(item.exercise.name, style = MaterialTheme.typography.titleSmall)
            Text(
                item.exercise.exerciseType.name +
                    if (item.exercise.isUnilateral) " · unilateral" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val primary = item.muscles.filter { it.role == MuscleRole.PRIMARY }
            val secondary = item.muscles.filter { it.role == MuscleRole.SECONDARY }
            if (primary.isNotEmpty() || secondary.isNotEmpty()) {
                Text(
                    buildString {
                        if (primary.isNotEmpty()) {
                            append("P: ${primary.joinToString { "${it.muscle} (${it.contributionWeight})" }}")
                        }
                        if (secondary.isNotEmpty()) {
                            if (isNotEmpty()) append("  ·  ")
                            append("S: ${secondary.joinToString { "${it.muscle} (${it.contributionWeight})" }}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
