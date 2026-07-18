package dev.gatsyuk.soloranking.core.gamification

import dev.gatsyuk.soloranking.core.database.dao.ExerciseWithMuscles
import dev.gatsyuk.soloranking.core.database.dao.WorkoutExerciseWithSets
import dev.gatsyuk.soloranking.core.database.dao.WorkoutWithContent
import dev.gatsyuk.soloranking.core.database.entity.ExerciseEntity
import dev.gatsyuk.soloranking.core.database.entity.ExerciseMuscleEntity
import dev.gatsyuk.soloranking.core.database.entity.SetEntryEntity
import dev.gatsyuk.soloranking.core.database.entity.WorkoutEntity
import dev.gatsyuk.soloranking.core.database.entity.WorkoutExerciseEntity
import dev.gatsyuk.soloranking.core.model.ExerciseType
import dev.gatsyuk.soloranking.core.model.Muscle
import dev.gatsyuk.soloranking.core.model.MuscleRole
import dev.gatsyuk.soloranking.core.model.Rank
import dev.gatsyuk.soloranking.core.model.SetKind
import dev.gatsyuk.soloranking.core.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RankEngineTest {

    private val squat = ExerciseEntity(
        id = 1, name = "Back Squat", muscleGroupId = 1,
        exerciseType = ExerciseType.STRENGTH_WEIGHT_REPS,
    )
    private val bench = ExerciseEntity(
        id = 2, name = "Barbell Bench Press", muscleGroupId = 1,
        exerciseType = ExerciseType.STRENGTH_WEIGHT_REPS,
    )
    private val deadlift = ExerciseEntity(
        id = 3, name = "Deadlift", muscleGroupId = 2,
        exerciseType = ExerciseType.STRENGTH_WEIGHT_REPS,
    )

    private val catalog = listOf(
        ExerciseWithMuscles(
            squat,
            listOf(
                ExerciseMuscleEntity(1, Muscle.QUADS, MuscleRole.PRIMARY, 1.0),
                ExerciseMuscleEntity(1, Muscle.GLUTES, MuscleRole.SECONDARY, 0.4),
            ),
        ),
        ExerciseWithMuscles(
            bench,
            listOf(ExerciseMuscleEntity(2, Muscle.CHEST, MuscleRole.PRIMARY, 1.0)),
        ),
        ExerciseWithMuscles(
            deadlift,
            listOf(ExerciseMuscleEntity(3, Muscle.HAMSTRINGS, MuscleRole.PRIMARY, 1.0)),
        ),
    )

    private var nextSetId = 100L

    private fun set(weight: Double, reps: Int, kind: SetKind = SetKind.WORKING) =
        SetEntryEntity(
            id = nextSetId++, workoutExerciseId = 0, position = 0,
            setKind = kind, weightKg = weight, reps = reps,
        )

    private fun workout(
        id: Long,
        date: LocalDate,
        bodyweight: Double? = 90.0,
        vararg content: Pair<ExerciseEntity, List<SetEntryEntity>>,
    ) = WorkoutWithContent(
        workout = WorkoutEntity(id = id, name = "W$id", date = date, bodyweightKg = bodyweight),
        exercises = content.mapIndexed { index, (exercise, sets) ->
            WorkoutExerciseWithSets(
                workoutExercise = WorkoutExerciseEntity(
                    id = id * 10 + index, workoutId = id, exerciseId = exercise.id, position = index,
                ),
                exercise = exercise,
                sets = sets.mapIndexed { p, s -> s.copy(position = p) },
            )
        },
    )

    private val today = LocalDate.of(2026, 7, 11)

    @Test
    fun `first session earns set xp but no PR xp, second session PR pays out`() {
        val w1 = workout(1, today.minusWeeks(1), 90.0, squat to listOf(set(100.0, 5), set(100.0, 5)))
        val s1 = RankEngine.compute(listOf(w1), catalog, Sex.MALE, null, today)
        // 2 working sets x10 XP, no PR on virgin history (nothing beaten).
        assertEquals(20, s1.exercises.single().xp)

        val w2 = workout(2, today, 90.0, squat to listOf(set(110.0, 5)))
        val s2 = RankEngine.compute(listOf(w1, w2), catalog, Sex.MALE, null, today)
        // + 10 (set) + 100 (e1RM PR) + 25 (5-rep PR) = 135 for session 2.
        assertEquals(20 + 135, s2.exercises.single().xp)
        assertEquals(2, s2.totalPrCount)
    }

    @Test
    fun `warmups and implausible sets earn nothing and set no records`() {
        val w1 = workout(1, today.minusWeeks(1), 90.0, squat to listOf(set(100.0, 5)))
        val w2 = workout(
            2, today, 90.0,
            squat to listOf(
                set(600.0, 5),                       // implausible -> ignored
                set(120.0, 5, kind = SetKind.WARMUP), // warmup -> ignored
            ),
        )
        val state = RankEngine.compute(listOf(w1, w2), catalog, Sex.MALE, null, today)
        assertEquals(0, state.totalPrCount)
        // best e1RM still from the honest 100x5
        assertEquals(116.667, state.exercises.single().bestE1rmKg!!, 0.01)
    }

    @Test
    fun `session xp respects diminishing returns and hard cap`() {
        val tenSets = List(10) { set(100.0, 5) }
        val w1 = workout(1, today, 90.0, squat to tenSets)
        val state = RankEngine.compute(listOf(w1), catalog, Sex.MALE, null, today)
        // only first 6 sets pay volume XP
        assertEquals(60, state.exercises.single().xp)
        assertTrue(state.exercises.single().xp <= 200)
    }

    @Test
    fun `gl rank appears only once SBD plus profile exist`() {
        val squatOnly = workout(1, today, 90.0, squat to listOf(set(150.0, 5)))
        val partial = RankEngine.compute(listOf(squatOnly), catalog, Sex.MALE, null, today)
        assertNull(partial.glPoints)
        assertNull(partial.overallRank)

        val full = workout(
            2, today, 93.0,
            squat to listOf(set(240.0, 1)),
            bench to listOf(set(160.0, 1)),
            deadlift to listOf(set(300.0, 1)),
        )
        val state = RankEngine.compute(listOf(squatOnly, full), catalog, Sex.MALE, null, today)
        assertNotNull(state.glPoints)
        // total 700 @ 93 kg male ≈ 91.57 GL -> SS− tier (90–95); SSS+ ≈ WR (115+)
        assertEquals(91.57, state.glPoints!!, 0.05)
        assertEquals(Rank.SS_MINUS, state.overallRank)
    }

    @Test
    fun `muscle rank is contribution-weighted and untrained muscles stay unranked`() {
        val w = workout(
            1, today, 100.0,
            squat to listOf(set(160.0, 1)),   // squat multiple 1.6 = B anchor -> score 47.5
        )
        val state = RankEngine.compute(listOf(w), catalog, Sex.MALE, null, today)
        val quads = state.muscleRanks[Muscle.QUADS]!!
        assertEquals(47.5, quads.score, 0.5)
        assertEquals(Rank.B_MINUS, quads.rank)
        // glutes get the same score via 0.4 weight (single contributor)
        assertNotNull(state.muscleRanks[Muscle.GLUTES])
        // chest untrained -> UNRANKED (absent), not E
        assertNull(state.muscleRanks[Muscle.CHEST])
    }

    @Test
    fun `streak counts consecutive training weeks and survives current week gap`() {
        val w1 = workout(1, today.minusWeeks(3), 90.0, squat to listOf(set(100.0, 5)))
        val w2 = workout(2, today.minusWeeks(2), 90.0, squat to listOf(set(100.0, 5)))
        val w3 = workout(3, today.minusWeeks(1), 90.0, squat to listOf(set(100.0, 5)))
        val state = RankEngine.compute(listOf(w1, w2, w3), catalog, Sex.MALE, null, today)
        // trained last week but not yet this week -> streak alive at 3
        assertEquals(3, state.currentStreakWeeks)
        assertEquals(3, state.longestStreakWeeks)
    }

    @Test
    fun `achievements are deliberately blank until designed`() {
        val w1 = workout(1, today.minusWeeks(1), 90.0, squat to listOf(set(120.0, 3)))
        val state = RankEngine.compute(listOf(w1), catalog, Sex.MALE, null, today)
        assertTrue(state.achievements.isEmpty())
    }

    @Test
    fun `untouched prefab sets earn no xp and count nothing`() {
        val empty = SetEntryEntity(id = nextSetId++, workoutExerciseId = 0, position = 0)
        val w1 = workout(1, today, 90.0, squat to listOf(empty, set(100.0, 5)))
        val state = RankEngine.compute(listOf(w1), catalog, Sex.MALE, null, today)
        // only the real set pays: 1 x 10 XP
        assertEquals(10, state.exercises.single().xp)
    }

    @Test
    fun `deterministic - same input same output`() {
        val w1 = workout(1, today.minusWeeks(1), 90.0, squat to listOf(set(100.0, 5)))
        val w2 = workout(2, today, 90.0, squat to listOf(set(110.0, 5)))
        val a = RankEngine.compute(listOf(w1, w2), catalog, Sex.MALE, null, today)
        val b = RankEngine.compute(listOf(w2, w1), catalog, Sex.MALE, null, today) // order-independent input
        assertEquals(a.overallXp, b.overallXp)
        assertEquals(a.glPoints, b.glPoints)
        assertEquals(a.exercises.single().xp, b.exercises.single().xp)
    }
}
