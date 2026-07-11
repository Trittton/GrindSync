package dev.gatsyuk.grindsync.core.database

import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gatsyuk.grindsync.core.database.seed.DatabaseSeeder
import dev.gatsyuk.grindsync.core.database.seed.SeedData
import dev.gatsyuk.grindsync.core.model.ExerciseType
import dev.gatsyuk.grindsync.core.model.Muscle
import dev.gatsyuk.grindsync.core.model.MuscleRole
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "SeedDataTest"

/**
 * DoD proof: seeded exercises (with types + muscle mappings) and the example
 * routine template are queryable. Logs real output for inspection.
 */
@RunWith(AndroidJUnit4::class)
class SeedDataTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedIsQueryable_exercisesWithTypesAndMuscles() = runTest {
        DatabaseSeeder(db).seedIfEmpty()

        val exercises = db.exerciseDao().getExercisesWithMuscles()
        Log.i(TAG, "Seeded ${exercises.size} exercises")
        exercises.forEach { e ->
            Log.i(TAG, "${e.exercise.name} [${e.exercise.exerciseType}] -> " +
                e.muscles.joinToString { "${it.muscle}:${it.role}:${it.contributionWeight}" })
        }

        assertEquals(SeedData.exercises.size, exercises.size)

        // All 10 tracking modes are represented in the catalog.
        assertEquals(
            ExerciseType.entries.toSet(),
            exercises.map { it.exercise.exerciseType }.toSet(),
        )

        // Spot-check a mapping: Bench Press = CHEST primary 1.0, TRICEPS secondary 0.4.
        val bench = exercises.first { it.exercise.name == "Barbell Bench Press" }
        val chest = bench.muscles.first { it.muscle == Muscle.CHEST }
        assertEquals(MuscleRole.PRIMARY, chest.role)
        assertEquals(1.0, chest.contributionWeight, 0.0)
        val triceps = bench.muscles.first { it.muscle == Muscle.TRICEPS }
        assertEquals(MuscleRole.SECONDARY, triceps.role)
        assertEquals(0.4, triceps.contributionWeight, 0.0)

        // Every non-cardio/non-other exercise has at least one primary mover.
        exercises
            .filter { it.exercise.exerciseType != ExerciseType.CARDIO_TIME_DISTANCE_KCAL }
            .filter { it.exercise.exerciseType != ExerciseType.OTHER_NOTES }
            .forEach { e ->
                assertTrue(
                    "${e.exercise.name} must have a primary muscle",
                    e.muscles.any { it.role == MuscleRole.PRIMARY },
                )
            }
    }

    @Test
    fun seedIsQueryable_exampleRoutineTemplate() = runTest {
        DatabaseSeeder(db).seedIfEmpty()

        val routines = db.routineDao().getRoutinesWithExercises()
        assertEquals(1, routines.size)

        val routine = routines.single()
        Log.i(TAG, "Routine '${routine.routine.name}' (${routine.routine.targetMode}): " +
            routine.exercises.joinToString {
                "#${it.exerciseId} ${it.targetSets}x${it.repMin ?: "-"}–${it.repMax ?: "-"}"
            })

        assertEquals(SeedData.EXAMPLE_ROUTINE_NAME, routine.routine.name)
        assertEquals(SeedData.exampleRoutine.size, routine.exercises.size)

        // Rep ranges present for rep-based entries, absent for the time-based Plank.
        val plankExercise = db.exerciseDao().findByName("Plank")
        assertNotNull(plankExercise)
        val plankEntry = routine.exercises.first { it.exerciseId == plankExercise!!.id }
        assertEquals(null, plankEntry.repMin)
        assertEquals(3, plankEntry.targetSets)
    }

    @Test
    fun seedIsIdempotent() = runTest {
        val seeder = DatabaseSeeder(db)
        seeder.seedIfEmpty()
        seeder.seedIfEmpty()
        assertEquals(SeedData.exercises.size, db.exerciseDao().countExercises())
    }
}
