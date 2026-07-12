package com.healthypantry.feature.pantry.domain.usecase

import com.healthypantry.feature.pantry.data.repo.StockBatchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Spec: Projected vs Actual Stock (openspec/changes/pantry-tracker/specs/pantry-stock/spec.md).
 *
 * Actual stock is already independently queryable via
 * [StockBatchRepository.observeTotalOnHand] (PR3) — this use-case builds on it rather than
 * duplicating it.
 *
 * `PlanEntry` (meal-planning) does not exist yet (PR7/PR8), so the committed/reserved quantity
 * per `FoodItem` is accepted as a caller-supplied input rather than sourced here. PR8's
 * mark-eaten use-case will compute that input from real `PlanEntry` rows once they exist; this
 * use-case owns only the subtraction/aggregation logic and its correctness.
 */
class ComputeProjectedStockUseCase @Inject constructor(
    private val stockBatchRepository: StockBatchRepository,
) {

    /** projected = actualStock - committedQuantity for a single [FoodItem][com.healthypantry.feature.pantry.domain.model.FoodItem]. */
    fun compute(actualStock: Double, committedQuantity: Double): Double =
        actualStock - committedQuantity

    /**
     * Observes projected stock for [foodItemId] by combining actual stock
     * ([StockBatchRepository.observeTotalOnHand]) with a caller-supplied
     * [committedQuantities] map (`FoodItemId -> committed quantity`). Items absent from the map
     * default to zero commitment, so projected stock equals actual stock.
     */
    fun observeProjected(foodItemId: Long, committedQuantities: Map<Long, Double>): Flow<Double> =
        stockBatchRepository.observeTotalOnHand(foodItemId).map { actualStock ->
            compute(actualStock, committedQuantities[foodItemId] ?: 0.0)
        }
}
