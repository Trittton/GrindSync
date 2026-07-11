package dev.gatsyuk.grindsync.core.stats

import dev.gatsyuk.grindsync.core.database.dao.DiaryEntryWithFood
import dev.gatsyuk.grindsync.core.database.entity.DiaryEntryEntity
import dev.gatsyuk.grindsync.core.database.entity.FoodItemEntity
import dev.gatsyuk.grindsync.core.model.Meal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class NutritionMathTest {

    private fun entry(food: FoodItemEntity, qty: Double) = DiaryEntryWithFood(
        entry = DiaryEntryEntity(
            date = LocalDate.of(2026, 7, 11), meal = Meal.LUNCH,
            foodItemId = food.id, quantityServings = qty,
        ),
        food = food,
    )

    @Test
    fun `totals scale by quantity and sum across entries`() {
        val oats = FoodItemEntity(
            id = 1, name = "Oats", kcalPerServing = 370.0,
            proteinG = 13.0, carbsG = 60.0, fatG = 7.0,
        )
        val chicken = FoodItemEntity(
            id = 2, name = "Chicken Breast", kcalPerServing = 165.0,
            proteinG = 31.0, carbsG = 0.0, fatG = 3.6,
        )
        // 80 g oats (0.8 servings of 100 g) + 200 g chicken (2.0)
        val totals = NutritionMath.totals(listOf(entry(oats, 0.8), entry(chicken, 2.0)))
        assertEquals(370 * 0.8 + 165 * 2.0, totals.kcal, 0.001)
        assertEquals(13 * 0.8 + 31 * 2.0, totals.proteinG, 0.001)
        assertEquals(60 * 0.8, totals.carbsG, 0.001)
        assertEquals(7 * 0.8 + 3.6 * 2.0, totals.fatG, 0.001)
    }

    @Test
    fun `empty day is all zeros`() {
        val totals = NutritionMath.totals(emptyList())
        assertEquals(0.0, totals.kcal, 0.0)
        assertEquals(0.0, totals.proteinG, 0.0)
    }
}
