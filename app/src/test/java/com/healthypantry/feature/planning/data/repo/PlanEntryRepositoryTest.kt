package com.healthypantry.feature.planning.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.database.AppDatabase
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.data.entity.FoodItemEntity
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.planning.domain.model.MealSlot
import com.healthypantry.feature.planning.domain.model.PlanEntry
import com.healthypantry.feature.planning.domain.model.PlanEntryType
import com.healthypantry.feature.recipes.data.entity.RecipeEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Spec: Weekly Plan Assignment and Quick-Add
 * (sdd/pantry-tracker/spec, domain meal-planning)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanEntryRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: PlanEntryRepository
    private var bowlId: Long = 0
    private var bananaId: Long = 0

    private val monday = 20000L
    private val tuesday = 20001L

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
        repository = PlanEntryRepositoryImpl(database.planEntryDao(), testDispatcherProvider)

        bowlId = database.recipeDao().insert(
            RecipeEntity(name = "Bowl", servings = 2, notes = null, createdAt = Instant.parse("2026-07-12T00:00:00Z")),
        )
        bananaId = database.foodItemDao().insert(
            FoodItemEntity(
                name = "Banana",
                canonicalUnit = MeasurementUnit.PIECE,
                source = FoodItemSource.MANUAL,
                caloriesPerUnit = 89.0,
                proteinGramsPerUnit = 1.1,
                carbsGramsPerUnit = 22.8,
                fatGramsPerUnit = 0.3,
                barcode = null,
                externalSourceId = null,
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upsert creates a recipe assignment and observeWeek returns the domain model`() = runTest {
        // GIVEN recipe "Bowl" exists (see setUp)
        // WHEN assigned to Monday/dinner
        val id = repository.upsert(
            PlanEntry(dateEpochDay = monday, mealSlot = MealSlot.DINNER, type = PlanEntryType.RECIPE, recipeId = bowlId, servings = 1.0),
        )

        // THEN Monday/dinner shows "Bowl"
        val stored = repository.observeWeek(monday, monday).first().first { it.id == id }
        assertEquals(PlanEntryType.RECIPE, stored.type)
        assertEquals(bowlId, stored.recipeId)
    }

    @Test
    fun `upsert creates a quick-add item entry without a recipe`() = runTest {
        // GIVEN item "Banana" exists (see setUp)
        // WHEN quick-added to Tuesday/breakfast without a recipe
        val id = repository.upsert(
            PlanEntry(dateEpochDay = tuesday, mealSlot = MealSlot.BREAKFAST, type = PlanEntryType.ITEM, foodItemId = bananaId, quantity = 1.0),
        )

        val stored = repository.observeWeek(tuesday, tuesday).first().first { it.id == id }
        assertEquals(PlanEntryType.ITEM, stored.type)
        assertNull(stored.recipeId)
        assertEquals(bananaId, stored.foodItemId)
    }

    @Test
    fun `upsert with an existing id updates the entry instead of inserting a duplicate`() = runTest {
        val id = repository.upsert(
            PlanEntry(dateEpochDay = monday, mealSlot = MealSlot.DINNER, type = PlanEntryType.RECIPE, recipeId = bowlId, servings = 1.0),
        )
        val stored = repository.observeWeek(monday, monday).first().first { it.id == id }

        repository.upsert(stored.copy(servings = 3.0))

        val week = repository.observeWeek(monday, monday).first()
        assertEquals(1, week.size)
        assertEquals(3.0, week.first().servings ?: -1.0, 0.0001)
    }

    @Test
    fun `markEaten marks the entry eaten at the given instant`() = runTest {
        val id = repository.upsert(
            PlanEntry(dateEpochDay = monday, mealSlot = MealSlot.DINNER, type = PlanEntryType.RECIPE, recipeId = bowlId, servings = 1.0),
        )
        val eatenAt = Instant.parse("2026-07-13T19:00:00Z")

        repository.markEaten(id, eatenAt)

        val stored = repository.observeWeek(monday, monday).first().first { it.id == id }
        assertTrue(stored.eaten)
        assertEquals(eatenAt.toEpochMilli(), stored.eatenAt)
    }

    @Test
    fun `delete removes the plan entry so it no longer appears in observeWeek`() = runTest {
        val id = repository.upsert(
            PlanEntry(dateEpochDay = monday, mealSlot = MealSlot.DINNER, type = PlanEntryType.RECIPE, recipeId = bowlId, servings = 1.0),
        )
        val stored = repository.observeWeek(monday, monday).first().first { it.id == id }

        repository.delete(stored)

        assertTrue(repository.observeWeek(monday, monday).first().isEmpty())
    }
}
