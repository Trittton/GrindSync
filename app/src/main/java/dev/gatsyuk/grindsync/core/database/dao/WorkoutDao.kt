package dev.gatsyuk.grindsync.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import dev.gatsyuk.grindsync.core.database.entity.ExerciseEntity
import dev.gatsyuk.grindsync.core.database.entity.SetEntryEntity
import dev.gatsyuk.grindsync.core.database.entity.WorkoutEntity
import dev.gatsyuk.grindsync.core.database.entity.WorkoutExerciseEntity
import kotlinx.coroutines.flow.Flow

data class WorkoutExerciseWithSets(
    @Embedded val workoutExercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "exercise_id", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workout_exercise_id")
    val sets: List<SetEntryEntity>,
)

data class WorkoutWithContent(
    @Embedded val workout: WorkoutEntity,
    @Relation(entity = WorkoutExerciseEntity::class, parentColumn = "id", entityColumn = "workout_id")
    val exercises: List<WorkoutExerciseWithSets>,
)

@Dao
interface WorkoutDao {

    // --- inserts ---
    @Insert suspend fun insertWorkout(workout: WorkoutEntity): Long
    @Insert suspend fun insertWorkoutExercise(entry: WorkoutExerciseEntity): Long
    @Insert suspend fun insertSetEntry(set: SetEntryEntity): Long

    // --- updates / deletes ---
    @Update suspend fun updateWorkout(workout: WorkoutEntity)
    @Update suspend fun updateSetEntry(set: SetEntryEntity)
    @Delete suspend fun deleteSetEntry(set: SetEntryEntity)
    @Delete suspend fun deleteWorkoutExercise(entry: WorkoutExerciseEntity)
    @Query("DELETE FROM workout WHERE id = :id")
    suspend fun deleteWorkoutById(id: Long)

    /** Prunes prefab rows the user never touched (no measurements, no note). */
    @Query(
        """
        DELETE FROM set_entry
        WHERE workout_exercise_id IN (SELECT id FROM workout_exercise WHERE workout_id = :workoutId)
          AND weight_kg IS NULL AND reps IS NULL AND time_seconds IS NULL
          AND distance_meters IS NULL AND kcal IS NULL AND notes IS NULL
        """,
    )
    suspend fun deleteEmptySets(workoutId: Long)

    // --- reads ---
    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun getWorkout(id: Long): WorkoutEntity?

    @Transaction
    @Query("SELECT * FROM workout WHERE id = :id")
    fun observeWorkoutWithContent(id: Long): Flow<WorkoutWithContent?>

    /** Completed sessions, newest first — the Log/History list. */
    @Transaction
    @Query("SELECT * FROM workout WHERE end_time IS NOT NULL ORDER BY date DESC, id DESC")
    fun observeCompletedWorkouts(): Flow<List<WorkoutWithContent>>

    /** The single live session, if any (end_time not yet set). */
    @Query("SELECT * FROM workout WHERE end_time IS NULL ORDER BY id DESC LIMIT 1")
    fun observeInProgressWorkout(): Flow<WorkoutEntity?>

    @Query("SELECT COUNT(*) FROM workout")
    suspend fun countWorkouts(): Int

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM workout_exercise WHERE workout_id = :workoutId")
    suspend fun nextExercisePosition(workoutId: Long): Int

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM set_entry WHERE workout_exercise_id = :workoutExerciseId")
    suspend fun nextSetPosition(workoutExerciseId: Long): Int

    /**
     * Sets from the most recent COMPLETED performance of an exercise —
     * powers routine TargetMode.LATEST prefill and "same as last time" hints.
     */
    @Query(
        """
        SELECT s.* FROM set_entry s
        WHERE s.workout_exercise_id = (
            SELECT we.id FROM workout_exercise we
            JOIN workout w ON we.workout_id = w.id
            WHERE we.exercise_id = :exerciseId AND w.end_time IS NOT NULL
            ORDER BY w.date DESC, we.id DESC LIMIT 1
        )
        ORDER BY s.position
        """,
    )
    suspend fun getLastPerformedSets(exerciseId: Long): List<SetEntryEntity>
}
