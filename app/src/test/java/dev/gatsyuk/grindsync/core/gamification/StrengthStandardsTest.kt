package dev.gatsyuk.grindsync.core.gamification

import dev.gatsyuk.grindsync.core.model.Rank
import dev.gatsyuk.grindsync.core.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthStandardsTest {

    @Test
    fun `band anchors map to their calibrated scores`() {
        // Back Squat male anchors: D=0.8 C=1.2 B=1.6 A=2.0 S=2.5 (x BW)
        // -> scores 17.5 / 32.5 / 47.5 / 62.5 / 77.5 (mid-tier of each letter).
        val bw = 100.0
        assertEquals(17.5, StrengthStandards.score("Back Squat", 80.0, bw, Sex.MALE)!!, 0.001)
        assertEquals(32.5, StrengthStandards.score("Back Squat", 120.0, bw, Sex.MALE)!!, 0.001)
        assertEquals(47.5, StrengthStandards.score("Back Squat", 160.0, bw, Sex.MALE)!!, 0.001)
        assertEquals(62.5, StrengthStandards.score("Back Squat", 200.0, bw, Sex.MALE)!!, 0.001)
        assertEquals(77.5, StrengthStandards.score("Back Squat", 250.0, bw, Sex.MALE)!!, 0.001)
    }

    @Test
    fun `interpolation is linear between anchors`() {
        // halfway between D (0.8 -> 17.5) and C (1.2 -> 32.5) = 1.0 -> 25.0
        val score = StrengthStandards.score("Back Squat", 100.0, 100.0, Sex.MALE)!!
        assertEquals(25.0, score, 0.001)
    }

    @Test
    fun `female bands scale by documented factor`() {
        // Female D anchor = 0.8 * 0.72 = 0.576 x BW -> exactly the D anchor score
        val score = StrengthStandards.score("Back Squat", 57.6, 100.0, Sex.FEMALE)!!
        assertEquals(17.5, score, 0.001)
    }

    @Test
    fun `world-record territory caps at max score and lands SSS+`() {
        // 2.5 * 1.35 = 3.375x BW -> beyond the WR anchor -> capped
        val wr = StrengthStandards.score("Back Squat", 340.0, 100.0, Sex.MALE)!!
        assertEquals(StrengthStandards.MAX_SCORE, wr, 0.0)
        assertEquals(Rank.SSS_PLUS, Rank.fromScore(wr))
    }

    @Test
    fun `rank ladder tiers resolve by 5-point steps`() {
        assertEquals(Rank.E_MINUS, Rank.fromScore(0.0))
        assertEquals(Rank.E, Rank.fromScore(5.0))
        assertEquals(Rank.E_PLUS, Rank.fromScore(10.0))
        assertEquals(Rank.D_MINUS, Rank.fromScore(17.5))
        assertEquals(Rank.C, Rank.fromScore(35.0))
        assertEquals(Rank.B_MINUS, Rank.fromScore(47.5))
        assertEquals(Rank.A_MINUS, Rank.fromScore(62.5))
        assertEquals(Rank.S_MINUS, Rank.fromScore(77.5))
        assertEquals(Rank.SS_PLUS, Rank.fromScore(100.0))  // world-class GL
        assertEquals(Rank.SSS_PLUS, Rank.fromScore(115.0)) // all-time WR level
        assertEquals(Rank.SSS_PLUS, Rank.fromScore(500.0)) // clamped
    }

    @Test
    fun `no standard or no profile yields null`() {
        assertNull(StrengthStandards.score("Lateral Raise", 20.0, 80.0, Sex.MALE))
        assertNull(StrengthStandards.score("Back Squat", 100.0, 80.0, Sex.UNSET))
    }

    @Test
    fun `all standard names exist in seed catalog naming`() {
        listOf(
            "Back Squat", "Barbell Bench Press", "Deadlift", "Overhead Press",
            "Barbell Row", "Front Squat", "Romanian Deadlift", "Hip Thrust",
            "Barbell Curl", "Skull Crusher",
        ).forEach { assertTrue(it, StrengthStandards.hasStandard(it)) }
    }
}
