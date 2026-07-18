package dev.gatsyuk.soloranking.core.data

import dev.gatsyuk.soloranking.core.database.dao.DiaryEntryWithFood
import dev.gatsyuk.soloranking.core.database.dao.NutritionDao
import dev.gatsyuk.soloranking.core.database.entity.DiaryEntryEntity
import dev.gatsyuk.soloranking.core.database.entity.FoodItemEntity
import dev.gatsyuk.soloranking.core.database.entity.NutritionTargetEntity
import dev.gatsyuk.soloranking.core.model.Meal
import dev.gatsyuk.soloranking.feature.nutrition.data.OpenFoodFactsClient
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NutritionRepository @Inject constructor(
    private val dao: NutritionDao,
    private val offClient: OpenFoodFactsClient,
) {

    // Sensible defaults until the user sets targets.
    val defaultTarget = NutritionTargetEntity(
        effectiveDate = LocalDate.ofEpochDay(0),
        kcalTarget = 2500, proteinTargetG = 150, carbsTargetG = 250, fatTargetG = 80,
    )

    fun observeDay(date: LocalDate): Flow<List<DiaryEntryWithFood>> = dao.observeDay(date)
    fun observeRange(start: LocalDate, end: LocalDate) = dao.observeRange(start, end)
    fun observeAllFoods() = dao.observeAllFoods()
    fun observeFavorites() = dao.observeFavorites()
    fun observeRecentFoods() = dao.observeRecentFoods()
    fun observeTargetFor(date: LocalDate): Flow<NutritionTargetEntity?> = dao.observeTargetFor(date)

    suspend fun createFood(food: FoodItemEntity): Long = dao.insertFood(food)

    suspend fun toggleFavorite(food: FoodItemEntity) =
        dao.updateFood(food.copy(isFavorite = !food.isFavorite))

    /**
     * Log an OFF search result: cache it locally first (dedup by barcode) so
     * the food works offline forever after (SPEC §8.1).
     */
    suspend fun ensureLocalFood(food: FoodItemEntity): Long {
        if (food.id != 0L) return food.id
        val existing = food.barcode?.let { dao.findByBarcode(it) }
        return existing?.id ?: dao.insertFood(food)
    }

    suspend fun logEntry(date: LocalDate, meal: Meal, foodId: Long, quantityServings: Double) {
        dao.insertEntry(
            DiaryEntryEntity(
                date = date, meal = meal,
                foodItemId = foodId, quantityServings = quantityServings,
            ),
        )
    }

    suspend fun updateEntryQuantity(entry: DiaryEntryEntity, quantityServings: Double) =
        dao.updateEntry(entry.copy(quantityServings = quantityServings))

    suspend fun deleteEntry(entry: DiaryEntryEntity) = dao.deleteEntry(entry)

    suspend fun setTarget(kcal: Int, protein: Int, carbs: Int, fat: Int) {
        dao.insertTarget(
            NutritionTargetEntity(
                effectiveDate = LocalDate.now(),
                kcalTarget = kcal, proteinTargetG = protein,
                carbsTargetG = carbs, fatTargetG = fat,
            ),
        )
    }

    /** Explicit user action only (NFR-8). */
    suspend fun searchOpenFoodFacts(query: String) = offClient.search(query)
}
