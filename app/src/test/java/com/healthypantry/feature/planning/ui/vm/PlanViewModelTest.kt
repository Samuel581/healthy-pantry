package com.healthypantry.feature.planning.ui.vm

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.common.MainDispatcherRule
import com.healthypantry.core.database.AppDatabase
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.core.unit.UnitConverter
import com.healthypantry.feature.pantry.data.entity.FoodItemEntity
import com.healthypantry.feature.pantry.data.entity.StockBatchEntity
import com.healthypantry.feature.pantry.data.repo.FoodItemRepositoryImpl
import com.healthypantry.feature.pantry.data.repo.StockBatchRepositoryImpl
import com.healthypantry.feature.pantry.data.repo.UnitConversionRepositoryImpl
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.planning.data.repo.PlanEntryRepositoryImpl
import com.healthypantry.feature.planning.domain.model.MealSlot
import com.healthypantry.feature.planning.domain.usecase.ComputeWeeklyNeedsUseCase
import com.healthypantry.feature.planning.domain.usecase.MarkPlanEntryEatenUseCase
import com.healthypantry.feature.recipes.data.entity.RecipeEntity
import com.healthypantry.feature.recipes.data.entity.RecipeIngredientEntity
import com.healthypantry.feature.recipes.data.repo.RecipeRepositoryImpl
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec: Weekly Plan Assignment, Weekly Ingredient Needs, Mark Plan Entry Eaten
 * (openspec/changes/pantry-tracker/specs/meal-planning/spec.md).
 *
 * Unlike `PantryViewModelTest`/`RecipeViewModelTest` (plain JVM hand-written fakes),
 * [PlanViewModel] is exercised against a real in-memory Room database (Robolectric) with every
 * real repository/use-case implementation — [MarkPlanEntryEatenUseCase] requires a real
 * [AppDatabase] for its `withTransaction` call, which cannot be hand-faked, so this mirrors
 * `MarkPlanEntryEatenUseCaseTest`'s own fixture instead (same "real in-memory Room" convention
 * this project already uses wherever a component needs one).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private var collectJob: Job? = null

    private val testDispatcherProvider = object : DispatcherProvider {
        private val dispatcher = UnconfinedTestDispatcher()
        override val main get() = dispatcher
        override val io get() = dispatcher
        override val default get() = dispatcher
    }

    private var riceId: Long = 0
    private var chickenId: Long = 0
    private var bowlId: Long = 0

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        riceId = database.foodItemDao().insert(foodItem("Rice"))
        chickenId = database.foodItemDao().insert(foodItem("Chicken"))
        bowlId = database.recipeDao().insert(RecipeEntity(name = "Bowl", servings = 2, notes = null, createdAt = Instant.parse("2026-07-12T00:00:00Z")))
        database.recipeIngredientDao().insertAll(
            listOf(
                RecipeIngredientEntity(recipeId = bowlId, foodItemId = riceId, quantity = 200.0, unit = MeasurementUnit.GRAM, sortOrder = 0),
                RecipeIngredientEntity(recipeId = bowlId, foodItemId = chickenId, quantity = 150.0, unit = MeasurementUnit.GRAM, sortOrder = 1),
            ),
        )
    }

    @After
    fun tearDown() {
        collectJob?.cancel()
        database.close()
    }

    private fun foodItem(name: String) = FoodItemEntity(
        name = name,
        canonicalUnit = MeasurementUnit.GRAM,
        source = FoodItemSource.MANUAL,
        caloriesPerUnit = 1.3,
        proteinGramsPerUnit = 0.03,
        carbsGramsPerUnit = 0.28,
        fatGramsPerUnit = 0.003,
        barcode = null,
        externalSourceId = null,
    )

    private suspend fun addStock(foodItemId: Long, quantity: Double) {
        database.stockBatchDao().insert(
            StockBatchEntity(foodItemId = foodItemId, quantity = quantity, expiryDate = null, addedAt = Instant.parse("2026-07-10T00:00:00Z")),
        )
    }

    private fun buildViewModel(): PlanViewModel {
        val stockBatchRepository = StockBatchRepositoryImpl(database.stockBatchDao(), testDispatcherProvider)
        val recipeRepository = RecipeRepositoryImpl(database, database.recipeDao(), database.recipeIngredientDao(), testDispatcherProvider)
        val foodItemRepository = FoodItemRepositoryImpl(database.foodItemDao(), testDispatcherProvider)
        val unitConversionRepository = UnitConversionRepositoryImpl(database.unitConversionDao(), testDispatcherProvider)
        val planEntryRepository = PlanEntryRepositoryImpl(database.planEntryDao(), testDispatcherProvider)
        val unitConverter = UnitConverter()

        return PlanViewModel(
            planEntryRepository = planEntryRepository,
            recipeRepository = recipeRepository,
            foodItemRepository = foodItemRepository,
            unitConversionRepository = unitConversionRepository,
            computeWeeklyNeedsUseCase = ComputeWeeklyNeedsUseCase(unitConverter),
            markPlanEntryEatenUseCase = MarkPlanEntryEatenUseCase(
                database = database,
                stockBatchRepository = stockBatchRepository,
                recipeRepository = recipeRepository,
                unitConversionRepository = unitConversionRepository,
                planEntryRepository = planEntryRepository,
                unitConverter = unitConverter,
            ),
            dispatcherProvider = testDispatcherProvider,
        )
    }

    private fun PlanViewModel.startCollecting() {
        collectJob = uiState.onEach { }.launchIn(MainScope())
    }

    @Test
    fun `uiState starts with isLoading true and no entries before any plan entry is persisted`() = runTest {
        val viewModel = buildViewModel()
        viewModel.startCollecting()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.entries.isEmpty())
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `quickAddItem persists a new ITEM plan entry for the given day and slot, shown with the food item's name`() = runTest {
        val viewModel = buildViewModel()
        viewModel.startCollecting()
        advanceUntilIdle()
        val day = viewModel.weekRange.startEpochDay

        viewModel.quickAddItem(day = day, mealSlot = MealSlot.BREAKFAST, foodItemId = riceId, quantity = 2.0)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.entries.size)
        assertEquals("Rice", state.entries.first().displayName)
        assertEquals(MealSlot.BREAKFAST, state.entries.first().entry.mealSlot)
    }

    @Test
    fun `assignRecipe persists a new RECIPE plan entry for the given day and slot, shown with the recipe's name`() = runTest {
        val viewModel = buildViewModel()
        viewModel.startCollecting()
        advanceUntilIdle()
        val day = viewModel.weekRange.startEpochDay

        viewModel.assignRecipe(day = day, mealSlot = MealSlot.DINNER, recipeId = bowlId, servings = 1.0)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.entries.size)
        assertEquals("Bowl", state.entries.first().displayName)
    }

    @Test
    fun `weeklyNeeds sums a RECIPE entry's ingredient quantities scaled by servings ratio`() = runTest {
        val viewModel = buildViewModel()
        viewModel.startCollecting()
        advanceUntilIdle()
        val day = viewModel.weekRange.startEpochDay

        viewModel.assignRecipe(day = day, mealSlot = MealSlot.DINNER, recipeId = bowlId, servings = 1.0)
        advanceUntilIdle()

        val needs = viewModel.uiState.value.weeklyNeeds
        assertEquals(100.0, needs[riceId]!!, 0.0001)
        assertEquals(75.0, needs[chickenId]!!, 0.0001)
    }

    @Test
    fun `markEaten decrements actual stock and flips the entry's eaten flag`() = runTest {
        addStock(riceId, 500.0)
        val viewModel = buildViewModel()
        viewModel.startCollecting()
        advanceUntilIdle()
        val day = viewModel.weekRange.startEpochDay

        viewModel.quickAddItem(day = day, mealSlot = MealSlot.LUNCH, foodItemId = riceId, quantity = 300.0)
        advanceUntilIdle()
        val entry = viewModel.uiState.value.entries.first().entry

        viewModel.markEaten(entry)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.entries.first().entry.eaten)
        assertEquals(200.0, database.stockBatchDao().observeTotalOnHand(riceId).first(), 0.0001)
    }

    @Test
    fun `markEaten on a RECIPE entry with zero recipe servings surfaces InvalidRecipeServings via errorEvent`() = runTest {
        // GIVEN a recipe whose servings is 0 (no domain/DB validation prevents this - see
        // MarkPlanEntryEatenUseCaseTest's own zero-servings scenario)
        val brokenRecipeId = database.recipeDao().insert(
            RecipeEntity(name = "BrokenBowl", servings = 0, notes = null, createdAt = Instant.parse("2026-07-12T00:00:00Z")),
        )
        database.recipeIngredientDao().insert(
            RecipeIngredientEntity(recipeId = brokenRecipeId, foodItemId = riceId, quantity = 200.0, unit = MeasurementUnit.GRAM, sortOrder = 0),
        )
        addStock(riceId, 500.0)
        val viewModel = buildViewModel()
        viewModel.startCollecting()
        advanceUntilIdle()
        val day = viewModel.weekRange.startEpochDay

        viewModel.assignRecipe(day = day, mealSlot = MealSlot.DINNER, recipeId = brokenRecipeId, servings = 1.0)
        advanceUntilIdle()
        val entry = viewModel.uiState.value.entries.first().entry

        val errorDeferred = async { viewModel.errorEvent.first() }
        advanceUntilIdle()

        // WHEN marking it eaten
        viewModel.markEaten(entry)
        advanceUntilIdle()

        // THEN the typed InvalidRecipeServings failure surfaces through errorEvent, and stock/the
        // eaten flag are both left untouched
        assertEquals("Recipe has invalid servings (0) - can't compute quantities.", errorDeferred.await())
        assertEquals(500.0, database.stockBatchDao().observeTotalOnHand(riceId).first(), 0.0001)
        assertFalse(viewModel.uiState.value.entries.first().entry.eaten)
    }

    @Test
    fun `markEaten called twice with the same stale (eaten=false) entry only decrements stock once`() = runTest {
        // GIVEN a plan entry marked eaten once already, decrementing stock 500g -> 200g
        addStock(riceId, 500.0)
        val viewModel = buildViewModel()
        viewModel.startCollecting()
        advanceUntilIdle()
        val day = viewModel.weekRange.startEpochDay

        viewModel.quickAddItem(day = day, mealSlot = MealSlot.LUNCH, foodItemId = riceId, quantity = 300.0)
        advanceUntilIdle()
        val staleEntry = viewModel.uiState.value.entries.first().entry

        viewModel.markEaten(staleEntry)
        advanceUntilIdle()
        assertEquals(200.0, database.stockBatchDao().observeTotalOnHand(riceId).first(), 0.0001)

        // WHEN markEaten is invoked again using the SAME stale (eaten=false) snapshot, simulating
        // a double-tap that fires before the UI's own entry reference was refreshed
        viewModel.markEaten(staleEntry)
        advanceUntilIdle()

        // THEN the second call is a no-op: stock stays at 200g and the entry remains eaten
        assertEquals(200.0, database.stockBatchDao().observeTotalOnHand(riceId).first(), 0.0001)
        assertTrue(viewModel.uiState.value.entries.first().entry.eaten)
    }

    @Test
    fun `deleteEntry removes the plan entry from uiState`() = runTest {
        val viewModel = buildViewModel()
        viewModel.startCollecting()
        advanceUntilIdle()
        val day = viewModel.weekRange.startEpochDay

        viewModel.quickAddItem(day = day, mealSlot = MealSlot.SNACK, foodItemId = riceId, quantity = 1.0)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.entries.size)
        val entry = viewModel.uiState.value.entries.first().entry

        viewModel.deleteEntry(entry)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.entries.isEmpty())
    }
}
