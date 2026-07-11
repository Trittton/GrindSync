package dev.gatsyuk.grindsync.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.gatsyuk.grindsync.core.model.ExerciseType
import dev.gatsyuk.grindsync.core.model.Muscle
import dev.gatsyuk.grindsync.core.model.MuscleRole

/** Display "category" an exercise is filed under (RepCount's Category). */
@Entity(tableName = "muscle_group")
data class MuscleGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "display_order") val displayOrder: Int,
)

@Entity(
    tableName = "exercise",
    foreignKeys = [
        ForeignKey(
            entity = MuscleGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["muscle_group_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("muscle_group_id"), Index(value = ["name"], unique = true)],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "muscle_group_id") val muscleGroupId: Long,
    @ColumnInfo(name = "exercise_type") val exerciseType: ExerciseType,
    @ColumnInfo(name = "is_unilateral") val isUnilateral: Boolean = false,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean = false,
    // Added in schema v2 (MIGRATION_1_2): archive instead of delete, so history survives.
    @ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
)

/**
 * Exercise -> muscle mapping with contribution weights; drives Rank Map
 * aggregation later (SPEC §5.1, §7.7). Seeded from Phase 0 by design.
 */
@Entity(
    tableName = "exercise_muscle",
    primaryKeys = ["exercise_id", "muscle"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exercise_id")],
)
data class ExerciseMuscleEntity(
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    val muscle: Muscle,
    val role: MuscleRole,
    @ColumnInfo(name = "contribution_weight") val contributionWeight: Double,
)
