package dev.gatsyuk.grindsync.core.stats

import dev.gatsyuk.grindsync.core.database.dao.DiaryEntryWithFood

/** Pure macro arithmetic over diary entries — recomputable, no cached state. */
object NutritionMath {

    data class DayTotals(
        val kcal: Double,
        val proteinG: Double,
        val carbsG: Double,
        val fatG: Double,
    )

    fun totals(entries: List<DiaryEntryWithFood>): DayTotals {
        var kcal = 0.0
        var protein = 0.0
        var carbs = 0.0
        var fat = 0.0
        entries.forEach { e ->
            val q = e.entry.quantityServings
            kcal += e.food.kcalPerServing * q
            protein += e.food.proteinG * q
            carbs += e.food.carbsG * q
            fat += e.food.fatG * q
        }
        return DayTotals(kcal, protein, carbs, fat)
    }
}
