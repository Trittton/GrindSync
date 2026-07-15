package dev.gatsyuk.grindsync.core.export

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gatsyuk.grindsync.core.data.WorkoutRepository
import dev.gatsyuk.grindsync.core.database.AppDatabase
import dev.gatsyuk.grindsync.core.database.entity.NutritionTargetEntity
import dev.gatsyuk.grindsync.core.database.seed.DatabaseSeeder
import dev.gatsyuk.grindsync.core.model.Meal
import dev.gatsyuk.grindsync.core.model.SetKind
import dev.gatsyuk.grindsync.core.model.SetValidation
import dev.gatsyuk.grindsync.core.database.entity.FoodItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Phase 5 core guarantee: export -> wipe -> import reproduces the DB exactly. */
@RunWith(AndroidJUnit4::class)
class ExportImportRoundTripTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ExportImportRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repo = ExportImportRepository(db.exportDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun export_wipe_import_reproducesDatabase() = runTest {
        // Seed catalog + a real workout + nutrition, so all tables are exercised.
        DatabaseSeeder(db).seedIfEmpty()
        val workoutRepo = WorkoutRepository(db.workoutDao(), db.routineDao(), db.exerciseDao())
        val routine = db.routineDao().getRoutinesWithExercises().first()
        val workoutId = workoutRepo.startFromRoutine(routine.routine.id)
        val live = db.workoutDao().observeWorkoutWithContent(workoutId)
        val squat = live.let { db.workoutDao().getWorkout(workoutId) }
        // Log a set via repository so validation path is used.
        val we = db.exportDao().allWorkoutExercises().first { it.workoutId == workoutId }
        workoutRepo.addSet(
            we.id,
            db.exportDao().allExercises().first { it.id == we.exerciseId }.exerciseType,
            SetValidation.Candidate(weightKg = 100.0, reps = 5),
            kind = SetKind.WORKING,
        )
        workoutRepo.finishWorkout(workoutId)

        val nutritionDao = db.nutritionDao()
        val foodId = nutritionDao.insertFood(
            FoodItemEntity(name = "Oats", kcalPerServing = 370.0, proteinG = 13.0, carbsG = 60.0, fatG = 7.0),
        )
        nutritionDao.insertEntry(
            dev.gatsyuk.grindsync.core.database.entity.DiaryEntryEntity(
                date = LocalDate.of(2026, 7, 11), meal = Meal.BREAKFAST,
                foodItemId = foodId, quantityServings = 0.8,
            ),
        )
        nutritionDao.insertTarget(
            NutritionTargetEntity(
                effectiveDate = LocalDate.of(2026, 7, 1),
                kcalTarget = 2500, proteinTargetG = 150, carbsTargetG = 250, fatTargetG = 80,
            ),
        )

        // Snapshot everything.
        val before = repo.buildSnapshot()
        assertTrue(before.exercises.isNotEmpty())
        assertTrue(before.setEntries.isNotEmpty())
        assertTrue(before.diaryEntries.isNotEmpty())

        // Round-trip through JSON text (proves serialization is lossless too).
        val json = BackupSerializer.toJson(before)
        repo.importJson(json)

        val after = repo.buildSnapshot().copy(exportedAtEpochMillis = before.exportedAtEpochMillis)
        assertEquals(before, after)
    }
}
