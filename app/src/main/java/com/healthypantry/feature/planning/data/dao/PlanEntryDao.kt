package com.healthypantry.feature.planning.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.healthypantry.feature.planning.data.entity.PlanEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanEntryDao {

    @Insert
    suspend fun insert(entry: PlanEntryEntity): Long

    @Update
    suspend fun update(entry: PlanEntryEntity)

    @Delete
    suspend fun delete(entry: PlanEntryEntity)

    /** All plan entries whose [PlanEntryEntity.dateEpochDay] falls within `[start, end]` (inclusive). */
    @Query("SELECT * FROM plan_entry WHERE dateEpochDay BETWEEN :start AND :end ORDER BY dateEpochDay ASC")
    fun observeWeek(start: Long, end: Long): Flow<List<PlanEntryEntity>>

    /**
     * Marks a plan entry eaten at [eatenAt] (caller-supplied epoch millis, not `System
     * .currentTimeMillis()`, so this stays deterministic and unit-testable). Moves the entry's
     * quantity out of projected deficit and into an actual-stock decrement (spec "Projected vs
     * Actual Stock").
     */
    @Query("UPDATE plan_entry SET eaten = 1, eatenAt = :eatenAt WHERE id = :id")
    suspend fun markEaten(id: Long, eatenAt: Long)
}
