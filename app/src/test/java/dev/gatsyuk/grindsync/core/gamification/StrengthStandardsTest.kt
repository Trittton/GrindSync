package dev.gatsyuk.grindsync.core.gamification

import dev.gatsyuk.grindsync.core.model.Rank
import dev.gatsyuk.grindsync.core.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthStandardsTest {

    @Test
    fun `band anchors map to their exact scores`() {
        // Back Squat male anchors: D=0.8 C=1.2 B=1.6 A=2.0 S=2.5 (x BW)
        val bw = 100.0
        assertEquals(20.0, StrengthStandards.score("Back Squat", 80.0, bw, Sex.MALE)!!, 0.001)
        assertEquals(40.0, StrengthStandards.score("Back Squat", 120.0, bw, Sex.MALE)!!, 0.001)
        assertEquals(60.0, StrengthStandards.score("Back Squat", 160.0, bw, Sex.MALE)!!, 0.001)
        assertEquals(80.0, StrengthStandards.score("Back Squat", 200.0, bw, Sex.MALE)!!, 0.001)
        assertEquals(95.0, StrengthStandards.score("Back Squat", 250.0, bw, Sex.MALE)!!, 0.001)
    }

    @Test
    fun `interpolation is linear between anchors`() {
        // halfway between D (0.8 -> 20) and C (1.2 -> 40) = 1.0 -> 30
        val score = StrengthStandards.score("Back Squat", 100.0, 100.0, Sex.MALE)!!
        assertEquals(30.0, score, 0.001)
    }

    @Test
    fun `female bands scale by documented factor`() {
        // Female D anchor = 0.8 * 0.72 = 0.576 x BW -> exactly score 20
        val score = StrengthStandards.score("Back Squat", 57.6, 100.0, Sex.FEMALE)!!
        assertEquals(20.0, score, 0.001)
    }

    @Test
    fun `scores clamp at 100 and rank letters follow bands`() {
        val monster = StrengthStandards.score("Back Squat", 400.0, 100.0, Sex.MALE)!!
        assertEquals(100.0, monster, 0.0)
        assertEquals(Rank.S, StrengthStandards.rankForScore(monster))
        assertEquals(Rank.E, StrengthStandards.rankForScore(10.0))
        assertEquals(Rank.D, StrengthStandards.rankForScore(20.0))
        assertEquals(Rank.C, StrengthStandards.rankForScore(45.0))
        assertEquals(Rank.B, StrengthStandards.rankForScore(65.0))
        assertEquals(Rank.A, StrengthStandards.rankForScore(85.0))
    }

    @Test
    fun `no standard or no profile yields null`() {
        assertNull(StrengthStandards.score("Lateral Raise", 20.0, 80.0, Sex.MALE))
        assertNull(StrengthStandards.score("Back Squat", 100.0, 80.0, Sex.UNSET))
    }

    @Test
    fun `gl rank bands`() {
        assertEquals(Rank.S, StrengthStandards.rankForGlPoints(92.0))
        assertEquals(Rank.A, StrengthStandards.rankForGlPoints(80.0))
        assertEquals(Rank.B, StrengthStandards.rankForGlPoints(70.0))
        assertEquals(Rank.C, StrengthStandards.rankForGlPoints(55.0))
        assertEquals(Rank.D, StrengthStandards.rankForGlPoints(40.0))
        assertEquals(Rank.E, StrengthStandards.rankForGlPoints(20.0))
    }

    @Test
    fun `all standard names exist in seed catalog naming`() {
        // Guard against silent detachment if seed names drift.
        listOf(
            "Back Squat", "Barbell Bench Press", "Deadlift", "Overhead Press",
            "Barbell Row", "Front Squat", "Romanian Deadlift", "Hip Thrust",
            "Barbell Curl", "Skull Crusher",
        ).forEach { assertTrue(it, StrengthStandards.hasStandard(it)) }
    }
}
