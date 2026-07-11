package dev.gatsyuk.grindsync.core.gamification

import dev.gatsyuk.grindsync.core.model.Rank
import dev.gatsyuk.grindsync.core.model.Sex

/**
 * Per-exercise relative-strength standards (SPEC §7.3): e1RM expressed as a
 * bodyweight multiple, banded D..S (below D = E). IPF GL can't rank a lateral
 * raise, so accessories/compounds get these authored tables instead.
 *
 * v1 limitations (documented, revisit in tuning pass):
 * - Tables authored for the barbell STRENGTH_WEIGHT_REPS lifts only.
 *   Bodyweight/assisted/cardio modes have no standard yet -> unranked, still earn XP.
 * - Male anchors; female = male × 0.72 (common relative-strength approximation)
 *   until per-lift female tables are authored.
 * - Keyed by seed-catalog exercise name; renaming a seeded lift detaches its standard.
 */
object StrengthStandards {

    private const val FEMALE_FACTOR = 0.72

    /** BW multiples that anchor ranks D, C, B, A, S (scores 20/40/60/80/95). */
    data class Bands(val d: Double, val c: Double, val b: Double, val a: Double, val s: Double)

    private val MALE_BANDS: Map<String, Bands> = mapOf(
        "Back Squat" to Bands(0.80, 1.20, 1.60, 2.00, 2.50),
        "Barbell Bench Press" to Bands(0.60, 0.90, 1.25, 1.60, 2.00),
        "Deadlift" to Bands(1.00, 1.50, 2.00, 2.50, 3.00),
        "Overhead Press" to Bands(0.40, 0.60, 0.80, 1.05, 1.30),
        "Barbell Row" to Bands(0.50, 0.75, 1.00, 1.30, 1.60),
        "Front Squat" to Bands(0.65, 1.00, 1.35, 1.70, 2.10),
        "Romanian Deadlift" to Bands(0.80, 1.20, 1.60, 2.00, 2.40),
        "Hip Thrust" to Bands(1.00, 1.50, 2.00, 2.60, 3.20),
        "Barbell Curl" to Bands(0.25, 0.40, 0.55, 0.75, 0.95),
        "Skull Crusher" to Bands(0.25, 0.40, 0.55, 0.70, 0.90),
    )

    fun hasStandard(exerciseName: String): Boolean = exerciseName in MALE_BANDS

    /**
     * 0..100 score for [e1rmKg] at [bodyweightKg]. Piecewise-linear through the
     * band anchors: D=20, C=40, B=60, A=80, S=95; 0 at zero, 100 well past S.
     */
    fun score(exerciseName: String, e1rmKg: Double, bodyweightKg: Double, sex: Sex): Double? {
        if (sex == Sex.UNSET || bodyweightKg <= 0 || e1rmKg <= 0) return null
        val male = MALE_BANDS[exerciseName] ?: return null
        val f = if (sex == Sex.FEMALE) FEMALE_FACTOR else 1.0
        val multiple = e1rmKg / bodyweightKg
        val anchors = listOf(
            0.0 to 0.0,
            male.d * f to 20.0,
            male.c * f to 40.0,
            male.b * f to 60.0,
            male.a * f to 80.0,
            male.s * f to 95.0,
            male.s * f * 1.3 to 100.0,
        )
        if (multiple >= anchors.last().first) return 100.0
        val upper = anchors.indexOfFirst { multiple < it.first }
        val (x0, y0) = anchors[upper - 1]
        val (x1, y1) = anchors[upper]
        return y0 + (y1 - y0) * (multiple - x0) / (x1 - x0)
    }

    /** Score bands shared by exercise ranks, muscle ranks, and the GL rank scale. */
    fun rankForScore(score: Double): Rank = when {
        score >= 95 -> Rank.S
        score >= 80 -> Rank.A
        score >= 60 -> Rank.B
        score >= 40 -> Rank.C
        score >= 20 -> Rank.D
        else -> Rank.E
    }

    /**
     * Overall rank bands for IPF GL points (~100 = world-class). Tunable.
     * 93 kg male reference: ~380 kg total ≈ 50 GL (C), ~600 ≈ 78 (B/A), 700+ ≈ 92 (S).
     */
    fun rankForGlPoints(gl: Double): Rank = when {
        gl >= 92 -> Rank.S
        gl >= 80 -> Rank.A
        gl >= 65 -> Rank.B
        gl >= 50 -> Rank.C
        gl >= 35 -> Rank.D
        else -> Rank.E
    }
}
