package dev.gatsyuk.grindsync.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import dev.gatsyuk.grindsync.core.database.entity.ExerciseEntity
import dev.gatsyuk.grindsync.core.database.entity.ExerciseMuscleEntity
import dev.gatsyuk.grindsync.core.database.entity.MuscleGroupEntity
import kotlinx.coroutines.flow.Flow

data class ExerciseWithMuscles(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exercise_id")
    val muscles: List<ExerciseMuscleEntity>,
)

@Dao
interface ExerciseDao {

    @Insert
    suspend fun insertMuscleGroups(groups: List<MuscleGroupEntity>): List<Long>

    @Insert
    suspend fun insertExercises(exercises: List<ExerciseEntity>): List<Long>

    @Insert
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @androidx.room.Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    @Query("DELETE FROM exercise_muscle WHERE exercise_id = :exerciseId")
    suspend fun deleteMusclesFor(exerciseId: Long)

    @Insert
    suspend fun insertExerciseMuscles(mappings: List<ExerciseMuscleEntity>)

    @Query("SELECT * FROM muscle_group ORDER BY display_order")
    fun observeMuscleGroups(): Flow<List<MuscleGroupEntity>>

    @Transaction
    @Query("SELECT * FROM exercise ORDER BY name")
    fun observeExercisesWithMuscles(): Flow<List<ExerciseWithMuscles>>

    @Transaction
    @Query("SELECT * FROM exercise ORDER BY name")
    suspend fun getExercisesWithMuscles(): List<ExerciseWithMuscles>

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun countExercises(): Int

    @Query("SELECT * FROM exercise WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntity?

    @Query("SELECT * FROM muscle_group WHERE name = :name LIMIT 1")
    suspend fun findGroupByName(name: String): MuscleGroupEntity?

    @Query("SELECT COALESCE(MAX(display_order) + 1, 0) FROM muscle_group")
    suspend fun nextGroupDisplayOrder(): Int
}
