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
     *
     * The `AND eaten = 0` clause is the atomic double-tap/race guard: SQLite/Room serializes
     * writers, so of two concurrent calls for the same [id] (e.g. a UI double-tap on "Mark eaten"
     * before the first write's result has propagated back to the caller's in-memory snapshot),
     * only one can ever flip `eaten` 0->1 and affect a row — the other always affects zero rows,
     * regardless of what stale `eaten` value each caller started from.
     *
     * @return the number of rows affected: 1 if this call transitioned the entry from not-eaten to
     * eaten, 0 if it was already eaten (by this call or a concurrent one). Callers MUST only
     * perform the corresponding stock decrement when this returns 1.
     */
    @Query("UPDATE plan_entry SET eaten = 1, eatenAt = :eatenAt WHERE id = :id AND eaten = 0")
    suspend fun markEaten(id: Long, eatenAt: Long): Int
}
