package com.healthypantry.feature.pantry.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.healthypantry.feature.pantry.data.entity.StockBatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockBatchDao {

    @Insert
    suspend fun insert(batch: StockBatchEntity): Long

    @Update
    suspend fun update(batch: StockBatchEntity)

    @Delete
    suspend fun delete(batch: StockBatchEntity)

    @Query("SELECT * FROM stock_batch WHERE foodItemId = :foodItemId ORDER BY addedAt ASC")
    fun observeForFoodItem(foodItemId: Long): Flow<List<StockBatchEntity>>

    /**
     * One-shot (non-[Flow]) FIFO-ordered read of every batch for [foodItemId], used by
     * `StockBatchRepository.decrementForFoodItem` to consume the oldest stock first without
     * needing to collect a [Flow] inside a suspend function.
     */
    @Query("SELECT * FROM stock_batch WHERE foodItemId = :foodItemId ORDER BY addedAt ASC")
    suspend fun getForFoodItem(foodItemId: Long): List<StockBatchEntity>

    /**
     * Total on-hand quantity currently held for a food item, across all its (non-deleted)
     * batches. `COALESCE(..., 0.0)` guarantees a value even when no batches exist, and Room's
     * `Flow` re-runs this query — and thus re-excludes deleted rows — on every table write, so a
     * deleted batch is never counted (spec "Item and Stock Batch CRUD" — delete scenario).
     */
    @Query("SELECT COALESCE(SUM(quantity), 0.0) FROM stock_batch WHERE foodItemId = :foodItemId")
    fun observeTotalOnHand(foodItemId: Long): Flow<Double>
}
