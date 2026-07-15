package dev.gatsyuk.grindsync.core.export

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
import dev.gatsyuk.grindsync.core.model.ExerciseType
import dev.gatsyuk.grindsync.core.model.FoodSource
import dev.gatsyuk.grindsync.core.model.Meal
import dev.gatsyuk.grindsync.core.model.Muscle
import dev.gatsyuk.grindsync.core.model.MuscleRole
import dev.gatsyuk.grindsync.core.model.SetKind
import dev.gatsyuk.grindsync.core.model.TargetMode
import java.time.LocalDate

// Enums are carried as names (matching how Room stores them), so an unknown
// future value fails loudly on import rather than being silently coerced.

fun MuscleGroupEntity.toDto() = MuscleGroupDto(id, name, displayOrder)
fun MuscleGroupDto.toEntity() = MuscleGroupEntity(id, name, displayOrder)

fun ExerciseEntity.toDto() = ExerciseDto(
    id, name, muscleGroupId, exerciseType.name, isUnilateral, isCustom, isArchived, defaultWarmupSets,
)
fun ExerciseDto.toEntity() = ExerciseEntity(
    id, name, muscleGroupId, ExerciseType.valueOf(exerciseType), isUnilateral, isCustom, isArchived,
    defaultWarmupSets,
)

fun ExerciseMuscleEntity.toDto() =
    ExerciseMuscleDto(exerciseId, muscle.name, role.name, contributionWeight)
fun ExerciseMuscleDto.toEntity() = ExerciseMuscleEntity(
    exerciseId, Muscle.valueOf(muscle), MuscleRole.valueOf(role), contributionWeight,
)

fun RoutineEntity.toDto() = RoutineDto(id, name, targetMode.name, notes, displayOrder)
fun RoutineDto.toEntity() = RoutineEntity(id, name, TargetMode.valueOf(targetMode), notes, displayOrder)

fun RoutineExerciseEntity.toDto() =
    RoutineExerciseDto(id, routineId, exerciseId, position, targetSets, repMin, repMax)
fun RoutineExerciseDto.toEntity() =
    RoutineExerciseEntity(id, routineId, exerciseId, position, targetSets, repMin, repMax)

fun WorkoutEntity.toDto() = WorkoutDto(
    id, name, date.toEpochDay(), startTimeEpochMillis, endTimeEpochMillis,
    bodyweightKg, notes, sourceRoutineId,
)
fun WorkoutDto.toEntity() = WorkoutEntity(
    id, name, LocalDate.ofEpochDay(dateEpochDay), startTimeEpochMillis, endTimeEpochMillis,
    bodyweightKg, notes, sourceRoutineId,
)

fun WorkoutExerciseEntity.toDto() =
    WorkoutExerciseDto(id, workoutId, exerciseId, position, supersetGroupId)
fun WorkoutExerciseDto.toEntity() =
    WorkoutExerciseEntity(id, workoutId, exerciseId, position, supersetGroupId)

fun SetEntryEntity.toDto() = SetEntryDto(
    id, workoutExerciseId, position, setKind.name, weightKg, reps,
    timeSeconds, distanceMeters, kcal, notes, isPr,
)
fun SetEntryDto.toEntity() = SetEntryEntity(
    id, workoutExerciseId, position, SetKind.valueOf(setKind), weightKg, reps,
    timeSeconds, distanceMeters, kcal, notes, isPr,
)

fun FoodItemEntity.toDto() = FoodItemDto(
    id, name, brand, source.name, barcode, servingSize, servingUnit,
    kcalPerServing, proteinG, carbsG, fatG, isFavorite,
)
fun FoodItemDto.toEntity() = FoodItemEntity(
    id, name, brand, FoodSource.valueOf(source), barcode, servingSize, servingUnit,
    kcalPerServing, proteinG, carbsG, fatG, isFavorite,
)

fun DiaryEntryEntity.toDto() =
    DiaryEntryDto(id, date.toEpochDay(), meal.name, foodItemId, quantityServings)
fun DiaryEntryDto.toEntity() =
    DiaryEntryEntity(id, LocalDate.ofEpochDay(dateEpochDay), Meal.valueOf(meal), foodItemId, quantityServings)

fun NutritionTargetEntity.toDto() = NutritionTargetDto(
    id, effectiveDate.toEpochDay(), kcalTarget, proteinTargetG, carbsTargetG, fatTargetG,
)
fun NutritionTargetDto.toEntity() = NutritionTargetEntity(
    id, LocalDate.ofEpochDay(effectiveDateEpochDay), kcalTarget, proteinTargetG, carbsTargetG, fatTargetG,
)
