package dev.gatsyuk.soloranking.core.gamification

import dev.gatsyuk.soloranking.core.model.Sex
import kotlin.math.exp

/**
 * Strength normalization behind an interface so DOTS can drop in later
 * (SPEC §7.3 "keep it swappable").
 */
interface StrengthNormalization {
    /** Normalized points for a squat+bench+deadlift [totalKg] at [bodyweightKg]. */
    fun points(totalKg: Double, bodyweightKg: Double, sex: Sex): Double?
}

/**
 * IPF GL ("Goodlift") points — classic/raw 3-lift coefficients from the
 * official IPF 2020 coefficient sheet (powerlifting.sport, verified 2026-07-11):
 *   GL = total × 100 / (A − B·e^(−C·BW))
 * Men:   A=1199.72839  B=1025.18162  C=0.009210
 * Women: A=610.32796   B=1045.59282  C=0.03048
 * ~100 points ≈ world-class. Reference check: 93 kg male, 700 kg total ≈ 91.57.
 */
object IpfGl : StrengthNormalization {

    private data class Coefficients(val a: Double, val b: Double, val c: Double)

    private val MEN = Coefficients(1199.72839, 1025.18162, 0.009210)
    private val WOMEN = Coefficients(610.32796, 1045.59282, 0.03048)

    // Official formula validity range for classic competition.
    private const val MIN_BW = 35.0
    private const val MAX_BW = 200.0

    override fun points(totalKg: Double, bodyweightKg: Double, sex: Sex): Double? {
        if (totalKg <= 0) return null
        val coeff = when (sex) {
            Sex.MALE -> MEN
            Sex.FEMALE -> WOMEN
            Sex.UNSET -> return null
        }
        val bw = bodyweightKg.coerceIn(MIN_BW, MAX_BW)
        val denominator = coeff.a - coeff.b * exp(-coeff.c * bw)
        if (denominator <= 0) return null
        return totalKg * 100.0 / denominator
    }
}
