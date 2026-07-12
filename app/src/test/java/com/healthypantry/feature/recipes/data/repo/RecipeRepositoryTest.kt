package com.healthypantry.feature.recipes.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.database.AppDatabase
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.data.entity.FoodItemEntity
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.recipes.data.dao.RecipeIngredientDao
import com.healthypantry.feature.recipes.data.entity.RecipeIngredientEntity
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.domain.model.RecipeIngredient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
 * Spec: Recipe CRUD with Ingredients
 * (sdd/pantry-tracker/spec, domain meal-planning)
 *
 * Exercises [RecipeRepositoryImpl] against a real in-memory Room database (Robolectric) to prove
 * it maps Room relation types to the domain-facing [com.healthypantry.feature.recipes.domain.model.RecipeWithIngredients]
 * correctly and does not leak Room types.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecipeRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: RecipeRepository
    private var riceId: Long = 0
    private var chickenId: Long = 0

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
        repository = RecipeRepositoryImpl(database, database.recipeDao(), database.recipeIngredientDao(), testDispatcherProvider)

        riceId = database.foodItemDao().insert(foodItem("Rice"))
        chickenId = database.foodItemDao().insert(foodItem("Chicken"))
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

    private fun bowlRecipe() = Recipe(
        name = "Bowl",
        servings = 2,
        createdAt = Instant.parse("2026-07-12T00:00:00Z"),
    )

    @Test
    fun `upsertRecipeWithIngredients on create persists the recipe with ingredients in entered order`() = runTest {
        // GIVEN pantry items "Rice" and "Chicken" exist (see setUp)
        // WHEN the user creates recipe "Bowl" with 200 g Rice and 150 g Chicken
        val recipeId = repository.upsertRecipeWithIngredients(
            bowlRecipe(),
            listOf(
                RecipeIngredient(recipeId = 0, foodItemId = riceId, quantity = 200.0, unit = MeasurementUnit.GRAM, sortOrder = 99),
                RecipeIngredient(recipeId = 0, foodItemId = chickenId, quantity = 150.0, unit = MeasurementUnit.GRAM, sortOrder = 99),
            ),
        )

        // THEN the recipe persists with both ingredients in entered order
        val withIngredients = repository.observeRecipeWithIngredients(recipeId).first()
        assertEquals("Bowl", withIngredients?.recipe?.name)
        assertEquals(
            listOf("Rice", "Chicken"),
            withIngredients?.ingredients?.map { it.foodItem.name },
        )
        assertEquals(listOf(0, 1), withIngredients?.ingredients?.map { it.ingredient.sortOrder })
    }

    @Test
    fun `upsertRecipeWithIngredients on edit fully replaces the previous ingredient list`() = runTest {
        val recipeId = repository.upsertRecipeWithIngredients(
            bowlRecipe(),
            listOf(RecipeIngredient(recipeId = 0, foodItemId = riceId, quantity = 200.0, unit = MeasurementUnit.GRAM, sortOrder = 0)),
        )

        repository.upsertRecipeWithIngredients(
            bowlRecipe().copy(id = recipeId, servings = 4),
            listOf(RecipeIngredient(recipeId = recipeId, foodItemId = chickenId, quantity = 300.0, unit = MeasurementUnit.GRAM, sortOrder = 0)),
        )

        val withIngredients = repository.observeRecipeWithIngredients(recipeId).first()
        assertEquals(4, withIngredients?.recipe?.servings)
        assertEquals(1, withIngredients?.ingredients?.size)
        assertEquals("Chicken", withIngredients?.ingredients?.first()?.foodItem?.name)
    }

    @Test
    fun `observeAll returns every persisted recipe as a domain model`() = runTest {
        repository.upsertRecipeWithIngredients(bowlRecipe(), emptyList())
        repository.upsertRecipeWithIngredients(bowlRecipe().copy(name = "Salad"), emptyList())

        val all = repository.observeAll().first()

        assertEquals(setOf("Bowl", "Salad"), all.map { it.name }.toSet())
    }

    @Test
    fun `delete removes the recipe so it no longer appears in observeAll`() = runTest {
        val recipeId = repository.upsertRecipeWithIngredients(bowlRecipe(), emptyList())
        val stored = repository.observeAll().first().first { it.id == recipeId }

        repository.delete(stored)

        assertTrue(repository.observeAll().first().isEmpty())
        assertNull(repository.observeRecipeWithIngredients(recipeId).first())
    }

    @Test
    fun `upsertRecipeWithIngredients on edit rolls back the delete when insertAll fails`() = runTest {
        // GIVEN a persisted recipe with one ingredient
        val recipeId = repository.upsertRecipeWithIngredients(
            bowlRecipe(),
            listOf(RecipeIngredient(recipeId = 0, foodItemId = riceId, quantity = 200.0, unit = MeasurementUnit.GRAM, sortOrder = 0)),
        )
        val failingRepository = RecipeRepositoryImpl(
            database,
            database.recipeDao(),
            insertAllThrowingDao(database.recipeIngredientDao()),
            testDispatcherProvider,
        )

        // WHEN the edit's insertAll fails after the previous ingredients were deleted
        var thrown: Throwable? = null
        try {
            failingRepository.upsertRecipeWithIngredients(
                bowlRecipe().copy(id = recipeId, servings = 4),
                listOf(RecipeIngredient(recipeId = recipeId, foodItemId = chickenId, quantity = 300.0, unit = MeasurementUnit.GRAM, sortOrder = 0)),
            )
        } catch (e: IllegalStateException) {
            thrown = e
        }
        assertTrue(thrown is IllegalStateException)

        // THEN the transaction rolled back: the original ingredient is still intact
        val withIngredients = repository.observeRecipeWithIngredients(recipeId).first()
        assertEquals(2, withIngredients?.recipe?.servings)
        assertEquals(1, withIngredients?.ingredients?.size)
        assertEquals("Rice", withIngredients?.ingredients?.first()?.foodItem?.name)
    }

    @Test
    fun `observeRecipeWithIngredients returns ingredients by sortOrder even after an out-of-order update`() = runTest {
        // GIVEN a recipe persisted with Rice (sortOrder 0) then Chicken (sortOrder 1)
        val recipeId = repository.upsertRecipeWithIngredients(
            bowlRecipe(),
            listOf(
                RecipeIngredient(recipeId = 0, foodItemId = riceId, quantity = 200.0, unit = MeasurementUnit.GRAM, sortOrder = 0),
                RecipeIngredient(recipeId = 0, foodItemId = chickenId, quantity = 150.0, unit = MeasurementUnit.GRAM, sortOrder = 1),
            ),
        )
        val rice = database.recipeIngredientDao().observeForRecipe(recipeId).first().first { it.foodItemId == riceId }
        val chicken = database.recipeIngredientDao().observeForRecipe(recipeId).first().first { it.foodItemId == chickenId }

        // WHEN a single ingredient's sortOrder is swapped in place (not by re-inserting)
        database.recipeIngredientDao().update(rice.copy(sortOrder = 1))
        database.recipeIngredientDao().update(chicken.copy(sortOrder = 0))

        // THEN the relation still returns ingredients ordered by sortOrder, not insertion order
        val withIngredients = repository.observeRecipeWithIngredients(recipeId).first()
        assertEquals(
            listOf("Chicken", "Rice"),
            withIngredients?.ingredients?.map { it.foodItem.name },
        )
    }

    /** Delegates every call to [delegate] except [insertAll], which always throws. */
    private fun insertAllThrowingDao(delegate: RecipeIngredientDao) = object : RecipeIngredientDao {
        override suspend fun insert(ingredient: RecipeIngredientEntity) = delegate.insert(ingredient)
        override suspend fun insertAll(ingredients: List<RecipeIngredientEntity>): List<Long> =
            throw IllegalStateException("forced insertAll failure")
        override suspend fun update(ingredient: RecipeIngredientEntity) = delegate.update(ingredient)
        override suspend fun delete(ingredient: RecipeIngredientEntity) = delegate.delete(ingredient)
        override suspend fun deleteAllForRecipe(recipeId: Long) = delegate.deleteAllForRecipe(recipeId)
        override fun observeForRecipe(recipeId: Long): Flow<List<RecipeIngredientEntity>> =
            delegate.observeForRecipe(recipeId)
    }
}
