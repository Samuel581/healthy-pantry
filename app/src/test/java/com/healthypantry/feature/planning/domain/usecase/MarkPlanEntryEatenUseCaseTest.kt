package com.healthypantry.feature.planning.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.common.Result
import com.healthypantry.core.database.AppDatabase
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.core.unit.UnitConverter
import com.healthypantry.feature.pantry.data.entity.FoodItemEntity
import com.healthypantry.feature.pantry.data.entity.StockBatchEntity
import com.healthypantry.feature.pantry.data.entity.UnitConversionEntity
import com.healthypantry.feature.pantry.data.repo.StockBatchRepositoryImpl
import com.healthypantry.feature.pantry.data.repo.UnitConversionRepositoryImpl
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.planning.data.repo.PlanEntryRepositoryImpl
import com.healthypantry.feature.planning.domain.model.MealSlot
import com.healthypantry.feature.planning.domain.model.PlanEntry
import com.healthypantry.feature.planning.domain.model.PlanEntryType
import com.healthypantry.feature.recipes.data.entity.RecipeEntity
import com.healthypantry.feature.recipes.data.entity.RecipeIngredientEntity
import com.healthypantry.feature.recipes.data.repo.RecipeRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Spec: "Mark-eaten decrements actual" (sdd/pantry-tracker/spec, "Projected vs Actual Stock").
 *
 * Exercises [MarkPlanEntryEatenUseCase] against a real in-memory Room database (Robolectric) with
 * every real repository implementation, proving the stock decrement and the `markEaten` flag-flip
 * commit together and that an unresolved ingredient conversion leaves both untouched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkPlanEntryEatenUseCaseTest {

    private lateinit var database: AppDatabase
    private lateinit var useCase: MarkPlanEntryEatenUseCase
    private var riceId: Long = 0
    private var chickenId: Long = 0
    private var bowlId: Long = 0

    private val testDispatcherProvider = object : DispatcherProvider {
        private val dispatcher = UnconfinedTestDispatcher()
        override val main get() = dispatcher
        override val io get() = dispatcher
        override val default get() = dispatcher
    }

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        val stockBatchRepository = StockBatchRepositoryImpl(database.stockBatchDao(), testDispatcherProvider)
        val recipeRepository = RecipeRepositoryImpl(database, database.recipeDao(), database.recipeIngredientDao(), testDispatcherProvider)
        val unitConversionRepository = UnitConversionRepositoryImpl(database.unitConversionDao())
        val planEntryRepository = PlanEntryRepositoryImpl(database.planEntryDao(), testDispatcherProvider)

        useCase = MarkPlanEntryEatenUseCase(
            database = database,
            stockBatchRepository = stockBatchRepository,
            recipeRepository = recipeRepository,
            unitConversionRepository = unitConversionRepository,
            planEntryRepository = planEntryRepository,
            unitConverter = UnitConverter(),
        )

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

    @Test
    fun `mark-eaten on an ITEM entry decrements actual stock by its quantity`() = runTest {
        // GIVEN a planned entry of 300g Rice for today, and 500g Rice actually on hand
        addStock(riceId, 500.0)
        val entry = PlanEntry(dateEpochDay = 1, mealSlot = MealSlot.LUNCH, type = PlanEntryType.ITEM, foodItemId = riceId, quantity = 300.0)
        val eatenAt = Instant.parse("2026-07-13T12:00:00Z")

        // WHEN the user marks it eaten
        val result = useCase.execute(entry.copy(id = insertEntry(entry)), eatenAt)

        // THEN actual stock decreases by 300g
        assertTrue(result is Result.Success)
        assertEquals(200.0, database.stockBatchDao().observeTotalOnHand(riceId).first(), 0.0001)
    }

    @Test
    fun `mark-eaten flips the eaten flag so the entry no longer appears in projected deficit`() = runTest {
        addStock(riceId, 500.0)
        val planEntry = PlanEntry(dateEpochDay = 1, mealSlot = MealSlot.LUNCH, type = PlanEntryType.ITEM, foodItemId = riceId, quantity = 300.0)
        val id = insertEntry(planEntry)

        useCase.execute(planEntry.copy(id = id), Instant.parse("2026-07-13T12:00:00Z"))

        val stored = database.planEntryDao().observeWeek(1, 1).first().first { it.id == id }
        assertTrue(stored.eaten)
    }

    @Test
    fun `mark-eaten on a RECIPE entry scales ingredients by servings ratio and decrements each food item`() = runTest {
        // GIVEN "Bowl" is a 2-serving recipe (200g Rice + 150g Chicken), planned at 1 serving
        addStock(riceId, 500.0)
        addStock(chickenId, 500.0)
        val id = insertEntry(PlanEntry(dateEpochDay = 1, mealSlot = MealSlot.DINNER, type = PlanEntryType.RECIPE, recipeId = bowlId, servings = 1.0))
        val entry = PlanEntry(id = id, dateEpochDay = 1, mealSlot = MealSlot.DINNER, type = PlanEntryType.RECIPE, recipeId = bowlId, servings = 1.0)

        // WHEN marked eaten
        val result = useCase.execute(entry, Instant.parse("2026-07-13T19:00:00Z"))

        // THEN each ingredient is decremented at half quantity: 100g Rice, 75g Chicken
        assertTrue(result is Result.Success)
        assertEquals(400.0, database.stockBatchDao().observeTotalOnHand(riceId).first(), 0.0001)
        assertEquals(425.0, database.stockBatchDao().observeTotalOnHand(chickenId).first(), 0.0001)
    }

    @Test
    fun `mark-eaten on a RECIPE entry converts an ingredient unit via its registered factor`() = runTest {
        // GIVEN a recipe whose Rice ingredient is specified in cups, and Rice registers 1 cup = 185g
        val cupBowlId = database.recipeDao().insert(RecipeEntity(name = "CupBowl", servings = 1, notes = null, createdAt = Instant.parse("2026-07-12T00:00:00Z")))
        database.recipeIngredientDao().insert(
            RecipeIngredientEntity(recipeId = cupBowlId, foodItemId = riceId, quantity = 2.0, unit = MeasurementUnit.CUP, sortOrder = 0),
        )
        database.unitConversionDao().insert(UnitConversionEntity(foodItemId = riceId, fromUnit = MeasurementUnit.CUP, toUnit = MeasurementUnit.GRAM, factor = 185.0))
        addStock(riceId, 500.0)
        val id = insertEntry(PlanEntry(dateEpochDay = 1, mealSlot = MealSlot.DINNER, type = PlanEntryType.RECIPE, recipeId = cupBowlId, servings = 1.0))
        val entry = PlanEntry(id = id, dateEpochDay = 1, mealSlot = MealSlot.DINNER, type = PlanEntryType.RECIPE, recipeId = cupBowlId, servings = 1.0)

        val result = useCase.execute(entry, Instant.parse("2026-07-13T19:00:00Z"))

        // THEN 2 cups converts to 370g before decrementing
        assertTrue(result is Result.Success)
        assertEquals(130.0, database.stockBatchDao().observeTotalOnHand(riceId).first(), 0.0001)
    }

    @Test
    fun `mark-eaten on a RECIPE entry with an unresolved conversion fails with zero side effects`() = runTest {
        // GIVEN a recipe whose Rice ingredient is specified in cups, with NO registered conversion
        val cupBowlId = database.recipeDao().insert(RecipeEntity(name = "CupBowl", servings = 1, notes = null, createdAt = Instant.parse("2026-07-12T00:00:00Z")))
        database.recipeIngredientDao().insert(
            RecipeIngredientEntity(recipeId = cupBowlId, foodItemId = riceId, quantity = 2.0, unit = MeasurementUnit.CUP, sortOrder = 0),
        )
        addStock(riceId, 500.0)
        val id = insertEntry(PlanEntry(dateEpochDay = 1, mealSlot = MealSlot.DINNER, type = PlanEntryType.RECIPE, recipeId = cupBowlId, servings = 1.0))
        val entry = PlanEntry(id = id, dateEpochDay = 1, mealSlot = MealSlot.DINNER, type = PlanEntryType.RECIPE, recipeId = cupBowlId, servings = 1.0)

        // WHEN marked eaten
        val result = useCase.execute(entry, Instant.parse("2026-07-13T19:00:00Z"))

        // THEN the call fails, stock is untouched, and the entry is not marked eaten
        assertTrue(result is Result.Failure)
        assertEquals(500.0, database.stockBatchDao().observeTotalOnHand(riceId).first(), 0.0001)
        val stored = database.planEntryDao().observeWeek(1, 1).first().first { it.id == id }
        assertFalse(stored.eaten)
    }

    @Test
    fun `mark-eaten on an ITEM entry clamps to zero instead of going negative when overdrawn`() = runTest {
        // GIVEN only 100g Rice actually on hand but a plan entry for 300g
        addStock(riceId, 100.0)
        val id = insertEntry(PlanEntry(dateEpochDay = 1, mealSlot = MealSlot.LUNCH, type = PlanEntryType.ITEM, foodItemId = riceId, quantity = 300.0))
        val entry = PlanEntry(id = id, dateEpochDay = 1, mealSlot = MealSlot.LUNCH, type = PlanEntryType.ITEM, foodItemId = riceId, quantity = 300.0)

        val result = useCase.execute(entry, Instant.parse("2026-07-13T12:00:00Z"))

        assertTrue(result is Result.Success)
        assertEquals(0.0, database.stockBatchDao().observeTotalOnHand(riceId).first(), 0.0001)
    }

    private suspend fun insertEntry(entry: PlanEntry): Long = database.planEntryDao().insert(
        com.healthypantry.feature.planning.data.entity.PlanEntryEntity(
            dateEpochDay = entry.dateEpochDay,
            mealSlot = entry.mealSlot,
            type = entry.type,
            recipeId = entry.recipeId,
            foodItemId = entry.foodItemId,
            quantity = entry.quantity,
            servings = entry.servings,
            eaten = false,
            eatenAt = null,
        ),
    )
}
