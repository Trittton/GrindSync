package dev.gatsyuk.soloranking.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.gatsyuk.soloranking.core.model.TargetMode

/** Reusable workout template (flat — no periodization, SPEC §12.3). */
@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "target_mode") val targetMode: TargetMode = TargetMode.LATEST,
    val notes: String? = null,
    @ColumnInfo(name = "display_order") val displayOrder: Int = 0,
)

@Entity(
    tableName = "routine_exercise",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("routine_id"), Index("exercise_id")],
)
data class RoutineExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "routine_id") val routineId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    val position: Int,
    @ColumnInfo(name = "target_sets") val targetSets: Int,
    // Nullable: rep ranges only make sense for rep-based exercise types.
    @ColumnInfo(name = "rep_min") val repMin: Int? = null,
    @ColumnInfo(name = "rep_max") val repMax: Int? = null,
)
