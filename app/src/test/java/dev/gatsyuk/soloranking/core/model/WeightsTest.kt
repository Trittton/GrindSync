package dev.gatsyuk.soloranking.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** kg is canonical; lb is a render-time view. Conversions must round-trip. */
class WeightsTest {

    @Test
    fun `kg to lb uses exact avoirdupois factor`() {
        // 100 kg = 220.4623 lb
        assertEquals(220.4623, Weights.kgToDisplay(100.0, WeightUnit.LB), 0.0001)
    }

    @Test
    fun `lb input converts to kg for storage`() {
        assertEquals(45.359237, Weights.displayToKg(100.0, WeightUnit.LB), 1e-9)
    }

    @Test
    fun `kg passes through untouched`() {
        assertEquals(102.5, Weights.kgToDisplay(102.5, WeightUnit.KG), 0.0)
        assertEquals(102.5, Weights.displayToKg(102.5, WeightUnit.KG), 0.0)
    }

    @Test
    fun `round trip loses less than a gram`() {
        val original = 87.5
        val roundTripped = Weights.displayToKg(Weights.kgToDisplay(original, WeightUnit.LB), WeightUnit.LB)
        assertEquals(original, roundTripped, 0.001)
    }

    @Test
    fun `format trims trailing zeros`() {
        assertEquals("100", Weights.format(100.0))
        assertEquals("102.5", Weights.format(102.5))
        assertEquals("62.35", Weights.format(62.349))
    }

    @Test
    fun `duration and seconds formatting`() {
        assertEquals("1 h 23 min", formatDurationMillis((60 + 23) * 60_000L))
        assertEquals("45 min", formatDurationMillis(45 * 60_000L))
        assertEquals("2:05", formatSeconds(125))
    }
}
