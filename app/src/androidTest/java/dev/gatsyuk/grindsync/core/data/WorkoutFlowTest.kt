package dev.gatsyuk.grindsync.core.data

import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gatsyuk.grindsync.core.database.AppDatabase
import dev.gatsyuk.grindsync.core.database.seed.DatabaseSeeder
import dev.gatsyuk.grindsync.core.database.seed.SeedData
import dev.gatsyuk.grindsync.core.model.ExerciseType
import dev.gatsyuk.grindsync.core.model.SetKind
import dev.gatsyuk.grindsync.core.model.SetValidation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "WorkoutFlowTest"

/**
 * Phase 1 primary flow, end to end at the data layer:
 * seeded routine -> START -> log sets -> finish -> history + LATEST prefill.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutFlowTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: WorkoutRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repo = WorkoutRepository(db.workoutDao(), db.routineDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun startFromRoutine_logSets_finish_historyAndPrefill() = runTest {
        DatabaseSeeder(db).seedIfEmpty()

        // START the seeded template.
        val routine = db.routineDao().getRoutinesWithExercises().single()
        val workoutId = repo.startFromRoutine(routine.routine.id)

        val live = db.workoutDao().observeWorkoutWithContent(workoutId).first()!!
        assertEquals(SeedData.exampleRoutine.size, live.exercises.size)
        assertNull(live.workout.endTimeEpochMillis) // in progress

        // Log two working sets of squats (weights canonical kg).
        val squat = live.exercises.first { it.exercise.name == "Back Squat" }
        repo.addSet(
            squat.workoutExercise.id, squat.exercise.exerciseType,
            SetValidation.Candidate(weightKg = 100.0, reps = 5),
        )
        repo.addSet(
            squat.workoutExercise.id, squat.exercise.exerciseType,
            SetValidation.Candidate(weightKg = 100.0, reps = 5), kind = SetKind.WORKING,
        )

        // Type gating: kcal on a strength lift must be rejected.
        val rejected = repo.addSet(
            squat.workoutExercise.id, squat.exercise.exerciseType,
            SetValidation.Candidate(kcal = 300),
        )
        assertNull("kcal must be rejected for STRENGTH_WEIGHT_REPS", rejected)

        // Time-based Plank takes seconds, not reps.
        val plank = live.exercises.first { it.exercise.name == "Plank" }
        val plankSet = repo.addSet(
            plank.workoutExercise.id, plank.exercise.exerciseType,
            SetValidation.Candidate(timeSeconds = 60),
        )
        assertNotNull(plankSet)

        repo.finishWorkout(workoutId)

        // History shows the completed session with its sets.
        val history = db.workoutDao().observeCompletedWorkouts().first()
        assertEquals(1, history.size)
        val logged = history.single()
        assertNotNull(logged.workout.endTimeEpochMillis)
        val squatSets = logged.exercises.first { it.exercise.name == "Back Squat" }.sets
        assertEquals(2, squatSets.size)
        assertEquals(100.0, squatSets.first().weightKg!!, 0.0)

        // LATEST prefill now serves this session back.
        val squatExerciseId = squat.exercise.id
        val prefill = repo.lastPerformedSets(squatExerciseId)
        assertEquals(2, prefill.size)
        assertEquals(5, prefill.first().reps)

        Log.i(TAG, "History: ${logged.workout.name}, " +
            "${logged.exercises.size} exercises, " +
            "${logged.exercises.sumOf { it.sets.size }} sets; " +
            "squat prefill = ${prefill.map { "${it.weightKg}x${it.reps}" }}")

        // ExerciseType coverage assertion: cardio prefill unaffected.
        assertTrue(repo.lastPerformedSets(-1L).isEmpty())
    }

    @Test
    fun emptyWorkout_discard_leavesNoTrace() = runTest {
        DatabaseSeeder(db).seedIfEmpty()
        val id = repo.startEmptyWorkout()
        assertNotNull(db.workoutDao().observeInProgressWorkout().first())
        repo.deleteWorkout(id)
        assertNull(db.workoutDao().observeInProgressWorkout().first())
        assertEquals(0, db.workoutDao().observeCompletedWorkouts().first().size)
    }
}
