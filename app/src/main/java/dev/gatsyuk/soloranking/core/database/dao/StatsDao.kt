package dev.gatsyuk.soloranking.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import dev.gatsyuk.soloranking.core.database.entity.SetEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** A set with the date of the (completed) workout it belongs to. */
data class SetForExercise(
    @Embedded val set: SetEntryEntity,
    @ColumnInfo(name = "workout_date") val workoutDate: LocalDate,
)

/** Catalog entry that has at least one logged set — the drilldown list. */
data class ExerciseHistorySummary(
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    @ColumnInfo(name = "exercise_name") val exerciseName: String,
    @ColumnInfo(name = "set_count") val setCount: Int,
    @ColumnInfo(name = "last_date") val lastDate: LocalDate,
)

@Dao
interface StatsDao {

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT s.*, w.date AS workout_date FROM set_entry s
        JOIN workout_exercise we ON s.workout_exercise_id = we.id
        JOIN workout w ON we.workout_id = w.id
        WHERE we.exercise_id = :exerciseId AND w.end_time IS NOT NULL
        ORDER BY w.date, w.id, s.position
        """,
    )
    fun observeSetsForExercise(exerciseId: Long): Flow<List<SetForExercise>>

    @Query(
        """
        SELECT e.id AS exercise_id, e.name AS exercise_name,
               COUNT(s.id) AS set_count, MAX(w.date) AS last_date
        FROM exercise e
        JOIN workout_exercise we ON we.exercise_id = e.id
        JOIN workout w ON we.workout_id = w.id AND w.end_time IS NOT NULL
        JOIN set_entry s ON s.workout_exercise_id = we.id
        GROUP BY e.id, e.name
        ORDER BY last_date DESC, exercise_name
        """,
    )
    fun observeExercisesWithHistory(): Flow<List<ExerciseHistorySummary>>
}
