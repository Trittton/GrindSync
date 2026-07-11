package dev.gatsyuk.grindsync.core.gamification

import dev.gatsyuk.grindsync.core.database.dao.ExerciseWithMuscles
import dev.gatsyuk.grindsync.core.database.dao.WorkoutWithContent
import dev.gatsyuk.grindsync.core.model.Muscle
import dev.gatsyuk.grindsync.core.model.MuscleRole
import dev.gatsyuk.grindsync.core.model.Rank
import dev.gatsyuk.grindsync.core.model.SetKind
import dev.gatsyuk.grindsync.core.model.Sex
import dev.gatsyuk.grindsync.core.stats.StatsCalculator
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The Solo-Leveling layer, computed ENTIRELY from raw history on demand.
 * There is deliberately no persisted XP/rank state (NFR-5 taken to its
 * conclusion): change a formula, and every number below updates everywhere.
 *
 * Anti-cheese rules (SPEC §7.5), all tunable constants below:
 * - Volume XP has diminishing returns: only the first [SET_XP_CAP_SETS]
 *   working sets per exercise per workout earn XP.
 * - PRs outrank volume: an e1RM PR is worth 10 working sets.
 * - Hard per-exercise session cap [SESSION_XP_CAP].
 * - Implausible entries (weight > [MAX_PLAUSIBLE_WEIGHT_KG], reps >
 *   [MAX_PLAUSIBLE_REPS]) earn nothing and set no records.
 */
object RankEngine {

    // --- XP tuning ---
    private const val XP_PER_SET = 10
    private const val SET_XP_CAP_SETS = 6
    private const val XP_E1RM_PR = 100
    private const val XP_REP_PR = 25
    private const val REP_PR_XP_CAP = 75
    private const val SESSION_XP_CAP = 200
    private const val XP_PER_WORKOUT = 50
    private const val MAX_PLAUSIBLE_WEIGHT_KG = 500.0
    private const val MAX_PLAUSIBLE_REPS = 100

    // Level n costs 100·n XP (per-exercise) / 500·n XP (overall) -> quadratic cumulative.
    private const val EXERCISE_LEVEL_UNIT = 50   // cumulative = 50·n·(n+1)
    private const val OVERALL_LEVEL_UNIT = 250   // cumulative = 250·n·(n+1)

    // Seed-catalog names of the GL total lifts.
    private const val SQUAT = "Back Squat"
    private const val BENCH = "Barbell Bench Press"
    private const val DEADLIFT = "Deadlift"

    data class LevelInfo(val level: Int, val xpInto: Int, val xpForNext: Int)

    data class ExerciseStanding(
        val exerciseId: Long,
        val name: String,
        val xp: Int,
        val level: LevelInfo,
        val bestE1rmKg: Double?,
        val score: Double?,   // null = no standard or no profile data
        val rank: Rank?,      // null = unranked
    )

    data class MuscleContributor(
        val exerciseName: String,
        val score: Double,
        val contributionWeight: Double,
        val role: MuscleRole,
    )

    data class MuscleRank(
        val muscle: Muscle,
        val score: Double,
        val rank: Rank,
        val contributors: List<MuscleContributor>,
    )

    data class AchievementState(
        val key: String,
        val title: String,
        val description: String,
        val unlockedOn: LocalDate?,
    )

    data class StatSheet(
        val strengthGl: Double?,      // IPF GL points, null until SBD + profile exist
        val enduranceReps28d: Int,    // working reps in the last 28 days
        val consistencyPct: Int,      // % of last 8 weeks with >=2 workouts
    )

    data class GamificationState(
        val overallXp: Int,
        val overallLevel: LevelInfo,
        val glPoints: Double?,
        val overallRank: Rank?,       // null = unranked (missing SBD/profile)
        val statSheet: StatSheet,
        val currentStreakWeeks: Int,
        val longestStreakWeeks: Int,
        val exercises: List<ExerciseStanding>,
        val muscleRanks: Map<Muscle, MuscleRank>, // muscles absent = UNRANKED
        val achievements: List<AchievementState>,
        val totalPrCount: Int,
    )

