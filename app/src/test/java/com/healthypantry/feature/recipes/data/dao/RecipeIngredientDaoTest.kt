package com.healthypantry.feature.recipes.data.dao

import android.database.sqlite.SQLiteConstraintException
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
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Spec: Recipe CRUD with Ingredients
 * (sdd/pantry-tracker/spec, domain meal-planning)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecipeIngredientDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: RecipeIngredientDao
    private lateinit var recipeDao: RecipeDao
    private lateinit var foodItemDao: FoodItemDao
    private var bowlId: Long = 0
    private var riceId: Long = 0
    private var chickenId: Long = 0

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.recipeIngredientDao()
        recipeDao = database.recipeDao()
        foodItemDao = database.foodItemDao()

        riceId = foodItemDao.insert(foodItem("Rice"))
        chickenId = foodItemDao.insert(foodItem("Chicken"))
        bowlId = recipeDao.insert(
            RecipeEntity(name = "Bowl", servings = 2, notes = null, createdAt = Instant.parse("2026-07-12T00:00:00Z")),
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

    private fun ingredient(foodItemId: Long, sortOrder: Int, quantity: Double = 100.0) = RecipeIngredientEntity(
        recipeId = bowlId,
        foodItemId = foodItemId,
        quantity = quantity,
        unit = MeasurementUnit.GRAM,
        sortOrder = sortOrder,
    )

    @Test
    fun `observeForRecipe returns ingredients ordered by sortOrder regardless of insertion order`() = runBlocking {
        dao.insert(ingredient(chickenId, sortOrder = 1))
        dao.insert(ingredient(riceId, sortOrder = 0))

        val ordered = dao.observeForRecipe(bowlId).first()

        assertEquals(listOf(riceId, chickenId), ordered.map { it.foodItemId })
    }

    @Test
    fun `insertAll inserts a full ingredient list in one call`() = runBlocking {
        dao.insertAll(listOf(ingredient(riceId, sortOrder = 0), ingredient(chickenId, sortOrder = 1)))

        assertEquals(2, dao.observeForRecipe(bowlId).first().size)
    }

    @Test
    fun `deleteAllForRecipe removes only that recipe's ingredient rows`() = runBlocking {
        val saladId = recipeDao.insert(
            RecipeEntity(name = "Salad", servings = 1, notes = null, createdAt = Instant.parse("2026-07-12T00:00:00Z")),
        )
        dao.insert(ingredient(riceId, sortOrder = 0))
        dao.insert(RecipeIngredientEntity(recipeId = saladId, foodItemId = chickenId, quantity = 50.0, unit = MeasurementUnit.GRAM, sortOrder = 0))

        dao.deleteAllForRecipe(bowlId)

        assertEquals(0, dao.observeForRecipe(bowlId).first().size)
        assertEquals(1, dao.observeForRecipe(saladId).first().size)
    }

    @Test
    fun `update overwrites an ingredient's quantity`() = runBlocking {
        val id = dao.insert(ingredient(riceId, sortOrder = 0, quantity = 200.0))
        val stored = dao.observeForRecipe(bowlId).first().first { it.id == id }

        dao.update(stored.copy(quantity = 300.0))

        assertEquals(300.0, dao.observeForRecipe(bowlId).first().first().quantity, 0.0001)
    }

    @Test
    fun `deleting a food item still referenced by a recipe ingredient is rejected`() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                dao.insert(ingredient(riceId, sortOrder = 0))
                foodItemDao.delete(foodItemDao.observeById(riceId).first()!!)
            }
        }
    }
}
