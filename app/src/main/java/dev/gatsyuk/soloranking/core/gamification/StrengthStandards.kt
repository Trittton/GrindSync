package dev.gatsyuk.soloranking.core.gamification

import dev.gatsyuk.soloranking.core.model.Sex

/**
 * Per-exercise relative-strength standards (SPEC §7.3): e1RM expressed as a
 * bodyweight multiple, mapped onto the unified 0–120 score scale that also
 * carries IPF GL points. Anchor calibration (see [dev.gatsyuk.soloranking.core.model.Rank]):
 *   D anchor -> 17.5 (mid-D) · C -> 32.5 · B -> 47.5 · A -> 62.5 · S -> 77.5
 *   elite×1.35 -> 117 (≈ all-time WR -> SSS+) · hard cap 120.
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
    private const val WR_MULTIPLE_FACTOR = 1.35
    const val MAX_SCORE = 120.0

    /** BW multiples anchoring scores 17.5 / 32.5 / 47.5 / 62.5 / 77.5. */
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
     * 0..[MAX_SCORE] score for [e1rmKg] at [bodyweightKg], piecewise-linear
     * through the band anchors. Null when no standard/profile applies.
     */
    fun score(exerciseName: String, e1rmKg: Double, bodyweightKg: Double, sex: Sex): Double? {
        if (sex == Sex.UNSET || bodyweightKg <= 0 || e1rmKg <= 0) return null
        val male = MALE_BANDS[exerciseName] ?: return null
        val f = if (sex == Sex.FEMALE) FEMALE_FACTOR else 1.0
        val multiple = e1rmKg / bodyweightKg
        val anchors = listOf(
            0.0 to 0.0,
            male.d * f to 17.5,
            male.c * f to 32.5,
            male.b * f to 47.5,
            male.a * f to 62.5,
            male.s * f to 77.5,
            male.s * f * WR_MULTIPLE_FACTOR to 117.0,
        )
        if (multiple >= anchors.last().first) return MAX_SCORE
        val upper = anchors.indexOfFirst { multiple < it.first }
        val (x0, y0) = anchors[upper - 1]
        val (x1, y1) = anchors[upper]
        return y0 + (y1 - y0) * (multiple - x0) / (x1 - x0)
    }
}
