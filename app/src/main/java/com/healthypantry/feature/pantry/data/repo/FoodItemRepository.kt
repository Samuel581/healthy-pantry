package com.healthypantry.feature.pantry.data.repo

import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.feature.pantry.data.dao.FoodItemDao
import com.healthypantry.feature.pantry.domain.model.FoodItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Domain-facing CRUD access to [FoodItem]s (spec "Item and Stock Batch CRUD"). Wraps
 * [FoodItemDao] and never exposes Room entity types to callers.
 */
interface FoodItemRepository {
    fun observeAll(): Flow<List<FoodItem>>
    fun observeById(id: Long): Flow<FoodItem?>

    /** Inserts a new item ([FoodItem.id] == 0) or updates an existing one; returns its id. */
    suspend fun upsert(item: FoodItem): Long
    suspend fun delete(item: FoodItem)
}

class FoodItemRepositoryImpl @Inject constructor(
    private val foodItemDao: FoodItemDao,
    private val dispatcherProvider: DispatcherProvider,
) : FoodItemRepository {

    override fun observeAll(): Flow<List<FoodItem>> =
        foodItemDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeById(id: Long): Flow<FoodItem?> =
        foodItemDao.observeById(id).map { it?.toDomain() }

    override suspend fun upsert(item: FoodItem): Long = withContext(dispatcherProvider.io) {
        if (item.id == 0L) {
            foodItemDao.insert(item.toEntity())
        } else {
            foodItemDao.update(item.toEntity())
            item.id
        }
    }

    override suspend fun delete(item: FoodItem) = withContext(dispatcherProvider.io) {
        foodItemDao.delete(item.toEntity())
    }
}
