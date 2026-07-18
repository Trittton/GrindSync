package dev.gatsyuk.soloranking.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import dev.gatsyuk.soloranking.core.database.entity.RoutineEntity
import dev.gatsyuk.soloranking.core.database.entity.RoutineExerciseEntity
import kotlinx.coroutines.flow.Flow

data class RoutineWithExercises(
    @Embedded val routine: RoutineEntity,
    @Relation(parentColumn = "id", entityColumn = "routine_id")
    val exercises: List<RoutineExerciseEntity>,
)

@Dao
interface RoutineDao {

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Insert
    suspend fun insertRoutineExercises(entries: List<RoutineExerciseEntity>)

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routine WHERE id = :id")
    suspend fun deleteRoutineById(id: Long)

    /** Editor save = replace the exercise list wholesale. */
    @Query("DELETE FROM routine_exercise WHERE routine_id = :routineId")
    suspend fun deleteRoutineExercisesFor(routineId: Long)

    @Transaction
    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun getRoutineWithExercises(id: Long): RoutineWithExercises?

    @Transaction
    @Query("SELECT * FROM routine ORDER BY display_order, name")
    fun observeRoutinesWithExercises(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routine ORDER BY display_order, name")
    suspend fun getRoutinesWithExercises(): List<RoutineWithExercises>
}
