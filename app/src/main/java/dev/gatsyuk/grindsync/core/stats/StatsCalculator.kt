package dev.gatsyuk.grindsync.core.stats

import dev.gatsyuk.grindsync.core.database.dao.SetForExercise
import dev.gatsyuk.grindsync.core.database.dao.WorkoutWithContent
import dev.gatsyuk.grindsync.core.database.entity.SetEntryEntity
import dev.gatsyuk.grindsync.core.model.ExerciseType
import dev.gatsyuk.grindsync.core.model.SetKind
import dev.gatsyuk.grindsync.core.model.trackedDurationMillis
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Pure, deterministic statistics over raw Workout/SetEntry history.
 * Everything here is recomputable at any time (NFR-5) — no cached state.
 *
 * Scoring rules (documented once, applied app-wide):
 * - Estimated 1RM: Epley — `w × (1 + reps/30)`; reps == 1 returns w (SPEC §7.2).
 * - WARMUP sets never count toward PRs, e1RM, or volume. WORKING and DROPSET do.
 * - e1RM/volume apply to types tracking external/added weight × reps:
 *   STRENGTH_WEIGHT_REPS and BODYWEIGHT_WEIGHT_REPS (added weight — a consistent
 *   progression metric even though it isn't a true 1RM).
 *   ASSISTED_WEIGHT_REPS is EXCLUDED: its weight is machine assistance, so a
 *   "1RM of the assist" would be nonsense.
 * - Unilateral exercises count volume twice (RepCount's "count weight twice
 *   in statistics" flag semantics).
 */
object StatsCalculator {

    private val STRENGTH_TYPES = setOf(
        ExerciseType.STRENGTH_WEIGHT_REPS,
        ExerciseType.BODYWEIGHT_WEIGHT_REPS,
    )

    fun typeSupportsStrengthStats(type: ExerciseType): Boolean = type in STRENGTH_TYPES

    fun e1rm(weightKg: Double, reps: Int): Double? {
        if (weightKg <= 0 || reps <= 0) return null
        return if (reps == 1) weightKg else weightKg * (1 + reps / 30.0)
    }

    private fun SetEntryEntity.countsForStats(): Boolean = setKind != SetKind.WARMUP

    private fun setVolumeKg(set: SetEntryEntity, unilateral: Boolean): Double {
        val w = set.weightKg ?: return 0.0
        val r = set.reps ?: return 0.0
        if (!set.countsForStats() || w <= 0 || r <= 0) return 0.0
        return w * r * (if (unilateral) 2 else 1)
    }

    // ---------- overall totals (Profile dashboard) ----------

    data class TotalStats(
        val workouts: Int,
        val totalDurationMillis: Long,
        val totalVolumeKg: Double,
        val totalSets: Int,
        val totalReps: Int,
    )

    fun totals(history: List<WorkoutWithContent>): TotalStats {
        var duration = 0L
        var volume = 0.0
        var sets = 0
        var reps = 0
        history.forEach { workout ->
            // >2h sessions were left running by accident; excluded (user rule).
            trackedDurationMillis(
                workout.workout.startTimeEpochMillis,
                workout.workout.endTimeEpochMillis,
            )?.let { duration += it }
            workout.exercises.forEach { entry ->
                entry.sets.forEach { set ->
                    // Untouched prefab rows (no measurements at all) don't count.
                    val hasData = set.weightKg != null || set.reps != null ||
                        set.timeSeconds != null || set.distanceMeters != null || set.kcal != null
                    if (set.countsForStats() && hasData) {
                        sets++
                        reps += set.reps ?: 0
                        if (typeSupportsStrengthStats(entry.exercise.exerciseType)) {
                            volume += setVolumeKg(set, entry.exercise.isUnilateral)
                        }
                    }
                }
            }
        }
        return TotalStats(history.size, duration, volume, sets, reps)
    }

    /** Workouts per ISO week for the last [weeks] weeks, oldest first (index 0). */
    fun weeklyFrequency(history: List<WorkoutWithContent>, weeks: Int, today: LocalDate): List<Int> {
        val wf = WeekFields.of(Locale.US)
        fun weekKey(d: LocalDate) = d.get(wf.weekBasedYear()) * 100 + d.get(wf.weekOfWeekBasedYear())
        val counts = history.groupingBy { weekKey(it.workout.date) }.eachCount()
        return (weeks - 1 downTo 0).map { back ->
            counts[weekKey(today.minusWeeks(back.toLong()))] ?: 0
        }
    }

    // ---------- per-exercise drilldown ----------

    data class SessionPoint(
        val date: LocalDate,
        val bestE1rmKg: Double?,
        val volumeKg: Double,
    )

    /** One point per training day: best e1RM that day + total day volume. */
    fun sessionSeries(
        sets: List<SetForExercise>,
        type: ExerciseType,
        unilateral: Boolean,
    ): List<SessionPoint> {
        if (!typeSupportsStrengthStats(type)) return emptyList()
        return sets.groupBy { it.workoutDate }
            .toSortedMap()
            .map { (date, daySets) ->
                val counted = daySets.map { it.set }.filter { it.countsForStats() }
                SessionPoint(
                    date = date,
                    bestE1rmKg = counted.mapNotNull { s ->
                        s.weightKg?.let { w -> s.reps?.let { r -> e1rm(w, r) } }
                    }.maxOrNull(),
                    volumeKg = counted.sumOf { setVolumeKg(it, unilateral) },
                )
            }
    }

    data class RepPr(val reps: Int, val weightKg: Double, val date: LocalDate)

    /** Best weight ever lifted at each rep count — "PRs across all rep ranges". */
    fun repPrs(sets: List<SetForExercise>, type: ExerciseType): List<RepPr> {
        if (!typeSupportsStrengthStats(type)) return emptyList()
        return sets
            .filter { it.set.countsForStats() }
            .mapNotNull { s ->
                val w = s.set.weightKg ?: return@mapNotNull null
                val r = s.set.reps ?: return@mapNotNull null
                if (w <= 0 || r <= 0) null else Triple(r, w, s.workoutDate)
            }
            .groupBy { it.first }
            .map { (reps, entries) ->
                val best = entries.maxBy { it.second }
                RepPr(reps, best.second, best.third)
            }
            .sortedBy { it.reps }
    }

    data class BestE1rm(val valueKg: Double, val date: LocalDate)

    fun bestE1rm(sets: List<SetForExercise>, type: ExerciseType): BestE1rm? {
        if (!typeSupportsStrengthStats(type)) return null
        return sets
            .filter { it.set.countsForStats() }
            .mapNotNull { s ->
                val w = s.set.weightKg ?: return@mapNotNull null
                val r = s.set.reps ?: return@mapNotNull null
                e1rm(w, r)?.let { it to s.workoutDate }
            }
            .maxByOrNull { it.first }
            ?.let { BestE1rm(it.first, it.second) }
    }
}
