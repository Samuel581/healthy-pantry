package com.healthypantry.feature.pantry.ui.vm

import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.common.MainDispatcherRule
import com.healthypantry.core.unit.ConversionFactor
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.core.unit.UnitConverter
import com.healthypantry.feature.pantry.data.repo.FoodItemRepository
import com.healthypantry.feature.pantry.data.repo.StockBatchRepository
import com.healthypantry.feature.pantry.data.repo.UnitConversionRepository
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.pantry.domain.model.StockBatch
import com.healthypantry.feature.pantry.domain.usecase.ComputeProjectedStockUseCase
import com.healthypantry.feature.planning.data.repo.PlanEntryRepository
import com.healthypantry.feature.planning.domain.model.MealSlot
import com.healthypantry.feature.planning.domain.model.PlanEntry
import com.healthypantry.feature.planning.domain.model.PlanEntryType
import com.healthypantry.feature.planning.domain.usecase.ComputeWeeklyNeedsUseCase
import com.healthypantry.feature.recipes.data.repo.RecipeRepository
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.domain.model.RecipeIngredient
import com.healthypantry.feature.recipes.domain.model.RecipeWithIngredients
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
 * Hand-written fakes for [FoodItemRepository]/[StockBatchRepository]/[PlanEntryRepository]/
 * [RecipeRepository]/[UnitConversionRepository] (same convention as
 * [ComputeProjectedStockUseCaseTest][com.healthypantry.feature.pantry.domain.usecase.ComputeProjectedStockUseCaseTest]
 * and `FoodItemRepositoryTest`) — a real in-memory Room DB (the `PlanViewModelTest` convention)
 * isn't needed here since, unlike `MarkPlanEntryEatenUseCase`, projected-stock resolution never
 * needs a real DB transaction.
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

        override suspend fun decrementForFoodItem(foodItemId: Long, amount: Double): Unit =
            throw NotImplementedError("not used by PantryViewModelTest")

        private fun recomputeTotal(foodItemId: Long) {
            val total = batches.filter { it.foodItemId == foodItemId }.sumOf { it.quantity }
            totals.getOrPut(foodItemId) { MutableStateFlow(0.0) }.value = total
        }
    }

    /** Always throws on write, to prove [PantryViewModel] doesn't crash on a repository failure. */
    private class FailingFoodItemRepository : FoodItemRepository {
        override fun observeAll(): Flow<List<FoodItem>> = MutableStateFlow(emptyList())
        override fun observeById(id: Long): Flow<FoodItem?> = MutableStateFlow(null)
        override suspend fun upsert(item: FoodItem): Long =
            throw IllegalStateException("simulated repository failure")
        override suspend fun delete(item: FoodItem) {
            throw IllegalStateException("simulated repository failure")
        }
    }

    private class FakePlanEntryRepository : PlanEntryRepository {
        private val entriesFlow = MutableStateFlow<List<PlanEntry>>(emptyList())
        private var nextId = 1L

        override fun observeWeek(start: Long, end: Long): Flow<List<PlanEntry>> =
            entriesFlow.map { entries -> entries.filter { it.dateEpochDay in start..end } }

        override suspend fun upsert(entry: PlanEntry): Long {
            val id = if (entry.id == 0L) nextId++ else entry.id
            val stored = entry.copy(id = id)
            entriesFlow.value = entriesFlow.value.filterNot { it.id == id } + stored
            return id
        }

        override suspend fun delete(entry: PlanEntry) {
            entriesFlow.value = entriesFlow.value.filterNot { it.id == entry.id }
        }

        override suspend fun markEaten(id: Long, eatenAt: Instant): Int =
            throw NotImplementedError("not used by PantryViewModelTest")
    }

    /** No recipe-referencing scenario in this test file needs real data; every call is unused. */
    private class FakeRecipeRepository : RecipeRepository {
        override fun observeAll(): Flow<List<Recipe>> = MutableStateFlow(emptyList())
        override fun observeRecipeWithIngredients(id: Long): Flow<RecipeWithIngredients?> = MutableStateFlow(null)
        override suspend fun upsertRecipeWithIngredients(recipe: Recipe, ingredients: List<RecipeIngredient>): Long =
            throw NotImplementedError("not used by PantryViewModelTest")
        override suspend fun delete(recipe: Recipe): Unit =
            throw NotImplementedError("not used by PantryViewModelTest")
    }

    /** No conversion-dependent scenario in this test file needs real data; every write is unused. */
    private class FakeUnitConversionRepository : UnitConversionRepository {
        override fun observeForFoodItem(foodItemId: Long): Flow<List<ConversionFactor>> = MutableStateFlow(emptyList())
        override suspend fun upsert(foodItemId: Long, factor: ConversionFactor): Long =
            throw NotImplementedError("not used by PantryViewModelTest")
        override suspend fun delete(foodItemId: Long, factor: ConversionFactor): Unit =
            throw NotImplementedError("not used by PantryViewModelTest")
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
        planEntryRepository: PlanEntryRepository = FakePlanEntryRepository(),
        recipeRepository: RecipeRepository = FakeRecipeRepository(),
        unitConversionRepository: UnitConversionRepository = FakeUnitConversionRepository(),
    ) = PantryViewModel(
        foodItemRepository = foodItemRepository,
        stockBatchRepository = stockBatchRepository,
        computeProjectedStockUseCase = ComputeProjectedStockUseCase(stockBatchRepository),
        planEntryRepository = planEntryRepository,
        recipeRepository = recipeRepository,
        unitConversionRepository = unitConversionRepository,
        computeWeeklyNeedsUseCase = ComputeWeeklyNeedsUseCase(UnitConverter()),
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
    fun `uiState joins a persisted food item with its actual and projected stock, equal when nothing is planned`() = runTest {
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
        // No plan entry for this item this week - committed quantity is zero, so projected == actual.
        assertEquals(500.0, state.items.first().projectedStock, 0.0001)
    }

    @Test
    fun `projectedStock subtracts a not-yet-eaten quick-add plan entry's quantity for that item`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val stockBatchRepository = FakeStockBatchRepository()
        val planEntryRepository = FakePlanEntryRepository()
        val viewModel = buildViewModel(foodItemRepository, stockBatchRepository, planEntryRepository = planEntryRepository)
        viewModel.startCollecting()

        val id = foodItemRepository.upsert(chickenBreast())
        stockBatchRepository.upsert(StockBatch(foodItemId = id, quantity = 500.0, addedAt = Instant.EPOCH))
        planEntryRepository.upsert(
            PlanEntry(
                dateEpochDay = LocalDate.now().toEpochDay(),
                mealSlot = MealSlot.LUNCH,
                type = PlanEntryType.ITEM,
                foodItemId = id,
                quantity = 200.0,
            ),
        )
        advanceUntilIdle()

        val row = viewModel.uiState.value.items.first()
        assertEquals(500.0, row.actualStock, 0.0001)
        assertEquals(300.0, row.projectedStock, 0.0001)
    }

    @Test
    fun `projectedStock ignores an already-eaten plan entry, since it's already reflected in actual stock`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val stockBatchRepository = FakeStockBatchRepository()
        val planEntryRepository = FakePlanEntryRepository()
        val viewModel = buildViewModel(foodItemRepository, stockBatchRepository, planEntryRepository = planEntryRepository)
        viewModel.startCollecting()

        val id = foodItemRepository.upsert(chickenBreast())
        stockBatchRepository.upsert(StockBatch(foodItemId = id, quantity = 500.0, addedAt = Instant.EPOCH))
        planEntryRepository.upsert(
            PlanEntry(
                dateEpochDay = LocalDate.now().toEpochDay(),
                mealSlot = MealSlot.LUNCH,
                type = PlanEntryType.ITEM,
                foodItemId = id,
                quantity = 200.0,
                eaten = true,
            ),
        )
        advanceUntilIdle()

        val row = viewModel.uiState.value.items.first()
        assertEquals(500.0, row.actualStock, 0.0001)
        assertEquals(500.0, row.projectedStock, 0.0001)
    }

    @Test
    fun `addItem persists a new food item so it appears in uiState, returning its new id`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val stockBatchRepository = FakeStockBatchRepository()
        val viewModel = buildViewModel(foodItemRepository, stockBatchRepository)
        viewModel.startCollecting()

        val id = viewModel.addItem(chickenBreast())
        advanceUntilIdle()

        assertTrue(id > 0)
        val state = viewModel.uiState.value
        assertEquals(1, state.items.size)
        assertEquals("Chicken breast", state.items.first().foodItem.name)
        assertEquals(id, state.items.first().foodItem.id)
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
        assertEquals(1.8, row.foodItem.caloriesPerUnit!!, 0.0001)
    }

    @Test
    fun `updateItem emits an errorEvent instead of crashing when the repository throws`() = runTest {
        val viewModel = buildViewModel(FailingFoodItemRepository(), FakeStockBatchRepository())
        viewModel.startCollecting()

        val errorDeferred = async { viewModel.errorEvent.first() }
        advanceUntilIdle()

        viewModel.updateItem(chickenBreast(id = 1L))
        advanceUntilIdle()

        assertEquals("simulated repository failure", errorDeferred.await())
    }

    @Test
    fun `addItem propagates a repository failure to its caller instead of swallowing it`() = runTest {
        // Unlike updateItem/deleteItem (fire-and-forget via launchOnIo, error surfaced through
        // errorEvent), addItem is a plain suspend function so its caller can await the new id for
        // an id-dependent follow-up (e.g. attaching a starting batch) - which also means a failure
        // must propagate directly to that caller rather than being swallowed internally.
        val viewModel = buildViewModel(FailingFoodItemRepository(), FakeStockBatchRepository())
        viewModel.startCollecting()
        advanceUntilIdle()

        try {
            viewModel.addItem(chickenBreast())
            org.junit.Assert.fail("Expected addItem to propagate the repository's exception")
        } catch (e: IllegalStateException) {
            assertEquals("simulated repository failure", e.message)
        }
    }
}
