package dev.gatsyuk.grindsync.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.gatsyuk.grindsync.core.model.SetKind
import java.time.LocalDate

/**
 * A performed session (a Log entry). All weights here and in [SetEntryEntity]
 * are canonical kg — kg/lb is a display-only preference (SPEC §12.1).
 */
@Entity(
    tableName = "workout",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_routine_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("source_routine_id"), Index("date")],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: LocalDate,
    @ColumnInfo(name = "start_time") val startTimeEpochMillis: Long? = null,
    @ColumnInfo(name = "end_time") val endTimeEpochMillis: Long? = null,
    @ColumnInfo(name = "bodyweight_kg") val bodyweightKg: Double? = null,
    val notes: String? = null,
    @ColumnInfo(name = "source_routine_id") val sourceRoutineId: Long? = null,
)

@Entity(
    tableName = "workout_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("workout_id"), Index("exercise_id")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "workout_id") val workoutId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    val position: Int,
    @ColumnInfo(name = "superset_group_id") val supersetGroupId: Long? = null,
)

/**
 * POLYMORPHIC set record (SPEC §5.1): a superset of nullable measurement
 * columns, gated by the parent exercise's ExerciseType via SetValidation.
 * The DB deliberately does not enforce the gating — the repository layer does —
 * so an ExerciseType edit never strands existing rows.
 */
@Entity(
    tableName = "set_entry",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workout_exercise_id")],
)
data class SetEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "workout_exercise_id") val workoutExerciseId: Long,
    val position: Int,
    @ColumnInfo(name = "set_kind") val setKind: SetKind = SetKind.WORKING,
    @ColumnInfo(name = "weight_kg") val weightKg: Double? = null,
    val reps: Int? = null,
    @ColumnInfo(name = "time_seconds") val timeSeconds: Int? = null,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double? = null,
    val kcal: Int? = null,
    val notes: String? = null,
    // Derived flag (recomputable from history); cached for list rendering.
    @ColumnInfo(name = "is_pr") val isPr: Boolean = false,
)
