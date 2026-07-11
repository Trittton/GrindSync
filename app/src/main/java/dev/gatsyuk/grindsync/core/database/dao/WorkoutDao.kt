package dev.gatsyuk.grindsync.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.gatsyuk.grindsync.core.database.entity.SetEntryEntity
import dev.gatsyuk.grindsync.core.database.entity.WorkoutEntity
import dev.gatsyuk.grindsync.core.database.entity.WorkoutExerciseEntity
import kotlinx.coroutines.flow.Flow

/** Phase 0 keeps this minimal; the live-logging surface arrives in Phase 1. */
@Dao
interface WorkoutDao {

    @Insert
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Insert
    suspend fun insertWorkoutExercise(entry: WorkoutExerciseEntity): Long

    @Insert
    suspend fun insertSetEntry(set: SetEntryEntity): Long

    @Query("SELECT * FROM workout ORDER BY date DESC, id DESC")
    fun observeWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT COUNT(*) FROM workout")
    suspend fun countWorkouts(): Int
}
