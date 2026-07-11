package dev.gatsyuk.grindsync.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.gatsyuk.grindsync.core.database.AppDatabase
import dev.gatsyuk.grindsync.core.database.Migrations
import dev.gatsyuk.grindsync.core.database.dao.ExerciseDao
import dev.gatsyuk.grindsync.core.database.dao.NutritionDao
import dev.gatsyuk.grindsync.core.database.dao.RoutineDao
import dev.gatsyuk.grindsync.core.database.dao.StatsDao
import dev.gatsyuk.grindsync.core.database.dao.WorkoutDao
import dev.gatsyuk.grindsync.core.database.seed.DatabaseSeeder
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // Deliberately NO fallbackToDestructiveMigration: a missing migration must
    // crash in development rather than silently wipe data (NFR-4).
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    @Singleton
    fun provideSeeder(db: AppDatabase): DatabaseSeeder = DatabaseSeeder(db)

    @Provides fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideRoutineDao(db: AppDatabase): RoutineDao = db.routineDao()
    @Provides fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()
    @Provides fun provideStatsDao(db: AppDatabase): StatsDao = db.statsDao()
    @Provides fun provideNutritionDao(db: AppDatabase): NutritionDao = db.nutritionDao()
}
