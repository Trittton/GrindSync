package dev.gatsyuk.grindsync.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gatsyuk.grindsync.core.database.AppDatabase
import dev.gatsyuk.grindsync.core.database.entity.FoodItemEntity
import dev.gatsyuk.grindsync.core.database.entity.NutritionTargetEntity
import dev.gatsyuk.grindsync.core.model.FoodSource
import dev.gatsyuk.grindsync.core.model.Meal
import dev.gatsyuk.grindsync.core.stats.NutritionMath
import dev.gatsyuk.grindsync.feature.nutrition.data.OpenFoodFactsClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Phase 4 primary flow at the data layer: foods -> diary -> totals -> targets. */
@RunWith(AndroidJUnit4::class)
class NutritionFlowTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: NutritionRepository

    private val today = LocalDate.of(2026, 7, 11)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repo = NutritionRepository(db.nutritionDao(), OpenFoodFactsClient())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun logFoods_dayTotals_targetResolution() = runTest {
        val oatsId = repo.createFood(
            FoodItemEntity(
                name = "Oats", kcalPerServing = 370.0,
                proteinG = 13.0, carbsG = 60.0, fatG = 7.0,
            ),
        )
        repo.logEntry(today, Meal.BREAKFAST, oatsId, 0.8)
        repo.logEntry(today, Meal.LUNCH, oatsId, 1.0)

        val day = db.nutritionDao().observeDay(today).first()
        assertEquals(2, day.size)
        val totals = NutritionMath.totals(day)
        assertEquals(370 * 1.8, totals.kcal, 0.001)

        // Other days unaffected.
        assertEquals(0, db.nutritionDao().observeDay(today.plusDays(1)).first().size)

        // Target resolution: none -> null; set one -> applies from its date on.
        assertEquals(null, db.nutritionDao().observeTargetFor(today).first())
        db.nutritionDao().insertTarget(
            NutritionTargetEntity(
                effectiveDate = today, kcalTarget = 2800,
                proteinTargetG = 160, carbsTargetG = 300, fatTargetG = 90,
            ),
        )
        assertEquals(2800, db.nutritionDao().observeTargetFor(today).first()!!.kcalTarget)
        assertEquals(2800, db.nutritionDao().observeTargetFor(today.plusDays(5)).first()!!.kcalTarget)
        assertEquals(null, db.nutritionDao().observeTargetFor(today.minusDays(1)).first())
    }

    @Test
    fun offFoods_cacheOnAdd_dedupeByBarcode() = runTest {
        val offFood = FoodItemEntity(
            name = "Peanut Butter", source = FoodSource.OFF, barcode = "737628064502",
            kcalPerServing = 588.0, proteinG = 25.0, carbsG = 20.0, fatG = 50.0,
        )
        val id1 = repo.ensureLocalFood(offFood)
        val id2 = repo.ensureLocalFood(offFood) // same barcode -> same row
        assertEquals(id1, id2)
        assertEquals(1, db.nutritionDao().observeAllFoods().first().size)

        repo.logEntry(today, Meal.SNACK, id1, 0.3)
        val recents = db.nutritionDao().observeRecentFoods().first()
        assertEquals("Peanut Butter", recents.single().name)
    }
}
