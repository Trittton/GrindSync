package dev.gatsyuk.soloranking.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gatsyuk.soloranking.core.database.AppDatabase
import dev.gatsyuk.soloranking.core.database.seed.DatabaseSeeder
import dev.gatsyuk.soloranking.core.model.SetValidation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Round-3 actions: Repeat Workout, Save as Routine, note inheritance. */
@RunWith(AndroidJUnit4::class)
class WorkoutActionsTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: WorkoutRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repo = WorkoutRepository(db.workoutDao(), db.routineDao(), db.exerciseDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun logFirstSession(): Pair<Long, Long> {
        DatabaseSeeder(db).seedIfEmpty()
        val routine = db.routineDao().getRoutinesWithExercises().single()
        val workoutId = repo.startFromRoutine(routine.routine.id)
        val content = db.workoutDao().getWorkoutWithContent(workoutId)!!
        val squat = content.exercises.first { it.exercise.name == "Back Squat" }
        repo.addSet(
            squat.workoutExercise.id, squat.exercise.exerciseType,
            SetValidation.Candidate(weightKg = 100.0, reps = 5), notes = "belt on",
        )
        // Workout-level note + finish.
        repo.updateWorkout(content.workout.copy(notes = "gym was empty"))
        repo.finishWorkout(workoutId)
        return workoutId to routine.routine.id
    }

    @Test
    fun notes_inherit_from_latest_performance_only() = runTest {
        val (_, routineId) = logFirstSession()

        // Second run inherits both note levels from run #1.
        val secondId = repo.startFromRoutine(routineId)
        val second = db.workoutDao().getWorkoutWithContent(secondId)!!
        assertEquals("gym was empty", second.workout.notes)
        val squat2 = second.exercises.first { it.exercise.name == "Back Squat" }
        assertEquals("belt on", squat2.sets.sortedBy { it.position }.first().notes)

        // Perform the set in run #2 with a changed note, finish; run #3 sees
        // the NEW note (latest wins). The set must actually be performed —
        // ghost-prefill rows left empty are pruned, notes and all.
        val firstSet = squat2.sets.sortedBy { it.position }.first()
        repo.updateSet(
            squat2.exercise.exerciseType,
            firstSet.copy(weightKg = 105.0, reps = 5, notes = "no belt today"),
        )
        repo.updateWorkout(second.workout.copy(notes = "crowded"))
        repo.finishWorkout(secondId)

        val thirdId = repo.startFromRoutine(routineId)
        val third = db.workoutDao().getWorkoutWithContent(thirdId)!!
        assertEquals("crowded", third.workout.notes)
        val squat3 = third.exercises.first { it.exercise.name == "Back Squat" }
        assertEquals("no belt today", squat3.sets.sortedBy { it.position }.first().notes)
    }

    @Test
    fun repeatWorkout_clones_values_and_notes_as_live_session() = runTest {
        val (workoutId, _) = logFirstSession()

        val cloneId = repo.repeatWorkout(workoutId)
        val clone = db.workoutDao().getWorkoutWithContent(cloneId)!!
        assertNull(clone.workout.endTimeEpochMillis) // live, not finished
        assertEquals("gym was empty", clone.workout.notes)
        val squat = clone.exercises.first { it.exercise.name == "Back Squat" }
        val set = squat.sets.single()
        assertEquals(100.0, set.weightKg!!, 0.0)
        assertEquals(5, set.reps)
        assertEquals("belt on", set.notes)
        assertEquals(false, set.isPr) // PR flags never clone
    }

    @Test
    fun saveAsRoutine_builds_template_from_session() = runTest {
        val (workoutId, _) = logFirstSession()
        val routineId = repo.saveAsRoutine(workoutId)

        val routines = db.routineDao().getRoutinesWithExercises()
        val saved = routines.first { it.routine.id == routineId }
        assertEquals("Full Body A (example)", saved.routine.name)
        assertEquals("gym was empty", saved.routine.notes)
        // Only the squat survived pruning in session #1 -> 1 exercise had sets,
        // but ALL performed exercises are captured (squat has 1 set, 5-5 reps).
        val squatEntry = saved.exercises.first { entry ->
            db.workoutDao().getWorkoutWithContent(workoutId)!!
                .exercises.first { it.exercise.name == "Back Squat" }
                .exercise.id == entry.exerciseId
        }
        assertEquals(1, squatEntry.targetSets)
        assertEquals(5, squatEntry.repMin)
        assertEquals(5, squatEntry.repMax)
    }

    @Test
    fun prune_drops_unperformed_sets_even_with_inherited_notes() = runTest {
        val (_, routineId) = logFirstSession()
        // Run #2: prefabs carry the structure (and inherited notes) of the last
        // session but NO measurements — those render as greyed ghosts in the UI.
        val secondId = repo.startFromRoutine(routineId)
        val live = db.workoutDao().getWorkoutWithContent(secondId)!!
        val liveSquat = live.exercises.first { it.exercise.name == "Back Squat" }
        assertEquals(1, liveSquat.sets.size) // last session's count reproduced...
        assertTrue(liveSquat.sets.all { it.weightKg == null && it.reps == null }) // ...values empty

        // User performs nothing and finishes: every untouched row is pruned,
        // so an unperformed session contributes zero sets to history/stats.
        repo.finishWorkout(secondId)
        val second = db.workoutDao().getWorkoutWithContent(secondId)!!
        val squat = second.exercises.first { it.exercise.name == "Back Squat" }
        assertEquals(0, squat.sets.size)
    }
}
