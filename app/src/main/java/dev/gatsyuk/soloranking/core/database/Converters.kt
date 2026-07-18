package dev.gatsyuk.soloranking.core.database

import androidx.room.TypeConverter
import dev.gatsyuk.soloranking.core.model.ExerciseType
import dev.gatsyuk.soloranking.core.model.FoodSource
import dev.gatsyuk.soloranking.core.model.Meal
import dev.gatsyuk.soloranking.core.model.Muscle
import dev.gatsyuk.soloranking.core.model.MuscleRole
import dev.gatsyuk.soloranking.core.model.SetKind
import dev.gatsyuk.soloranking.core.model.TargetMode
import java.time.LocalDate

/**
 * Enums are stored by name (TEXT) rather than ordinal so reordering an enum
 * can never corrupt persisted data. LocalDate is stored as epoch day (INTEGER)
 * so date-range queries stay index-friendly.
 */
class Converters {

    @TypeConverter fun exerciseTypeToString(v: ExerciseType): String = v.name
    @TypeConverter fun stringToExerciseType(v: String): ExerciseType = ExerciseType.valueOf(v)

    @TypeConverter fun setKindToString(v: SetKind): String = v.name
    @TypeConverter fun stringToSetKind(v: String): SetKind = SetKind.valueOf(v)

    @TypeConverter fun targetModeToString(v: TargetMode): String = v.name
    @TypeConverter fun stringToTargetMode(v: String): TargetMode = TargetMode.valueOf(v)

    @TypeConverter fun muscleToString(v: Muscle): String = v.name
    @TypeConverter fun stringToMuscle(v: String): Muscle = Muscle.valueOf(v)

    @TypeConverter fun muscleRoleToString(v: MuscleRole): String = v.name
    @TypeConverter fun stringToMuscleRole(v: String): MuscleRole = MuscleRole.valueOf(v)

    @TypeConverter fun mealToString(v: Meal): String = v.name
    @TypeConverter fun stringToMeal(v: String): Meal = Meal.valueOf(v)

    @TypeConverter fun foodSourceToString(v: FoodSource): String = v.name
    @TypeConverter fun stringToFoodSource(v: String): FoodSource = FoodSource.valueOf(v)

    @TypeConverter fun localDateToEpochDay(v: LocalDate): Long = v.toEpochDay()
    @TypeConverter fun epochDayToLocalDate(v: Long): LocalDate = LocalDate.ofEpochDay(v)
}
