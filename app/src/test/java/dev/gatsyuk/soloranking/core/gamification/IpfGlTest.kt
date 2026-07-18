package dev.gatsyuk.soloranking.core.gamification

import dev.gatsyuk.soloranking.core.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coefficients verified against the official IPF 2020 coefficient sheet
 * (powerlifting.sport/fileadmin/ipf/data/ipf-formula/IPF_GL_Coefficients-2020.pdf).
 * Reference values computed with those published coefficients.
 */
class IpfGlTest {

    @Test
    fun `male 93kg 700kg total is about 91_57 points`() {
        val points = IpfGl.points(totalKg = 700.0, bodyweightKg = 93.0, sex = Sex.MALE)!!
        assertEquals(91.57, points, 0.05)
    }

    @Test
    fun `female 63kg 400kg total is world-class territory`() {
        val points = IpfGl.points(totalKg = 400.0, bodyweightKg = 63.0, sex = Sex.FEMALE)!!
        // denominator = 610.32796 - 1045.59282 * e^(-0.03048*63) ≈ 456.97 -> ~87.5
        assertEquals(87.5, points, 0.5)
    }

    @Test
    fun `heavier lifter needs bigger total for same points`() {
        val light = IpfGl.points(500.0, 74.0, Sex.MALE)!!
        val heavy = IpfGl.points(500.0, 120.0, Sex.MALE)!!
        assertTrue("same total must score lower at higher BW", heavy < light)
    }

    @Test
    fun `unset sex or zero total yields no points`() {
        assertNull(IpfGl.points(500.0, 90.0, Sex.UNSET))
        assertNull(IpfGl.points(0.0, 90.0, Sex.MALE))
    }
}
