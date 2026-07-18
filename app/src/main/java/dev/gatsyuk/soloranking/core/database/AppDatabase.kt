package dev.gatsyuk.soloranking.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.gatsyuk.soloranking.core.database.dao.ExerciseDao
import dev.gatsyuk.soloranking.core.database.dao.ExportDao
import dev.gatsyuk.soloranking.core.database.dao.NutritionDao
import dev.gatsyuk.soloranking.core.database.dao.RoutineDao
import dev.gatsyuk.soloranking.core.database.dao.StatsDao
import dev.gatsyuk.soloranking.core.database.dao.WorkoutDao
import dev.gatsyuk.soloranking.core.database.entity.DiaryEntryEntity
import dev.gatsyuk.soloranking.core.database.entity.ExerciseEntity
import dev.gatsyuk.soloranking.core.database.entity.ExerciseMuscleEntity
import dev.gatsyuk.soloranking.core.database.entity.FoodItemEntity
import dev.gatsyuk.soloranking.core.database.entity.MuscleGroupEntity
import dev.gatsyuk.soloranking.core.database.entity.NutritionTargetEntity
import dev.gatsyuk.soloranking.core.database.entity.RoutineEntity
import dev.gatsyuk.soloranking.core.database.entity.RoutineExerciseEntity
import dev.gatsyuk.soloranking.core.database.entity.SetEntryEntity
import dev.gatsyuk.soloranking.core.database.entity.WorkoutEntity
import dev.gatsyuk.soloranking.core.database.entity.WorkoutExerciseEntity

@Database(
    version = 4,
    exportSchema = true,
    entities = [
        MuscleGroupEntity::class,
        ExerciseEntity::class,
        ExerciseMuscleEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        SetEntryEntity::class,
        FoodItemEntity::class,
        DiaryEntryEntity::class,
        NutritionTargetEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun statsDao(): StatsDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun exportDao(): ExportDao

    companion object {
        const val NAME = "soloranking.db"
    }
}
