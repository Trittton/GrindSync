package dev.gatsyuk.grindsync.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import dev.gatsyuk.grindsync.core.database.entity.DiaryEntryEntity
import dev.gatsyuk.grindsync.core.database.entity.ExerciseEntity
import dev.gatsyuk.grindsync.core.database.entity.ExerciseMuscleEntity
import dev.gatsyuk.grindsync.core.database.entity.FoodItemEntity
import dev.gatsyuk.grindsync.core.database.entity.MuscleGroupEntity
import dev.gatsyuk.grindsync.core.database.entity.NutritionTargetEntity
import dev.gatsyuk.grindsync.core.database.entity.RoutineEntity
import dev.gatsyuk.grindsync.core.database.entity.RoutineExerciseEntity
import dev.gatsyuk.grindsync.core.database.entity.SetEntryEntity
import dev.gatsyuk.grindsync.core.database.entity.WorkoutEntity
import dev.gatsyuk.grindsync.core.database.entity.WorkoutExerciseEntity

/** Bulk read/replace for export & import. Restore runs in one transaction. */
@Dao
interface ExportDao {

    // --- reads (full-table dumps for export) ---
    @Query("SELECT * FROM muscle_group") suspend fun allMuscleGroups(): List<MuscleGroupEntity>
    @Query("SELECT * FROM exercise") suspend fun allExercises(): List<ExerciseEntity>
    @Query("SELECT * FROM exercise_muscle") suspend fun allExerciseMuscles(): List<ExerciseMuscleEntity>
    @Query("SELECT * FROM routine") suspend fun allRoutines(): List<RoutineEntity>
    @Query("SELECT * FROM routine_exercise") suspend fun allRoutineExercises(): List<RoutineExerciseEntity>
    @Query("SELECT * FROM workout") suspend fun allWorkouts(): List<WorkoutEntity>
    @Query("SELECT * FROM workout_exercise") suspend fun allWorkoutExercises(): List<WorkoutExerciseEntity>
    @Query("SELECT * FROM set_entry") suspend fun allSetEntries(): List<SetEntryEntity>
    @Query("SELECT * FROM food_item") suspend fun allFoodItems(): List<FoodItemEntity>
    @Query("SELECT * FROM diary_entry") suspend fun allDiaryEntries(): List<DiaryEntryEntity>
    @Query("SELECT * FROM nutrition_target") suspend fun allNutritionTargets(): List<NutritionTargetEntity>

    // --- inserts (explicit ids preserved) ---
    @Insert suspend fun insertMuscleGroups(rows: List<MuscleGroupEntity>)
    @Insert suspend fun insertExercises(rows: List<ExerciseEntity>)
    @Insert suspend fun insertExerciseMuscles(rows: List<ExerciseMuscleEntity>)
    @Insert suspend fun insertRoutines(rows: List<RoutineEntity>)
    @Insert suspend fun insertRoutineExercises(rows: List<RoutineExerciseEntity>)
    @Insert suspend fun insertWorkouts(rows: List<WorkoutEntity>)
    @Insert suspend fun insertWorkoutExercises(rows: List<WorkoutExerciseEntity>)
    @Insert suspend fun insertSetEntries(rows: List<SetEntryEntity>)
    @Insert suspend fun insertFoodItems(rows: List<FoodItemEntity>)
    @Insert suspend fun insertDiaryEntries(rows: List<DiaryEntryEntity>)
    @Insert suspend fun insertNutritionTargets(rows: List<NutritionTargetEntity>)

    // --- clears (child-first to respect FKs) ---
    @Query("DELETE FROM set_entry") suspend fun clearSetEntries()
    @Query("DELETE FROM workout_exercise") suspend fun clearWorkoutExercises()
    @Query("DELETE FROM workout") suspend fun clearWorkouts()
    @Query("DELETE FROM routine_exercise") suspend fun clearRoutineExercises()
    @Query("DELETE FROM routine") suspend fun clearRoutines()
    @Query("DELETE FROM exercise_muscle") suspend fun clearExerciseMuscles()
    @Query("DELETE FROM diary_entry") suspend fun clearDiaryEntries()
    @Query("DELETE FROM nutrition_target") suspend fun clearNutritionTargets()
    @Query("DELETE FROM food_item") suspend fun clearFoodItems()
    @Query("DELETE FROM exercise") suspend fun clearExercises()
    @Query("DELETE FROM muscle_group") suspend fun clearMuscleGroups()

    /**
     * Replace the entire database with the supplied rows, atomically. Insert
     * order is parent-first so foreign keys always resolve. A failure rolls the
     * whole thing back — the existing data is never left half-wiped (NFR-4 spirit).
     */
    @Transaction
    suspend fun clearAndRestore(
        muscleGroups: List<MuscleGroupEntity>,
        exercises: List<ExerciseEntity>,
        exerciseMuscles: List<ExerciseMuscleEntity>,
        routines: List<RoutineEntity>,
        routineExercises: List<RoutineExerciseEntity>,
        workouts: List<WorkoutEntity>,
        workoutExercises: List<WorkoutExerciseEntity>,
        setEntries: List<SetEntryEntity>,
        foodItems: List<FoodItemEntity>,
        diaryEntries: List<DiaryEntryEntity>,
        nutritionTargets: List<NutritionTargetEntity>,
    ) {
        clearSetEntries(); clearWorkoutExercises(); clearWorkouts()
        clearRoutineExercises(); clearRoutines()
        clearDiaryEntries(); clearNutritionTargets(); clearFoodItems()
        clearExerciseMuscles(); clearExercises(); clearMuscleGroups()

        insertMuscleGroups(muscleGroups)
        insertExercises(exercises)
        insertExerciseMuscles(exerciseMuscles)
        insertRoutines(routines)
        insertRoutineExercises(routineExercises)
        insertWorkouts(workouts)
        insertWorkoutExercises(workoutExercises)
        insertSetEntries(setEntries)
        insertFoodItems(foodItems)
        insertDiaryEntries(diaryEntries)
        insertNutritionTargets(nutritionTargets)
    }
}
