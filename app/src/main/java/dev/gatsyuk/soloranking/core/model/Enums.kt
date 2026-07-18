package dev.gatsyuk.soloranking.core.model

enum class SetKind { WARMUP, WORKING, DROPSET }

/** How a routine prefills targets when a workout is started from it. */
enum class TargetMode { LATEST, FIXED }

/**
 * Display-only unit preference. All stored weights are canonical kg;
 * conversion happens exclusively at render time (SPEC §12.1).
 */
enum class WeightUnit { KG, LB }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Needed only for strength normalization (IPF GL / standards). */
enum class Sex { UNSET, MALE, FEMALE }

enum class Meal { BREAKFAST, LUNCH, DINNER, SNACK }

/** Where a food's macro data came from. OFF = Open Food Facts (cached copy). */
enum class FoodSource { CUSTOM, OFF }

/**
 * Rank ladder, worst to best: 8 letter families × (−, neutral, +) = 24 tiers.
 * Calibration: score ≈ IPF GL points scale — 100 ≈ world-class (SS+),
 * ≥115 ≈ all-time world-record territory (SSS+). 5 points per tier.
 * UNRANKED (no data) is modeled as null and rendered as a dimmed E−
 * with an explanatory label.
 */
enum class Rank(val label: String) {
    E_MINUS("E-"), E("E"), E_PLUS("E+"),
    D_MINUS("D-"), D("D"), D_PLUS("D+"),
    C_MINUS("C-"), C("C"), C_PLUS("C+"),
    B_MINUS("B-"), B("B"), B_PLUS("B+"),
    A_MINUS("A-"), A("A"), A_PLUS("A+"),
    S_MINUS("S-"), S("S"), S_PLUS("S+"),
    SS_MINUS("SS-"), SS("SS"), SS_PLUS("SS+"),
    SSS_MINUS("SSS-"), SSS("SSS"), SSS_PLUS("SSS+");

    companion object {
        const val POINTS_PER_TIER = 5.0
        fun fromScore(score: Double): Rank =
            entries[(score / POINTS_PER_TIER).toInt().coerceIn(0, entries.size - 1)]
    }
}

const val KG_PER_LB = 0.45359237
