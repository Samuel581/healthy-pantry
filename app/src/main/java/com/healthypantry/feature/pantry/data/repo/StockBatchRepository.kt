package com.healthypantry.feature.pantry.data.repo

import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.feature.pantry.data.dao.StockBatchDao
import com.healthypantry.feature.pantry.domain.model.StockBatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Domain-facing CRUD access to [StockBatch]es (spec "Item and Stock Batch CRUD"). Wraps
 * [StockBatchDao] and never exposes Room entity types to callers.
 *
 * [observeTotalOnHand] re-derives its result from the DAO's live `SUM` query on every table
 * write, so a deleted batch is guaranteed to never be counted in a future stock calculation.
 */
interface StockBatchRepository {
    fun observeForFoodItem(foodItemId: Long): Flow<List<StockBatch>>
    fun observeTotalOnHand(foodItemId: Long): Flow<Double>

    /** Inserts a new batch ([StockBatch.id] == 0) or updates an existing one; returns its id. */
    suspend fun upsert(batch: StockBatch): Long
    suspend fun delete(batch: StockBatch)

    /**
     * Decrements [foodItemId]'s on-hand stock by [amount] (spec "Mark-eaten decrements actual"),
     * consuming the oldest batches first — FIFO, mirroring [StockBatchDao.observeForFoodItem]'s
     * `ORDER BY addedAt ASC`. A batch fully consumed is deleted rather than left at zero
     * quantity.
     *
     * Deliberate no-overdraft policy: if [amount] exceeds total on-hand, every batch is consumed
     * down to zero and the excess is silently dropped — the spec does not define overdraft
     * behavior, so this clamps rather than inventing a new error type; actual stock simply floors
     * at zero.
     */
    suspend fun decrementForFoodItem(foodItemId: Long, amount: Double)
}

class StockBatchRepositoryImpl @Inject constructor(
    private val stockBatchDao: StockBatchDao,
    private val dispatcherProvider: DispatcherProvider,
) : StockBatchRepository {

    override fun observeForFoodItem(foodItemId: Long): Flow<List<StockBatch>> =
        stockBatchDao.observeForFoodItem(foodItemId).map { entities -> entities.map { it.toDomain() } }

    override fun observeTotalOnHand(foodItemId: Long): Flow<Double> =
        stockBatchDao.observeTotalOnHand(foodItemId)

    override suspend fun upsert(batch: StockBatch): Long = withContext(dispatcherProvider.io) {
        if (batch.id == 0L) {
            stockBatchDao.insert(batch.toEntity())
        } else {
            stockBatchDao.update(batch.toEntity())
            batch.id
        }
    }

    override suspend fun delete(batch: StockBatch) = withContext(dispatcherProvider.io) {
        stockBatchDao.delete(batch.toEntity())
    }

    override suspend fun decrementForFoodItem(foodItemId: Long, amount: Double) = withContext(dispatcherProvider.io) {
        var remaining = amount
        val batches = stockBatchDao.getForFoodItem(foodItemId)

        for (batch in batches) {
            if (remaining <= 0.0) break

            if (batch.quantity <= remaining) {
                remaining -= batch.quantity
                stockBatchDao.delete(batch)
            } else {
                stockBatchDao.update(batch.copy(quantity = batch.quantity - remaining))
                remaining = 0.0
            }
        }
        // remaining > 0.0 here means amount exceeded total on-hand: every batch was already
        // consumed and the excess is silently dropped (no-overdraft policy, see interface KDoc).
    }
}
