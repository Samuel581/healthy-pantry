package com.healthypantry.feature.recipes.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthypantry.core.database.AppDatabase
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.data.dao.FoodItemDao
import com.healthypantry.feature.pantry.data.entity.FoodItemEntity
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.recipes.data.entity.RecipeEntity
import com.healthypantry.feature.recipes.data.entity.RecipeIngredientEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
 * Robolectric + in-memory Room per design.md "Testing strategy" — fast DAO-correctness
 * feedback without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecipeDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var recipeDao: RecipeDao
    private lateinit var recipeIngredientDao: RecipeIngredientDao
    private lateinit var foodItemDao: FoodItemDao
    private var riceId: Long = 0
    private var chickenId: Long = 0

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        recipeDao = database.recipeDao()
        recipeIngredientDao = database.recipeIngredientDao()
        foodItemDao = database.foodItemDao()

        riceId = foodItemDao.insert(foodItem("Rice"))
        chickenId = foodItemDao.insert(foodItem("Chicken"))
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

    private fun bowlRecipe() = RecipeEntity(
        name = "Bowl",
        servings = 2,
        notes = null,
        createdAt = Instant.parse("2026-07-12T00:00:00Z"),
    )

    @Test
    fun `insert persists a recipe and observeById returns it`() = runBlocking {
        val id = recipeDao.insert(bowlRecipe())

        val stored = recipeDao.observeById(id).first()

        assertEquals("Bowl", stored?.name)
        assertEquals(2, stored?.servings)
    }

    @Test
    fun `observeAll returns every inserted recipe`() = runBlocking {
        recipeDao.insert(bowlRecipe())
        recipeDao.insert(bowlRecipe().copy(name = "Salad"))

        val all = recipeDao.observeAll().first()

        assertEquals(2, all.size)
        assertEquals(setOf("Bowl", "Salad"), all.map { it.name }.toSet())
    }

    @Test
    fun `update overwrites an existing recipe's fields`() = runBlocking {
        val id = recipeDao.insert(bowlRecipe())
        val stored = recipeDao.observeById(id).first()!!

        recipeDao.update(stored.copy(servings = 4, notes = "double batch"))

        val updated = recipeDao.observeById(id).first()
        assertEquals(4, updated?.servings)
        assertEquals("double batch", updated?.notes)
    }

    @Test
    fun `delete removes the recipe`() = runBlocking {
        val id = recipeDao.insert(bowlRecipe())
        val stored = recipeDao.observeById(id).first()!!

        recipeDao.delete(stored)

        assertNull(recipeDao.observeById(id).first())
    }

    @Test
    fun `observeRecipeWithIngredients returns null for a nonexistent recipe`() = runBlocking {
        assertNull(recipeDao.observeRecipeWithIngredients(999L).first())
    }

    @Test
    fun `observeRecipeWithIngredients joins the recipe with its ingredients in entered order, each paired with its food item`() =
        runBlocking {
            // Given pantry items "Rice" and "Chicken" exist (see setUp)
            // When the user creates recipe "Bowl" with 200 g Rice and 150 g Chicken
            val recipeId = recipeDao.insert(bowlRecipe())
            recipeIngredientDao.insertAll(
                listOf(
                    RecipeIngredientEntity(
                        recipeId = recipeId,
                        foodItemId = riceId,
                        quantity = 200.0,
                        unit = MeasurementUnit.GRAM,
                        sortOrder = 0,
                    ),
                    RecipeIngredientEntity(
                        recipeId = recipeId,
                        foodItemId = chickenId,
                        quantity = 150.0,
                        unit = MeasurementUnit.GRAM,
                        sortOrder = 1,
                    ),
                ),
            )

            // Then the recipe persists with both ingredients in entered order
            val withIngredients = recipeDao.observeRecipeWithIngredients(recipeId).first()

            assertEquals("Bowl", withIngredients?.recipe?.name)
            assertEquals(2, withIngredients?.ingredients?.size)
            assertEquals(
                listOf("Rice", "Chicken"),
                withIngredients?.ingredients?.map { it.foodItem.name },
            )
            assertEquals(200.0, withIngredients?.ingredients?.get(0)?.ingredient?.quantity ?: -1.0, 0.0001)
            assertEquals(150.0, withIngredients?.ingredients?.get(1)?.ingredient?.quantity ?: -1.0, 0.0001)
        }

    @Test
    fun `deleting a recipe cascades its ingredient rows`() = runBlocking {
        val recipeId = recipeDao.insert(bowlRecipe())
        recipeIngredientDao.insertAll(
            listOf(
                RecipeIngredientEntity(
                    recipeId = recipeId,
                    foodItemId = riceId,
                    quantity = 200.0,
                    unit = MeasurementUnit.GRAM,
                    sortOrder = 0,
                ),
            ),
        )

        recipeDao.delete(recipeDao.observeById(recipeId).first()!!)

        assertTrue(recipeIngredientDao.observeForRecipe(recipeId).first().isEmpty())
    }
}
