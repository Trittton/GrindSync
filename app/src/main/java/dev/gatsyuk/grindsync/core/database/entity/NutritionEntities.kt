package dev.gatsyuk.grindsync.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.gatsyuk.grindsync.core.model.FoodSource
import dev.gatsyuk.grindsync.core.model.Meal
import java.time.LocalDate

/**
 * A food with macros per serving (SPEC §5.3). OFF items are normalized to a
 * 100 g serving; custom foods use whatever serving the user defines. Fetched
 * values are always user-overridable — corrected copies become CUSTOM (§8.3).
 */
@Entity(tableName = "food_item", indices = [Index("name")])
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val source: FoodSource = FoodSource.CUSTOM,
    val barcode: String? = null,
    @ColumnInfo(name = "serving_size") val servingSize: Double = 100.0,
    @ColumnInfo(name = "serving_unit") val servingUnit: String = "g",
    @ColumnInfo(name = "kcal_per_serving") val kcalPerServing: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    @ColumnInfo(name = "carbs_g") val carbsG: Double,
    @ColumnInfo(name = "fat_g") val fatG: Double,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
)

@Entity(
    tableName = "diary_entry",
    foreignKeys = [
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_item_id"],
            // Foods with logged history can't be hard-deleted — protects the diary.
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("date"), Index("food_item_id")],
)
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val meal: Meal,
    @ColumnInfo(name = "food_item_id") val foodItemId: Long,
    @ColumnInfo(name = "quantity_servings") val quantityServings: Double,
)

/** Daily targets; a new row per change keeps history for past-day rendering. */
@Entity(tableName = "nutrition_target", indices = [Index("effective_date")])
data class NutritionTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "effective_date") val effectiveDate: LocalDate,
    @ColumnInfo(name = "kcal_target") val kcalTarget: Int,
    @ColumnInfo(name = "protein_target_g") val proteinTargetG: Int,
    @ColumnInfo(name = "carbs_target_g") val carbsTargetG: Int,
    @ColumnInfo(name = "fat_target_g") val fatTargetG: Int,
)
