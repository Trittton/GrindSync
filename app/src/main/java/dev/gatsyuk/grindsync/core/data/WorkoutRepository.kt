package dev.gatsyuk.grindsync.core.data

import dev.gatsyuk.grindsync.core.database.dao.RoutineDao
import dev.gatsyuk.grindsync.core.database.dao.WorkoutDao
import dev.gatsyuk.grindsync.core.database.entity.SetEntryEntity
import dev.gatsyuk.grindsync.core.database.entity.WorkoutEntity
import dev.gatsyuk.grindsync.core.database.entity.WorkoutExerciseEntity
import dev.gatsyuk.grindsync.core.model.ExerciseType
import dev.gatsyuk.grindsync.core.model.SetKind
import dev.gatsyuk.grindsync.core.model.SetValidation
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Write-side of the workout domain. This is the layer that enforces the
 * ExerciseType field gating (SetValidation) the DB deliberately does not.
 */
@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val routineDao: RoutineDao,
) {

    suspend fun startEmptyWorkout(name: String = "Workout"): Long =
        workoutDao.insertWorkout(
            WorkoutEntity(
                name = name,
                date = LocalDate.now(),
                startTimeEpochMillis = System.currentTimeMillis(),
            ),
        )

    /**
     * START on a routine: instantiate a live workout with its exercises AND
     * the target number of set rows per exercise, prefilled from the last
     * performance (TargetMode LATEST). Untouched rows are pruned on finish.
     */
    suspend fun startFromRoutine(routineId: Long): Long {
        val routine = requireNotNull(routineDao.getRoutineWithExercises(routineId)) {
            "Routine $routineId not found"
        }
        val workoutId = workoutDao.insertWorkout(
            WorkoutEntity(
                name = routine.routine.name,
                date = LocalDate.now(),
                startTimeEpochMillis = System.currentTimeMillis(),
                sourceRoutineId = routineId,
            ),
        )
        routine.exercises.sortedBy { it.position }.forEachIndexed { index, entry ->
            val workoutExerciseId = workoutDao.insertWorkoutExercise(
                WorkoutExerciseEntity(
                    workoutId = workoutId,
                    exerciseId = entry.exerciseId,
                    position = index,
                ),
            )
            prefillSets(workoutExerciseId, entry.exerciseId, entry.targetSets)
        }
        return workoutId
    }

    /** Adding an exercise mid-workout mirrors its last session (count + values). */
    suspend fun addExercise(workoutId: Long, exerciseId: Long): Long {
        val workoutExerciseId = workoutDao.insertWorkoutExercise(
            WorkoutExerciseEntity(
                workoutId = workoutId,
                exerciseId = exerciseId,
                position = workoutDao.nextExercisePosition(workoutId),
            ),
        )
        val lastCount = workoutDao.getLastPerformedSets(exerciseId).size
        if (lastCount > 0) prefillSets(workoutExerciseId, exerciseId, lastCount)
        return workoutExerciseId
    }

    private suspend fun prefillSets(workoutExerciseId: Long, exerciseId: Long, count: Int) {
        val last = workoutDao.getLastPerformedSets(exerciseId)
        repeat(count.coerceAtLeast(0)) { index ->
            val template = last.getOrNull(index) ?: last.lastOrNull()
            workoutDao.insertSetEntry(
                SetEntryEntity(
                    workoutExerciseId = workoutExerciseId,
                    position = index,
                    setKind = template?.setKind ?: SetKind.WORKING,
                    weightKg = template?.weightKg,
                    reps = template?.reps,
                    timeSeconds = template?.timeSeconds,
                    distanceMeters = template?.distanceMeters,
                    kcal = template?.kcal,
                ),
            )
        }
    }

    /**
     * Insert a set after validating its fields against the exercise type.
     * Policy: fields OUTSIDE the type's set are hard-rejected (returns null);
     * an all-empty row is allowed because a set exists while it is being
     * typed in — emptiness is judged at analytics time, not entry time.
     */
    suspend fun addSet(
        workoutExerciseId: Long,
        type: ExerciseType,
        candidate: SetValidation.Candidate,
        kind: SetKind = SetKind.WORKING,
        notes: String? = null,
    ): Long? {
        val result = SetValidation.validate(type, candidate)
        if (result is SetValidation.Result.Invalid && result.disallowed.isNotEmpty()) return null
        return workoutDao.insertSetEntry(
            SetEntryEntity(
                workoutExerciseId = workoutExerciseId,
                position = workoutDao.nextSetPosition(workoutExerciseId),
                setKind = kind,
                weightKg = candidate.weightKg,
                reps = candidate.reps,
                timeSeconds = candidate.timeSeconds,
                distanceMeters = candidate.distanceMeters,
                kcal = candidate.kcal,
                notes = notes,
            ),
        )
    }

    /** Update measurements only if they stay valid for the type; metadata always updates. */
    suspend fun updateSet(type: ExerciseType, updated: SetEntryEntity): Boolean {
        val candidate = SetValidation.Candidate(
            weightKg = updated.weightKg,
            reps = updated.reps,
            timeSeconds = updated.timeSeconds,
            distanceMeters = updated.distanceMeters,
            kcal = updated.kcal,
        )
        // An all-empty row is allowed to exist while being edited; gate only extras.
        val result = SetValidation.validate(type, candidate)
        val invalidExtras = result is SetValidation.Result.Invalid && result.disallowed.isNotEmpty()
        if (invalidExtras) return false
        workoutDao.updateSetEntry(updated)
        return true
    }

    suspend fun deleteSet(set: SetEntryEntity) = workoutDao.deleteSetEntry(set)

    suspend fun removeExercise(entry: WorkoutExerciseEntity) =
        workoutDao.deleteWorkoutExercise(entry)

    suspend fun finishWorkout(workoutId: Long) {
        val workout = workoutDao.getWorkout(workoutId) ?: return
        if (workout.endTimeEpochMillis == null) {
            // Drop prefab rows the user never filled in, then close the session.
            workoutDao.deleteEmptySets(workoutId)
            workoutDao.updateWorkout(workout.copy(endTimeEpochMillis = System.currentTimeMillis()))
        }
    }

    suspend fun updateWorkout(workout: WorkoutEntity) = workoutDao.updateWorkout(workout)

    suspend fun deleteWorkout(workoutId: Long) = workoutDao.deleteWorkoutById(workoutId)

    suspend fun lastPerformedSets(exerciseId: Long): List<SetEntryEntity> =
        workoutDao.getLastPerformedSets(exerciseId)
}
