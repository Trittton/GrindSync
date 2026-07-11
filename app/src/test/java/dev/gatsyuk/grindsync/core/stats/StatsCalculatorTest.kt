package dev.gatsyuk.grindsync.core.stats

import dev.gatsyuk.grindsync.core.database.dao.SetForExercise
import dev.gatsyuk.grindsync.core.database.entity.SetEntryEntity
import dev.gatsyuk.grindsync.core.model.ExerciseType
import dev.gatsyuk.grindsync.core.model.SetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsCalculatorTest {

    private fun set(
        weight: Double?,
        reps: Int?,
        kind: SetKind = SetKind.WORKING,
        date: LocalDate = LocalDate.of(2026, 7, 1),
        position: Int = 0,
    ) = SetForExercise(
        set = SetEntryEntity(
            id = 0, workoutExerciseId = 1, position = position,
            setKind = kind, weightKg = weight, reps = reps,
        ),
        workoutDate = date,
    )

    @Test
    fun `epley e1rm matches published values`() {
        // 100 kg x 5 -> 100 * (1 + 5/30) = 116.67
        assertEquals(116.6667, StatsCalculator.e1rm(100.0, 5)!!, 0.001)
        // single at 140 -> exactly 140 (no formula inflation)
        assertEquals(140.0, StatsCalculator.e1rm(140.0, 1)!!, 0.0)
        // 60 x 10 -> 80
        assertEquals(80.0, StatsCalculator.e1rm(60.0, 10)!!, 0.001)
        assertNull(StatsCalculator.e1rm(0.0, 5))
        assertNull(StatsCalculator.e1rm(100.0, 0))
    }

    @Test
    fun `warmups never count toward e1rm or PRs`() {
        val sets = listOf(
            set(180.0, 5, kind = SetKind.WARMUP), // heavier but warmup
            set(100.0, 5),
        )
        val best = StatsCalculator.bestE1rm(sets, ExerciseType.STRENGTH_WEIGHT_REPS)!!
        assertEquals(116.6667, best.valueKg, 0.001)
        val prs = StatsCalculator.repPrs(sets, ExerciseType.STRENGTH_WEIGHT_REPS)
        assertEquals(listOf(100.0), prs.map { it.weightKg })
    }

    @Test
    fun `assisted exercises are excluded from strength stats`() {
        val sets = listOf(set(40.0, 8))
        assertNull(StatsCalculator.bestE1rm(sets, ExerciseType.ASSISTED_WEIGHT_REPS))
        assertTrue(StatsCalculator.repPrs(sets, ExerciseType.ASSISTED_WEIGHT_REPS).isEmpty())
        assertTrue(
            StatsCalculator.sessionSeries(sets, ExerciseType.ASSISTED_WEIGHT_REPS, false).isEmpty(),
        )
    }

    @Test
    fun `session series groups by day with best e1rm and unilateral-doubled volume`() {
        val d1 = LocalDate.of(2026, 7, 1)
        val d2 = LocalDate.of(2026, 7, 8)
        val sets = listOf(
            set(50.0, 10, date = d1),
            set(55.0, 8, date = d1, position = 1),
            set(60.0, 8, date = d2),
        )
        val series = StatsCalculator.sessionSeries(sets, ExerciseType.STRENGTH_WEIGHT_REPS, unilateral = true)
        assertEquals(2, series.size)
        // day 1: volume = (50*10 + 55*8) * 2 = 1880; best e1rm = 55*(1+8/30) = 69.67
        assertEquals(1880.0, series[0].volumeKg, 0.001)
        assertEquals(69.6667, series[0].bestE1rmKg!!, 0.001)
        // day 2 sorted after day 1
        assertEquals(d2, series[1].date)
    }

    @Test
    fun `rep PRs keep best weight per rep count`() {
        val d1 = LocalDate.of(2026, 6, 1)
        val d2 = LocalDate.of(2026, 7, 1)
        val sets = listOf(
            set(100.0, 5, date = d1),
            set(105.0, 5, date = d2), // beats previous 5RM
            set(90.0, 8, date = d1),
        )
        val prs = StatsCalculator.repPrs(sets, ExerciseType.STRENGTH_WEIGHT_REPS)
        assertEquals(2, prs.size)
        assertEquals(105.0, prs.first { it.reps == 5 }.weightKg, 0.0)
        assertEquals(d2, prs.first { it.reps == 5 }.date)
        assertEquals(90.0, prs.first { it.reps == 8 }.weightKg, 0.0)
    }

    @Test
    fun `weekly frequency buckets by iso week oldest first`() {
        // Empty history -> all zeros, correct length.
        val freq = StatsCalculator.weeklyFrequency(emptyList(), 8, LocalDate.of(2026, 7, 11))
        assertEquals(8, freq.size)
        assertTrue(freq.all { it == 0 })
    }
}
