package com.healthypantry.feature.pantry.data.repo

import com.healthypantry.core.unit.ConversionFactor
import com.healthypantry.feature.pantry.data.dao.UnitConversionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Domain-facing read access to a [com.healthypantry.feature.pantry.domain.model.FoodItem]'s
 * registered [ConversionFactor]s (spec "Unit Conversion Correctness"). Wraps [UnitConversionDao]
 * and never exposes Room entity types to callers — needed by the planning-domain macro-rollup/
 * weekly-needs/mark-eaten use-cases, which must convert a `RecipeIngredient`'s quantity+unit into
 * its FoodItem's canonical unit using only that item's own registered factors.
 *
 * v1 only needs a read path here: no spec scenario yet exercises creating/deleting a
 * [ConversionFactor] through the domain layer, so [UnitConversionDao.insert]/[UnitConversionDao.delete]
 * remain unused above the DAO for now (out of scope for this PR).
 */
interface UnitConversionRepository {
    fun observeForFoodItem(foodItemId: Long): Flow<List<ConversionFactor>>
}

class UnitConversionRepositoryImpl @Inject constructor(
    private val unitConversionDao: UnitConversionDao,
) : UnitConversionRepository {

    override fun observeForFoodItem(foodItemId: Long): Flow<List<ConversionFactor>> =
        unitConversionDao.observeForFoodItem(foodItemId).map { entities ->
            entities.map { ConversionFactor(fromUnit = it.fromUnit, toUnit = it.toUnit, factor = it.factor) }
        }
}
