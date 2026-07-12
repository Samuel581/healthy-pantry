package com.healthypantry.feature.pantry.domain.usecase

import com.healthypantry.feature.pantry.data.repo.StockBatchRepository
import com.healthypantry.feature.pantry.domain.model.StockBatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec: Projected vs Actual Stock
 * (openspec/changes/pantry-tracker/specs/pantry-stock/spec.md)
 *
 * `PlanEntry` does not exist yet (meal-planning lands in PR7/PR8), so this use-case accepts the
 * committed/reserved quantity for a `FoodItem` as a caller-supplied input rather than sourcing it
 * itself. PR8's mark-eaten use-case will be responsible for computing that input from real
 * `PlanEntry` rows once they exist; this test only proves the subtraction/aggregation logic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComputeProjectedStockUseCaseTest {

    /** Hand-written fake — only [observeTotalOnHand] is exercised by this test class. */
    private class FakeStockBatchRepository(totalOnHand: Double) : StockBatchRepository {
        private val totalOnHandFlow = MutableStateFlow(totalOnHand)
        override fun observeForFoodItem(foodItemId: Long): Flow<List<StockBatch>> =
            throw NotImplementedError("not used by ComputeProjectedStockUseCaseTest")
        override fun observeTotalOnHand(foodItemId: Long): Flow<Double> = totalOnHandFlow
        override suspend fun upsert(batch: StockBatch): Long =
            throw NotImplementedError("not used by ComputeProjectedStockUseCaseTest")
        override suspend fun delete(batch: StockBatch): Unit =
            throw NotImplementedError("not used by ComputeProjectedStockUseCaseTest")
    }

    private val useCase = ComputeProjectedStockUseCase(FakeStockBatchRepository(totalOnHand = 500.0))

    @Test
    fun `projected stock reflects an unconsumed plan`() {
        // Given "Chicken breast" has 500g actual stock and a commitment of 200g not yet eaten
        // When the user views projected stock
        val projected = useCase.compute(actualStock = 500.0, committedQuantity = 200.0)

        // Then projected stock is 300g
        assertEquals(300.0, projected, 0.0001)
    }

    @Test
    fun `projected stock matches actual stock once the entry no longer commits any quantity`() {
        // Given the Tuesday PlanEntry above is marked eaten elsewhere (PR8), so it no longer
        // commits any quantity against "Chicken breast" (actual stock already reflects the
        // deduction there — this use-case only re-derives projected from whatever committed
        // quantity the caller passes in)
        // When the user views projected stock with no outstanding commitment
        val projected = useCase.compute(actualStock = 300.0, committedQuantity = 0.0)

        // Then projected stock recalculates to match actual stock
        assertEquals(300.0, projected, 0.0001)
    }

    @Test
    fun `observeProjected combines actual stock from StockBatchRepository with the committed map`() = runTest {
        // Given "Chicken breast" (foodItemId 1L) has 500g actual stock (StockBatchRepository,
        // already independently queryable per PR3) and a commitment of 200g not yet eaten
        val repository = FakeStockBatchRepository(totalOnHand = 500.0)
        val useCaseUnderTest = ComputeProjectedStockUseCase(repository)

        // When the user views projected stock
        val projected = useCaseUnderTest.observeProjected(
            foodItemId = 1L,
            committedQuantities = mapOf(1L to 200.0),
        ).first()

        // Then projected stock for "Chicken breast" is 300g
        assertEquals(300.0, projected, 0.0001)
    }

    @Test
    fun `observeProjected defaults to actual stock when the item has no entry in the committed map`() = runTest {
        // Given "Chicken breast" (foodItemId 1L) has 300g actual stock and no commitment at all
        // (e.g. the Tuesday PlanEntry above was marked eaten and dropped from the caller's map)
        val repository = FakeStockBatchRepository(totalOnHand = 300.0)
        val useCaseUnderTest = ComputeProjectedStockUseCase(repository)

        // When the user views projected stock
        val projected = useCaseUnderTest.observeProjected(
            foodItemId = 1L,
            committedQuantities = emptyMap(),
        ).first()

        // Then projected stock recalculates to match actual stock
        assertEquals(300.0, projected, 0.0001)
    }
}
