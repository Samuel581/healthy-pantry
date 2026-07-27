package com.healthypantry.feature.pantry.data.repo

import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.unit.ConversionFactor
import com.healthypantry.feature.pantry.data.dao.UnitConversionDao
import com.healthypantry.feature.pantry.data.entity.UnitConversionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Domain-facing read/write access to a [com.healthypantry.feature.pantry.domain.model.FoodItem]'s
 * registered [ConversionFactor]s (spec "Unit Conversion Correctness"). Wraps [UnitConversionDao]
 * and never exposes Room entity types to callers — needed by the planning-domain macro-rollup/
 * weekly-needs/mark-eaten use-cases, which must convert a `RecipeIngredient`'s quantity+unit into
 * its FoodItem's canonical unit using only that item's own registered factors, and by the item
 * create/edit form (PR2 follow-up) to persist the conversions a user registers for an item.
 */
interface UnitConversionRepository {
    fun observeForFoodItem(foodItemId: Long): Flow<List<ConversionFactor>>

    /**
     * Persists a new [factor] registered for [foodItemId]; returns the new row's id.
     *
     * Unlike [StockBatchRepository.upsert]/`FoodItemRepository.upsert`, this never updates an
     * existing row: [ConversionFactor] is a plain value object with no id of its own (it's never
     * looked up by id, only ever compared by its `fromUnit`/`toUnit`/`factor` fields), so there is
     * no "existing row" a caller could address for an update — every call inserts a fresh row.
     */
    suspend fun upsert(foodItemId: Long, factor: ConversionFactor): Long

    /**
     * Deletes the persisted row for [foodItemId] whose fields match [factor], if any. A silent
     * no-op if no such row exists (e.g. it was already deleted), mirroring
     * [StockBatchRepository]'s no-overdraft "silently drop" style rather than throwing.
     */
    suspend fun delete(foodItemId: Long, factor: ConversionFactor)
}

class UnitConversionRepositoryImpl @Inject constructor(
    private val unitConversionDao: UnitConversionDao,
    private val dispatcherProvider: DispatcherProvider,
) : UnitConversionRepository {

    override fun observeForFoodItem(foodItemId: Long): Flow<List<ConversionFactor>> =
        unitConversionDao.observeForFoodItem(foodItemId).map { entities ->
            entities.map { ConversionFactor(fromUnit = it.fromUnit, toUnit = it.toUnit, factor = it.factor) }
        }

    override suspend fun upsert(foodItemId: Long, factor: ConversionFactor): Long =
        withContext(dispatcherProvider.io) {
            unitConversionDao.insert(
                UnitConversionEntity(
                    foodItemId = foodItemId,
                    fromUnit = factor.fromUnit,
                    toUnit = factor.toUnit,
                    factor = factor.factor,
                ),
            )
        }

    override suspend fun delete(foodItemId: Long, factor: ConversionFactor) = withContext(dispatcherProvider.io) {
        val match = unitConversionDao.getForFoodItem(foodItemId).firstOrNull {
            it.fromUnit == factor.fromUnit && it.toUnit == factor.toUnit && it.factor == factor.factor
        }
        if (match != null) {
            unitConversionDao.delete(match)
        }
    }
}
