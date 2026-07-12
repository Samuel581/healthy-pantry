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
}
