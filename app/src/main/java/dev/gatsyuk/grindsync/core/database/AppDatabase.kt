package dev.gatsyuk.grindsync.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.gatsyuk.grindsync.core.database.dao.ExerciseDao
import dev.gatsyuk.grindsync.core.database.dao.RoutineDao
import dev.gatsyuk.grindsync.core.database.dao.StatsDao
import dev.gatsyuk.grindsync.core.database.dao.WorkoutDao
import dev.gatsyuk.grindsync.core.database.entity.ExerciseEntity
import dev.gatsyuk.grindsync.core.database.entity.ExerciseMuscleEntity
import dev.gatsyuk.grindsync.core.database.entity.MuscleGroupEntity
import dev.gatsyuk.grindsync.core.database.entity.RoutineEntity
import dev.gatsyuk.grindsync.core.database.entity.RoutineExerciseEntity
import dev.gatsyuk.grindsync.core.database.entity.SetEntryEntity
import dev.gatsyuk.grindsync.core.database.entity.WorkoutEntity
import dev.gatsyuk.grindsync.core.database.entity.WorkoutExerciseEntity

@Database(
    version = 2,
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
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun statsDao(): StatsDao

    companion object {
        const val NAME = "grindsync.db"
    }
}
