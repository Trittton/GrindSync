package dev.gatsyuk.grindsync.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import dev.gatsyuk.grindsync.core.database.entity.RoutineEntity
import dev.gatsyuk.grindsync.core.database.entity.RoutineExerciseEntity
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

    @Transaction
    @Query("SELECT * FROM routine ORDER BY display_order, name")
    fun observeRoutinesWithExercises(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routine ORDER BY display_order, name")
    suspend fun getRoutinesWithExercises(): List<RoutineWithExercises>
}
