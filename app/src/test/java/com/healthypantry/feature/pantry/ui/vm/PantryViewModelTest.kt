package com.healthypantry.feature.pantry.ui.vm

import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.common.MainDispatcherRule
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.data.repo.FoodItemRepository
import com.healthypantry.feature.pantry.data.repo.StockBatchRepository
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.pantry.domain.model.StockBatch
import com.healthypantry.feature.pantry.domain.usecase.ComputeProjectedStockUseCase
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Spec: Item and Stock Batch CRUD, Projected vs Actual Stock
 * (openspec/changes/pantry-tracker/specs/pantry-stock/spec.md)
 *
 * Hand-written fakes for [FoodItemRepository]/[StockBatchRepository] (same convention as
 * [ComputeProjectedStockUseCaseTest][com.healthypantry.feature.pantry.domain.usecase.ComputeProjectedStockUseCaseTest]
 * and `FoodItemRepositoryTest`). `PlanEntry` doesn't exist yet (PR7/PR8), so every scenario here
 * expects projected stock to equal actual stock (zero committed quantity).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PantryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcherProvider = object : DispatcherProvider {
        private val dispatcher = UnconfinedTestDispatcher()
        override val main get() = dispatcher
        override val io get() = dispatcher
        override val default get() = dispatcher
    }

    private var collectJob: Job? = null

    @After
    fun tearDown() {
        collectJob?.cancel()
    }

    private class FakeFoodItemRepository : FoodItemRepository {
        private val itemsFlow = MutableStateFlow<List<FoodItem>>(emptyList())
        private var nextId = 1L

        override fun observeAll(): Flow<List<FoodItem>> = itemsFlow
        override fun observeById(id: Long): Flow<FoodItem?> =
            itemsFlow.map { items -> items.find { it.id == id } }

        override suspend fun upsert(item: FoodItem): Long {
            val id = if (item.id == 0L) nextId++ else item.id
            val stored = item.copy(id = id)
            itemsFlow.value = itemsFlow.value.filterNot { it.id == id } + stored
            return id
        }

        override suspend fun delete(item: FoodItem) {
            itemsFlow.value = itemsFlow.value.filterNot { it.id == item.id }
        }
    }

    private class FakeStockBatchRepository : StockBatchRepository {
        private val totals = mutableMapOf<Long, MutableStateFlow<Double>>()
        private val batches = mutableListOf<StockBatch>()
        private var nextId = 1L

        override fun observeForFoodItem(foodItemId: Long): Flow<List<StockBatch>> =
            throw NotImplementedError("not used by PantryViewModelTest")

        override fun observeTotalOnHand(foodItemId: Long): Flow<Double> =
            totals.getOrPut(foodItemId) { MutableStateFlow(0.0) }

        override suspend fun upsert(batch: StockBatch): Long {
            val id = if (batch.id == 0L) nextId++ else batch.id
            val stored = batch.copy(id = id)
            batches.removeAll { it.id == id }
            batches += stored
            recomputeTotal(stored.foodItemId)
            return id
        }

        override suspend fun delete(batch: StockBatch) {
            batches.removeAll { it.id == batch.id }
            recomputeTotal(batch.foodItemId)
        }

        private fun recomputeTotal(foodItemId: Long) {
            val total = batches.filter { it.foodItemId == foodItemId }.sumOf { it.quantity }
            totals.getOrPut(foodItemId) { MutableStateFlow(0.0) }.value = total
        }
    }

    private fun chickenBreast(id: Long = 0L) = FoodItem(
        id = id,
        name = "Chicken breast",
        canonicalUnit = MeasurementUnit.GRAM,
        source = FoodItemSource.MANUAL,
        caloriesPerUnit = 1.65,
        proteinGramsPerUnit = 0.31,
        carbsGramsPerUnit = 0.0,
        fatGramsPerUnit = 0.036,
    )

    private fun buildViewModel(
        foodItemRepository: FoodItemRepository,
        stockBatchRepository: StockBatchRepository,
    ) = PantryViewModel(
        foodItemRepository = foodItemRepository,
        stockBatchRepository = stockBatchRepository,
        computeProjectedStockUseCase = ComputeProjectedStockUseCase(stockBatchRepository),
        dispatcherProvider = testDispatcherProvider,
    )

    /** Subscribes to [PantryViewModel.uiState] so its `WhileSubscribed` upstream starts running. */
    private fun PantryViewModel.startCollecting() {
        collectJob = uiState.onEach { }.launchIn(MainScope())
    }

    @Test
    fun `uiState starts with isLoading true and no items before any food item is persisted`() = runTest {
        val viewModel = buildViewModel(FakeFoodItemRepository(), FakeStockBatchRepository())
        viewModel.startCollecting()
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertTrue(state.items.isEmpty())
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `uiState joins a persisted food item with its actual and projected stock`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val stockBatchRepository = FakeStockBatchRepository()
        val viewModel = buildViewModel(foodItemRepository, stockBatchRepository)
        viewModel.startCollecting()

        val id = foodItemRepository.upsert(chickenBreast())
        stockBatchRepository.upsert(StockBatch(foodItemId = id, quantity = 500.0, addedAt = Instant.EPOCH))
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertEquals(1, state.items.size)
        assertEquals("Chicken breast", state.items.first().foodItem.name)
        assertEquals(500.0, state.items.first().actualStock, 0.0001)
        assertEquals(500.0, state.items.first().projectedStock, 0.0001)
    }

    @Test
    fun `addItem persists a new food item so it appears in uiState`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val stockBatchRepository = FakeStockBatchRepository()
        val viewModel = buildViewModel(foodItemRepository, stockBatchRepository)
        viewModel.startCollecting()

        viewModel.addItem(chickenBreast())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.items.size)
        assertEquals("Chicken breast", state.items.first().foodItem.name)
    }

    @Test
    fun `deleteItem removes the food item from uiState`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val stockBatchRepository = FakeStockBatchRepository()
        val viewModel = buildViewModel(foodItemRepository, stockBatchRepository)
        viewModel.startCollecting()

        val id = foodItemRepository.upsert(chickenBreast())
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.items.size)

        viewModel.deleteItem(chickenBreast(id = id))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun `addStockBatch increases actual and projected stock for the affected item`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val stockBatchRepository = FakeStockBatchRepository()
        val viewModel = buildViewModel(foodItemRepository, stockBatchRepository)
        viewModel.startCollecting()

        val id = foodItemRepository.upsert(chickenBreast())
        advanceUntilIdle()
        assertEquals(0.0, viewModel.uiState.value.items.first().actualStock, 0.0001)

        viewModel.addStockBatch(StockBatch(foodItemId = id, quantity = 250.0, addedAt = Instant.EPOCH))
        advanceUntilIdle()

        val row = viewModel.uiState.value.items.first()
        assertEquals(250.0, row.actualStock, 0.0001)
        assertEquals(250.0, row.projectedStock, 0.0001)
    }

    @Test
    fun `updateItem changes an existing item's fields as reflected in uiState`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val stockBatchRepository = FakeStockBatchRepository()
        val viewModel = buildViewModel(foodItemRepository, stockBatchRepository)
        viewModel.startCollecting()

        val id = foodItemRepository.upsert(chickenBreast())
        advanceUntilIdle()

        viewModel.updateItem(chickenBreast(id = id).copy(name = "Grilled chicken breast", caloriesPerUnit = 1.8))
        advanceUntilIdle()

        val row = viewModel.uiState.value.items.first()
        assertEquals("Grilled chicken breast", row.foodItem.name)
        assertEquals(1.8, row.foodItem.caloriesPerUnit, 0.0001)
    }
}
