package com.healthypantry.feature.pantry.ui.vm

import androidx.lifecycle.SavedStateHandle
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Spec: Item and Stock Batch CRUD, Unit Conversion Correctness, Projected vs Actual Stock
 * (openspec/changes/pantry-tracker/specs/pantry-stock/spec.md,
 * openspec/changes/pantry-tracker/specs/unit-conversion/spec.md).
 *
 * Hand-written fakes mirroring [PantryViewModelTest]'s convention (same
 * [FakeFoodItemRepository]/[FakeStockBatchRepository]/[FakePlanEntryRepository]/
 * [FakeRecipeRepository] shape, kept consistent so both test files could plausibly share fakes
 * later). [FakeUnitConversionRepository] additionally supports seeding real data here, since
 * unlike `PantryViewModel`'s row list, [ItemDetailViewModel.uiState] surfaces `conversions`
 * directly and that join needs to be provable.
 *
 * The projected-stock cases mirror [PantryViewModelTest]'s equivalent cases exactly, scoped down
 * to a single item's `uiState` instead of a list row - see [ItemDetailViewModel]'s own KDoc for
 * why the committed-quantity resolution is duplicated rather than shared.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcherProvider = object : DispatcherProvider {
        private val dispatcher = UnconfinedTestDispatcher()
        override val main get() = dispatcher
        override val io get() = dispatcher
        override val default get() = dispatcher
    }

    /** Pins [ItemDetailViewModel.addBatch]'s `Instant.now(clock)` call; unrelated to `weekRange`,
     * which `ItemDetailViewModel` derives from the real `LocalDate.now()` (same as
     * [PantryViewModelTest]'s plan-entry fixtures use). */
    private val fixedClock = Clock.fixed(Instant.parse("2026-07-12T00:00:00Z"), ZoneOffset.UTC)

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

    /** Always throws on [delete]; [observeById] returns [seedItem] (non-null) so
     * [ItemDetailViewModel.deleteItem]'s `uiState.value.foodItem?.let { ... }` guard actually
     * calls through to the repository instead of silently no-op-ing on a null `foodItem`. */
    private class FailingFoodItemRepository(private val seedItem: FoodItem) : FoodItemRepository {
        override fun observeAll(): Flow<List<FoodItem>> = MutableStateFlow(listOf(seedItem))
        override fun observeById(id: Long): Flow<FoodItem?> = MutableStateFlow(seedItem)
        override suspend fun upsert(item: FoodItem): Long =
            throw IllegalStateException("simulated repository failure")
        override suspend fun delete(item: FoodItem) {
            throw IllegalStateException("simulated repository failure")
        }
    }

    private class FakeStockBatchRepository : StockBatchRepository {
        private val totals = mutableMapOf<Long, MutableStateFlow<Double>>()
        private val batchLists = mutableMapOf<Long, MutableStateFlow<List<StockBatch>>>()
        private val batches = mutableListOf<StockBatch>()
        private var nextId = 1L

        override fun observeForFoodItem(foodItemId: Long): Flow<List<StockBatch>> =
            batchLists.getOrPut(foodItemId) { MutableStateFlow(emptyList()) }

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
            throw NotImplementedError("not used by ItemDetailViewModelTest")

        private fun recomputeTotal(foodItemId: Long) {
            val total = batches.filter { it.foodItemId == foodItemId }.sumOf { it.quantity }
            totals.getOrPut(foodItemId) { MutableStateFlow(0.0) }.value = total
            batchLists.getOrPut(foodItemId) { MutableStateFlow(emptyList()) }.value =
                batches.filter { it.foodItemId == foodItemId }
        }
    }

    /** Always throws on write, to prove [ItemDetailViewModel] doesn't crash on a repository
     * failure. */
    private class FailingStockBatchRepository : StockBatchRepository {
        override fun observeForFoodItem(foodItemId: Long): Flow<List<StockBatch>> = MutableStateFlow(emptyList())
        override fun observeTotalOnHand(foodItemId: Long): Flow<Double> = MutableStateFlow(0.0)
        override suspend fun upsert(batch: StockBatch): Long =
            throw IllegalStateException("simulated repository failure")
        override suspend fun delete(batch: StockBatch) {
            throw IllegalStateException("simulated repository failure")
        }
        override suspend fun decrementForFoodItem(foodItemId: Long, amount: Double): Unit =
            throw NotImplementedError("not used by ItemDetailViewModelTest")
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
            throw NotImplementedError("not used by ItemDetailViewModelTest")
    }

    /** No recipe-referencing scenario in this test file needs real data; every call is unused. */
    private class FakeRecipeRepository : RecipeRepository {
        override fun observeAll(): Flow<List<Recipe>> = MutableStateFlow(emptyList())
        override fun observeRecipeWithIngredients(id: Long): Flow<RecipeWithIngredients?> = MutableStateFlow(null)
        override suspend fun upsertRecipeWithIngredients(recipe: Recipe, ingredients: List<RecipeIngredient>): Long =
            throw NotImplementedError("not used by ItemDetailViewModelTest")
        override suspend fun delete(recipe: Recipe): Unit =
            throw NotImplementedError("not used by ItemDetailViewModelTest")
    }

    /** Unlike [PantryViewModelTest]'s always-empty version, this one supports [seed] so
     * `uiState.conversions` can actually be exercised for the join test. */
    private class FakeUnitConversionRepository : UnitConversionRepository {
        private val factorsByItem = mutableMapOf<Long, MutableStateFlow<List<ConversionFactor>>>()

        override fun observeForFoodItem(foodItemId: Long): Flow<List<ConversionFactor>> =
            factorsByItem.getOrPut(foodItemId) { MutableStateFlow(emptyList()) }

        override suspend fun upsert(foodItemId: Long, factor: ConversionFactor): Long =
            throw NotImplementedError("not used by ItemDetailViewModelTest")
        override suspend fun delete(foodItemId: Long, factor: ConversionFactor): Unit =
            throw NotImplementedError("not used by ItemDetailViewModelTest")

        /** Test-only seeding helper - bypasses [upsert] since no scenario here exercises
         * conversion CRUD itself, only that [ItemDetailViewModel.uiState] joins it in. */
        fun seed(foodItemId: Long, factors: List<ConversionFactor>) {
            factorsByItem.getOrPut(foodItemId) { MutableStateFlow(emptyList()) }.value = factors
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
        itemId: Long,
        foodItemRepository: FoodItemRepository,
        stockBatchRepository: StockBatchRepository,
        planEntryRepository: PlanEntryRepository = FakePlanEntryRepository(),
        recipeRepository: RecipeRepository = FakeRecipeRepository(),
        unitConversionRepository: UnitConversionRepository = FakeUnitConversionRepository(),
    ) = ItemDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(ItemDetailViewModel.ITEM_ID_ARG to itemId)),
        foodItemRepository = foodItemRepository,
        stockBatchRepository = stockBatchRepository,
        unitConversionRepository = unitConversionRepository,
        computeProjectedStockUseCase = ComputeProjectedStockUseCase(stockBatchRepository),
        planEntryRepository = planEntryRepository,
        recipeRepository = recipeRepository,
        computeWeeklyNeedsUseCase = ComputeWeeklyNeedsUseCase(UnitConverter()),
        clock = fixedClock,
        dispatcherProvider = testDispatcherProvider,
    )

    /** Subscribes to [ItemDetailViewModel.uiState] so its `WhileSubscribed` upstream starts
     * running. */
    private fun ItemDetailViewModel.startCollecting() {
        collectJob = uiState.onEach { }.launchIn(MainScope())
    }

    @Test
    fun `uiState for a nonexistent item shows foodItem null and default fields, without crashing`() = runTest {
        val viewModel = buildViewModel(
            itemId = 999L,
            foodItemRepository = FakeFoodItemRepository(),
            stockBatchRepository = FakeStockBatchRepository(),
        )
        viewModel.startCollecting()
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertNull(state.foodItem)
        assertTrue(state.batches.isEmpty())
        assertTrue(state.conversions.isEmpty())
        assertEquals(0.0, state.actualStock, 0.0001)
        assertEquals(0.0, state.projectedStock, 0.0001)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `uiState joins the food item with its batches, conversions and actual stock, projected equal to actual when nothing is planned`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val stockBatchRepository = FakeStockBatchRepository()
        val unitConversionRepository = FakeUnitConversionRepository()

        val id = foodItemRepository.upsert(chickenBreast())
        stockBatchRepository.upsert(StockBatch(foodItemId = id, quantity = 500.0, addedAt = Instant.EPOCH))
        val factor = ConversionFactor(fromUnit = MeasurementUnit.CUP, toUnit = MeasurementUnit.GRAM, factor = 185.0)
        unitConversionRepository.seed(id, listOf(factor))

        val viewModel = buildViewModel(id, foodItemRepository, stockBatchRepository, unitConversionRepository = unitConversionRepository)
        viewModel.startCollecting()
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertEquals("Chicken breast", state.foodItem?.name)
        assertEquals(1, state.batches.size)
        assertEquals(500.0, state.batches.first().quantity, 0.0001)
        assertEquals(listOf(factor), state.conversions)
        assertEquals(500.0, state.actualStock, 0.0001)
        // No plan entry for this item this week - committed quantity is zero, so projected == actual.
        assertEquals(500.0, state.projectedStock, 0.0001)
    }

    @Test
    fun `projectedStock subtracts a not-yet-eaten quick-add plan entry's quantity for this item`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val stockBatchRepository = FakeStockBatchRepository()
        val planEntryRepository = FakePlanEntryRepository()

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

        val viewModel = buildViewModel(id, foodItemRepository, stockBatchRepository, planEntryRepository = planEntryRepository)
        viewModel.startCollecting()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(500.0, state.actualStock, 0.0001)
        assertEquals(300.0, state.projectedStock, 0.0001)
    }

    @Test
    fun `addBatch persists a new batch that appears in uiState-batches and increases actualStock`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val stockBatchRepository = FakeStockBatchRepository()
        val id = foodItemRepository.upsert(chickenBreast())
        val viewModel = buildViewModel(id, foodItemRepository, stockBatchRepository)
        viewModel.startCollecting()
        advanceUntilIdle()
        assertEquals(0.0, viewModel.uiState.value.actualStock, 0.0001)

        viewModel.addBatch(quantity = 250.0, expiryDate = LocalDate.of(2026, 8, 1))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.batches.size)
        assertEquals(250.0, state.batches.first().quantity, 0.0001)
        assertEquals(LocalDate.of(2026, 8, 1), state.batches.first().expiryDate)
        assertEquals(fixedClock.instant(), state.batches.first().addedAt)
        assertEquals(250.0, state.actualStock, 0.0001)
    }

    @Test
    fun `deleteBatch removes it from uiState-batches and decreases actualStock`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val stockBatchRepository = FakeStockBatchRepository()
        val id = foodItemRepository.upsert(chickenBreast())
        val batchId = stockBatchRepository.upsert(StockBatch(foodItemId = id, quantity = 500.0, addedAt = Instant.EPOCH))
        val viewModel = buildViewModel(id, foodItemRepository, stockBatchRepository)
        viewModel.startCollecting()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.batches.size)

        viewModel.deleteBatch(StockBatch(id = batchId, foodItemId = id, quantity = 500.0, addedAt = Instant.EPOCH))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.batches.isEmpty())
        assertEquals(0.0, state.actualStock, 0.0001)
    }

    @Test
    fun `deleteItem calls through to the food item repository's delete`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val stockBatchRepository = FakeStockBatchRepository()
        val id = foodItemRepository.upsert(chickenBreast())
        val viewModel = buildViewModel(id, foodItemRepository, stockBatchRepository)
        viewModel.startCollecting()
        advanceUntilIdle()
        assertEquals("Chicken breast", viewModel.uiState.value.foodItem?.name)

        viewModel.deleteItem()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.foodItem)
        assertNull(foodItemRepository.observeById(id).first())
    }

    @Test
    fun `addBatch emits an errorEvent instead of crashing when the repository throws`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val id = foodItemRepository.upsert(chickenBreast())
        val viewModel = buildViewModel(id, foodItemRepository, FailingStockBatchRepository())
        viewModel.startCollecting()

        val errorDeferred = async { viewModel.errorEvent.first() }
        advanceUntilIdle()

        viewModel.addBatch(quantity = 100.0, expiryDate = null)
        advanceUntilIdle()

        assertEquals("simulated repository failure", errorDeferred.await())
    }

    @Test
    fun `deleteBatch emits an errorEvent instead of crashing when the repository throws`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val id = foodItemRepository.upsert(chickenBreast())
        val viewModel = buildViewModel(id, foodItemRepository, FailingStockBatchRepository())
        viewModel.startCollecting()

        val errorDeferred = async { viewModel.errorEvent.first() }
        advanceUntilIdle()

        viewModel.deleteBatch(StockBatch(id = 1L, foodItemId = id, quantity = 100.0, addedAt = Instant.EPOCH))
        advanceUntilIdle()

        assertEquals("simulated repository failure", errorDeferred.await())
    }

    @Test
    fun `deleteItem emits an errorEvent instead of crashing when the repository throws`() = runTest {
        val stockBatchRepository = FakeStockBatchRepository()
        val seedItem = chickenBreast(id = 1L)
        val viewModel = buildViewModel(1L, FailingFoodItemRepository(seedItem), stockBatchRepository)
        viewModel.startCollecting()

        val errorDeferred = async { viewModel.errorEvent.first() }
        advanceUntilIdle()

        viewModel.deleteItem()
        advanceUntilIdle()

        assertEquals("simulated repository failure", errorDeferred.await())
    }
}
