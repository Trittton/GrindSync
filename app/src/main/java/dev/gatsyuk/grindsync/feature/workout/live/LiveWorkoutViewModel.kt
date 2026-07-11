package dev.gatsyuk.grindsync.feature.workout.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gatsyuk.grindsync.core.data.WorkoutRepository
import dev.gatsyuk.grindsync.core.database.dao.WorkoutDao
import dev.gatsyuk.grindsync.core.database.dao.WorkoutExerciseWithSets
import dev.gatsyuk.grindsync.core.database.entity.SetEntryEntity
import dev.gatsyuk.grindsync.core.database.entity.WorkoutEntity
import dev.gatsyuk.grindsync.core.datastore.UserPreferencesRepository
import dev.gatsyuk.grindsync.core.model.SetKind
import dev.gatsyuk.grindsync.core.model.SetValidation
import dev.gatsyuk.grindsync.core.model.WeightUnit
import dev.gatsyuk.grindsync.core.model.Weights
import dev.gatsyuk.grindsync.core.model.formatSeconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    workoutDao: WorkoutDao,
    private val repository: WorkoutRepository,
    private val prefs: UserPreferencesRepository,
    private val restTimer: RestTimer,
) : ViewModel() {

    val workoutId: Long = checkNotNull(savedStateHandle["workoutId"])

    val content = workoutDao.observeWorkoutWithContent(workoutId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weightUnit = prefs.weightUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeightUnit.KG)

    val restDefaultSeconds = prefs.restTimerSeconds
        .stateIn(viewModelScope, SharingStarted.Eagerly, 120)

    val restRemaining: StateFlow<Int?> = restTimer.secondsRemaining

    /** exerciseId -> sets from the last completed session (prefill hints). */
    private val _lastSessionSets = MutableStateFlow<Map<Long, List<SetEntryEntity>>>(emptyMap())
    val lastSessionSets: StateFlow<Map<Long, List<SetEntryEntity>>> = _lastSessionSets

    init {
        viewModelScope.launch {
            content.collect { workout ->
                val missing = workout?.exercises
                    ?.map { it.exercise.id }
                    ?.filter { it !in _lastSessionSets.value }
                    .orEmpty()
                if (missing.isNotEmpty()) {
                    val fetched = missing.associateWith { repository.lastPerformedSets(it) }
                    _lastSessionSets.value = _lastSessionSets.value + fetched
                }
            }
        }
    }

    // --- workout header ---

    fun updateWorkout(workout: WorkoutEntity) = viewModelScope.launch {
        repository.updateWorkout(workout)
    }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        restTimer.stop()
        repository.finishWorkout(workoutId)
        onDone()
    }

    fun deleteWorkout(onDone: () -> Unit) = viewModelScope.launch {
        restTimer.stop()
        repository.deleteWorkout(workoutId)
        onDone()
    }

    /** "Repeat Workout": fresh live session cloned from this one. */
    fun repeatWorkout(onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(repository.repeatWorkout(workoutId))
    }

    /** "Save as Routine": template from this session's exercises and set counts. */
    fun saveAsRoutine(onDone: () -> Unit) = viewModelScope.launch {
        repository.saveAsRoutine(workoutId)
        onDone()
    }

    /** Human-readable share text, weights in the display unit. */
    fun buildShareText(): String? {
        val current = content.value ?: return null
        val unit = weightUnit.value
        val sb = StringBuilder()
        sb.appendLine("${current.workout.name} — ${current.workout.date}")
        current.exercises.sortedBy { it.workoutExercise.position }.forEach { entry ->
            if (entry.sets.isEmpty()) return@forEach
            val sets = entry.sets.sortedBy { it.position }.joinToString { set ->
                buildString {
                    set.weightKg?.let { append(Weights.formatKgAs(it, unit)) }
                    set.reps?.let { append("×$it") }
                    set.timeSeconds?.let { append(formatSeconds(it)) }
                    set.distanceMeters?.let { append("${Weights.format(it)}m") }
                    set.kcal?.let { append("${it}kcal") }
                    if (isEmpty()) append("—")
                }
            }
            sb.appendLine("${entry.exercise.name}: $sets")
        }
        val totalSets = current.exercises.sumOf { it.sets.size }
        sb.append("$totalSets sets · logged with GrindSync")
        return sb.toString()
    }

    // --- exercises & sets ---

    fun addExercise(exerciseId: Long) = viewModelScope.launch {
        repository.addExercise(workoutId, exerciseId)
    }

    fun removeExercise(entry: WorkoutExerciseWithSets) = viewModelScope.launch {
        repository.removeExercise(entry.workoutExercise)
    }

    /** New set prefilled from this session's previous set, else last session's set at that index. */
    fun addSet(entry: WorkoutExerciseWithSets) = viewModelScope.launch {
        val ordered = entry.sets.sortedBy { it.position }
        val template = ordered.lastOrNull()
            ?: _lastSessionSets.value[entry.exercise.id]?.getOrNull(ordered.size)
        repository.addSet(
            workoutExerciseId = entry.workoutExercise.id,
            type = entry.exercise.exerciseType,
            candidate = SetValidation.Candidate(
                weightKg = template?.weightKg,
                reps = template?.reps,
                timeSeconds = template?.timeSeconds,
                distanceMeters = template?.distanceMeters,
                kcal = template?.kcal,
            ),
            kind = template?.setKind ?: SetKind.WORKING,
        )
    }

    fun updateSet(entry: WorkoutExerciseWithSets, updated: SetEntryEntity) = viewModelScope.launch {
        repository.updateSet(entry.exercise.exerciseType, updated)
    }

    fun deleteSet(set: SetEntryEntity) = viewModelScope.launch {
        repository.deleteSet(set)
    }

    // --- rest timer ---

    fun startRest(seconds: Int) {
        restTimer.start(seconds)
        viewModelScope.launch { prefs.setRestTimerSeconds(seconds) }
    }

    fun stopRest() = restTimer.stop()
}