    fun compute(
        history: List<WorkoutWithContent>,
        catalog: List<ExerciseWithMuscles>,
        sex: Sex,
        bodyweightFallbackKg: Double?,
        today: LocalDate = LocalDate.now(),
    ): GamificationState {
        val chronological = history.sortedWith(compareBy({ it.workout.date }, { it.workout.id }))

        // --- single chronological pass: XP + PR events ---
        val xpByExercise = mutableMapOf<Long, Int>()
        val bestE1rm = mutableMapOf<Long, Double>()
        val bestWeightAtReps = mutableMapOf<Long, MutableMap<Int, Double>>()
        var prEvents = 0

        chronological.forEach { workout ->
            workout.exercises.forEach { entry ->
                val type = entry.exercise.exerciseType
                val strength = StatsCalculator.typeSupportsStrengthStats(type)
                var sessionXp = 0
                var countedSets = 0
                var repPrXp = 0
                var e1rmPrAwarded = false

                entry.sets.sortedBy { it.position }.forEach sets@{ set ->
                    if (set.setKind == SetKind.WARMUP) return@sets
                    val w = set.weightKg
                    val r = set.reps
                    if ((w ?: 0.0) > MAX_PLAUSIBLE_WEIGHT_KG || (r ?: 0) > MAX_PLAUSIBLE_REPS) return@sets
                    // Untouched prefab rows (all fields empty) never earn XP —
                    // START pre-creates target sets, and blanks must not farm anything.
                    val hasData = w != null || r != null || set.timeSeconds != null ||
                        set.distanceMeters != null || set.kcal != null
                    if (!hasData) return@sets

                    countedSets++
                    if (countedSets <= SET_XP_CAP_SETS) sessionXp += XP_PER_SET

                    if (strength && w != null && w > 0 && r != null && r > 0) {
                        val e1 = StatsCalculator.e1rm(w, r)
                        if (e1 != null) {
                            val prev = bestE1rm[entry.exercise.id]
                            if (prev == null || e1 > prev) {
                                bestE1rm[entry.exercise.id] = e1
                                if (prev != null && !e1rmPrAwarded) {
                                    sessionXp += XP_E1RM_PR
                                    e1rmPrAwarded = true
                                    prEvents++
                                }
                            }
                        }

                        val perReps = bestWeightAtReps.getOrPut(entry.exercise.id) { mutableMapOf() }
                        val prevAtReps = perReps[r]
                        if (prevAtReps == null || w > prevAtReps) {
                            perReps[r] = w
                            if (prevAtReps != null && repPrXp < REP_PR_XP_CAP) {
                                sessionXp += XP_REP_PR
                                repPrXp += XP_REP_PR
                                prEvents++
                            }
                        }
                    }
                }

                xpByExercise.merge(
                    entry.exercise.id,
                    sessionXp.coerceAtMost(SESSION_XP_CAP),
                    Int::plus,
                )
            }
        }

        // --- profile inputs for normalization ---
        val bodyweightKg = chronological.lastOrNull { it.workout.bodyweightKg != null }
            ?.workout?.bodyweightKg ?: bodyweightFallbackKg

        val exerciseByName = catalog.associateBy { it.exercise.name }
        fun bestE1rmByName(name: String): Double? =
            exerciseByName[name]?.let { bestE1rm[it.exercise.id] }

        // --- overall GL rank ---
        val sbd = listOf(bestE1rmByName(SQUAT), bestE1rmByName(BENCH), bestE1rmByName(DEADLIFT))
        val glPoints = if (sbd.all { it != null } && bodyweightKg != null) {
            IpfGl.points(sbd.filterNotNull().sum(), bodyweightKg, sex)
        } else {
            null
        }
        val overallRank = glPoints?.let { Rank.fromScore(it) }

        // --- per-exercise standings + muscle ranks ---
        val standings = catalog
            .filter { bestE1rm.containsKey(it.exercise.id) || xpByExercise.containsKey(it.exercise.id) }
            .map { entry ->
                val xp = xpByExercise[entry.exercise.id] ?: 0
                val e1 = bestE1rm[entry.exercise.id]
                val score = if (e1 != null && bodyweightKg != null) {
                    StrengthStandards.score(entry.exercise.name, e1, bodyweightKg, sex)
                } else {
                    null
                }
                ExerciseStanding(
                    exerciseId = entry.exercise.id,
                    name = entry.exercise.name,
                    xp = xp,
                    level = levelInfo(xp, EXERCISE_LEVEL_UNIT),
                    bestE1rmKg = e1,
                    score = score,
                    rank = score?.let { Rank.fromScore(it) },
                )
            }
            .sortedByDescending { it.xp }

        val scoreByExerciseId = standings.filter { it.score != null }.associate { it.exerciseId to it.score!! }
        val muscleRanks = mutableMapOf<Muscle, MuscleRank>()
        Muscle.entries.forEach { muscle ->
            val contributors = catalog.mapNotNull { entry ->
                val score = scoreByExerciseId[entry.exercise.id] ?: return@mapNotNull null
                val mapping = entry.muscles.firstOrNull { it.muscle == muscle } ?: return@mapNotNull null
                MuscleContributor(entry.exercise.name, score, mapping.contributionWeight, mapping.role)
            }
            if (contributors.isNotEmpty()) {
                val weighted = contributors.sumOf { it.score * it.contributionWeight } /
                    contributors.sumOf { it.contributionWeight }
                muscleRanks[muscle] = MuscleRank(
                    muscle = muscle,
                    score = weighted,
                    rank = Rank.fromScore(weighted),
                    contributors = contributors.sortedByDescending { it.contributionWeight },
                )
            }
        }

        // --- streaks (consecutive ISO training weeks; alive through last week) ---
        val wf = WeekFields.of(Locale.US)
        fun weekIndex(d: LocalDate): Long = d.get(wf.weekBasedYear()) * 100L + d.get(wf.weekOfWeekBasedYear())
        val weeks = chronological.map { weekIndex(it.workout.date) }.distinct().sorted()
        var longest = 0
        var run = 0
        var prev: Long? = null
        var streakDates = mutableListOf<Pair<Long, Int>>() // weekIdx -> run length ending there
        weeks.forEach { wk ->
            run = if (prev != null && consecutiveWeeks(prev!!, wk)) run + 1 else 1
            streakDates.add(wk to run)
            if (run > longest) longest = run
            prev = wk
        }
        val thisWeek = weekIndex(today)
        val lastRun = streakDates.lastOrNull()
        val current = if (lastRun != null &&
            (lastRun.first == thisWeek || consecutiveWeeks(lastRun.first, thisWeek))
        ) {
            lastRun.second
        } else {
            0
        }

        // --- stat sheet ---
        val cutoff28 = today.minusDays(28)
        val reps28 = chronological
            .filter { it.workout.date >= cutoff28 }
            .sumOf { w ->
                w.exercises.sumOf { e ->
                    e.sets.filter { it.setKind != SetKind.WARMUP }.sumOf { it.reps ?: 0 }
                }
            }
        val goodWeeks = StatsCalculator.weeklyFrequency(chronological, 8, today).count { it >= 2 }
        val statSheet = StatSheet(
            strengthGl = glPoints,
            enduranceReps28d = reps28,
            consistencyPct = goodWeeks * 100 / 8,
        )

        // --- achievements: deliberately empty — the owner will design the set
        // worth having; the pipeline (derived, dated) stays in place. ---
        val achievements = emptyList<AchievementState>()

        val overallXp = xpByExercise.values.sum() + chronological.size * XP_PER_WORKOUT
        return GamificationState(
            overallXp = overallXp,
            overallLevel = levelInfo(overallXp, OVERALL_LEVEL_UNIT),
            glPoints = glPoints,
            overallRank = overallRank,
            statSheet = statSheet,
            currentStreakWeeks = current,
            longestStreakWeeks = longest,
            exercises = standings,
            muscleRanks = muscleRanks,
            achievements = achievements,
            totalPrCount = prEvents,
        )
    }

    /** cumulative XP to complete level n = unit·n·(n+1). */
    private fun levelInfo(xp: Int, unit: Int): LevelInfo {
        var level = 0
        while (unit * (level + 1) * (level + 2) <= xp) level++
        val floor = unit * level * (level + 1)
        val ceil = unit * (level + 1) * (level + 2)
        return LevelInfo(level = level + 1, xpInto = xp - floor, xpForNext = ceil - floor)
    }

    private fun consecutiveWeeks(a: Long, b: Long): Boolean {
        if (b == a + 1) return true
        // year rollover: ...52 (or 53) of year Y -> week 1 of year Y+1
        val yearA = (a / 100).toInt()
        val weekA = (a % 100).toInt()
        val yearB = (b / 100).toInt()
        val weekB = (b % 100).toInt()
        return yearB == yearA + 1 && weekB == 1 && weekA >= 52
    }
}
