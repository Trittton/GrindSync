package dev.gatsyuk.soloranking.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineShareTest {

    private val sample = SharedRoutine(
        name = "Push Day",
        notes = "focus on pause bench",
        exercises = listOf(
            SharedRoutineExercise(
                name = "Barbell Bench Press",
                category = "Chest",
                exerciseType = "STRENGTH_WEIGHT_REPS",
                muscles = listOf(
                    SharedMuscle("CHEST", "PRIMARY", 1.0),
                    SharedMuscle("TRICEPS", "SECONDARY", 0.4),
                ),
                targetSets = 3, repMin = 5, repMax = 8,
            ),
            SharedRoutineExercise(
                name = "My Custom Cable Fly",
                category = "Chest",
                exerciseType = "STRENGTH_WEIGHT_REPS",
                muscles = listOf(SharedMuscle("CHEST", "PRIMARY", 1.0)),
                targetSets = 2, repMin = 10, repMax = 15,
            ),
        ),
    )

    @Test
    fun `round trip preserves the routine`() {
        assertEquals(sample, RoutineShare.fromJson(RoutineShare.toJson(sample)))
    }

    @Test
    fun `garbage input fails with a readable message`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            RoutineShare.fromJson("hello, not json")
        }
        assertTrue(ex.message!!.contains("doesn't look like"))
    }

    @Test
    fun `newer format version is rejected`() {
        val future = RoutineShare.toJson(sample).replace("\"soloRankingRoutine\": 1", "\"soloRankingRoutine\": 99")
        val ex = assertThrows(IllegalArgumentException::class.java) {
            RoutineShare.fromJson(future)
        }
        assertTrue(ex.message!!.contains("newer version of Solo Ranking"))
    }

    @Test
    fun `legacy grindsyncRoutine key still imports`() {
        // Shares produced by pre-rename versions (<= 0.7.0) carry the old key;
        // unknown keys are ignored and the version field defaults to 1.
        val legacy = RoutineShare.toJson(sample)
            .replace("\"soloRankingRoutine\": 1", "\"grindsyncRoutine\": 1")
        assertEquals(sample, RoutineShare.fromJson(legacy))
    }

    @Test
    fun `empty routine is rejected`() {
        val empty = RoutineShare.toJson(sample.copy(exercises = emptyList()))
        assertThrows(IllegalArgumentException::class.java) { RoutineShare.fromJson(empty) }
    }
}
