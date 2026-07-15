package dev.gatsyuk.grindsync.core.model

import java.util.Locale

/**
 * Display-side unit conversion. Storage is ALWAYS kg (SPEC §12.1);
 * these run at render/input time only.
 */
object Weights {

    fun kgToDisplay(kg: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> kg
        WeightUnit.LB -> kg / KG_PER_LB
    }

    fun displayToKg(value: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> value
        WeightUnit.LB -> value * KG_PER_LB
    }

    /** "100", "102.5" — trims trailing zeros, max 2 decimals. */
    fun format(value: Double): String {
        val rounded = String.format(Locale.US, "%.2f", value)
        return rounded.trimEnd('0').trimEnd('.')
    }

    fun formatKgAs(kg: Double, unit: WeightUnit): String = format(kgToDisplay(kg, unit))

    fun unitLabel(unit: WeightUnit): String = when (unit) {
        WeightUnit.KG -> "kg"
        WeightUnit.LB -> "lb"
    }
}

/**
 * Sessions longer than this were almost certainly left running by accident
 * (user feedback) — their duration is hidden and excluded from stats.
 */
const val MAX_TRACKED_DURATION_MILLIS = 2 * 60 * 60 * 1000L

/** Duration between the timestamps, or null if absent/implausible (>2 h). */
fun trackedDurationMillis(startMillis: Long?, endMillis: Long?): Long? {
    if (startMillis == null || endMillis == null) return null
    val duration = endMillis - startMillis
    return duration.takeIf { it in 0..MAX_TRACKED_DURATION_MILLIS }
}

fun formatDurationMillis(millis: Long): String {
    val totalMinutes = millis / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h} h ${m} min" else "${m} min"
}

fun formatSeconds(total: Int): String = "%d:%02d".format(total / 60, total % 60)
