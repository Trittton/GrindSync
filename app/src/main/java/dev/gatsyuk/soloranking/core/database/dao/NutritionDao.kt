package dev.gatsyuk.soloranking.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import dev.gatsyuk.soloranking.core.database.entity.DiaryEntryEntity
import dev.gatsyuk.soloranking.core.database.entity.FoodItemEntity
import dev.gatsyuk.soloranking.core.database.entity.NutritionTargetEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class DiaryEntryWithFood(
    @Embedded val entry: DiaryEntryEntity,
    @Relation(parentColumn = "food_item_id", entityColumn = "id")
    val food: FoodItemEntity,
)

@Dao
interface NutritionDao {

    // --- foods ---
    @Insert suspend fun insertFood(food: FoodItemEntity): Long
    @Update suspend fun updateFood(food: FoodItemEntity)

    @Query("SELECT * FROM food_item ORDER BY name")
    fun observeAllFoods(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_item WHERE is_favorite = 1 ORDER BY name")
    fun observeFavorites(): Flow<List<FoodItemEntity>>

    /** Most recently logged distinct foods. */
    @Query(
        """
        SELECT f.* FROM food_item f
        JOIN (SELECT food_item_id, MAX(id) AS latest FROM diary_entry GROUP BY food_item_id) d
          ON f.id = d.food_item_id
        ORDER BY d.latest DESC LIMIT 10
        """,
    )
    fun observeRecentFoods(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_item WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): FoodItemEntity?

    // --- diary ---
    @Insert suspend fun insertEntry(entry: DiaryEntryEntity): Long
    @Update suspend fun updateEntry(entry: DiaryEntryEntity)
    @Delete suspend fun deleteEntry(entry: DiaryEntryEntity)

    @Transaction
    @Query("SELECT * FROM diary_entry WHERE date = :date ORDER BY id")
    fun observeDay(date: LocalDate): Flow<List<DiaryEntryWithFood>>

    @Transaction
    @Query("SELECT * FROM diary_entry WHERE date BETWEEN :start AND :end ORDER BY date")
    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<DiaryEntryWithFood>>

    // --- targets ---
    @Insert suspend fun insertTarget(target: NutritionTargetEntity)

    /** The target in force on [date] (latest effective_date not after it). */
    @Query(
        """
        SELECT * FROM nutrition_target
        WHERE effective_date <= :date
        ORDER BY effective_date DESC, id DESC LIMIT 1
        """,
    )
    fun observeTargetFor(date: LocalDate): Flow<NutritionTargetEntity?>
}
