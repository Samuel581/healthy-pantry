package com.healthypantry.feature.recipes.ui.vm

import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.common.MainDispatcherRule
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.data.repo.FoodItemRepository
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.recipes.data.repo.RecipeRepository
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.domain.model.RecipeIngredient
import com.healthypantry.feature.recipes.domain.model.RecipeIngredientDetail
import com.healthypantry.feature.recipes.domain.model.RecipeWithIngredients
import java.time.Instant
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
 * Spec: Recipe CRUD with Ingredients
 * (openspec/changes/pantry-tracker/specs/meal-planning/spec.md).
 *
 * Hand-written fakes for [RecipeRepository]/[FoodItemRepository] (same convention as
 * `PantryViewModelTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeViewModelTest {

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
        override fun observeById(id: Long): Flow<FoodItem?> = itemsFlow.map { items -> items.find { it.id == id } }

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

    private class FakeRecipeRepository : RecipeRepository {
        private val recipesFlow = MutableStateFlow<List<Recipe>>(emptyList())
        private val ingredientsByRecipeId = mutableMapOf<Long, List<RecipeIngredient>>()
        private val foodItemsById = mutableMapOf<Long, FoodItem>()
        private var nextRecipeId = 1L

        /** Test setup helper: registers a [FoodItem] so a saved [RecipeIngredient] can resolve it. */
        fun seedFoodItem(foodItem: FoodItem) {
            foodItemsById[foodItem.id] = foodItem
        }

        override fun observeAll(): Flow<List<Recipe>> = recipesFlow

        override fun observeRecipeWithIngredients(id: Long) = recipesFlow.map { recipes ->
            val recipe = recipes.find { it.id == id } ?: return@map null
            RecipeWithIngredients(
                recipe = recipe,
                ingredients = ingredientsByRecipeId[id].orEmpty().map { ingredient ->
                    RecipeIngredientDetail(ingredient = ingredient, foodItem = foodItemsById.getValue(ingredient.foodItemId))
                },
            )
        }

        override suspend fun upsertRecipeWithIngredients(recipe: Recipe, ingredients: List<RecipeIngredient>): Long {
            val id = if (recipe.id == 0L) nextRecipeId++ else recipe.id
            val stored = recipe.copy(id = id)
            recipesFlow.value = recipesFlow.value.filterNot { it.id == id } + stored
            ingredientsByRecipeId[id] = ingredients.map { it.copy(recipeId = id) }
            return id
        }

        override suspend fun delete(recipe: Recipe) {
            recipesFlow.value = recipesFlow.value.filterNot { it.id == recipe.id }
            ingredientsByRecipeId.remove(recipe.id)
        }
    }

    private fun chickenBreast(id: Long = 1L) = FoodItem(
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
        recipeRepository: RecipeRepository = FakeRecipeRepository(),
        foodItemRepository: FoodItemRepository = FakeFoodItemRepository(),
    ) = RecipeViewModel(
        recipeRepository = recipeRepository,
        foodItemRepository = foodItemRepository,
        dispatcherProvider = testDispatcherProvider,
    )

    private fun RecipeViewModel.startCollecting() {
        collectJob = uiState.onEach { }.launchIn(MainScope())
    }

    @Test
    fun `uiState starts with isLoading true and no recipes before any recipe is persisted`() = runTest {
        val viewModel = buildViewModel()
        viewModel.startCollecting()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.recipes.isEmpty())
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `saveRecipe persists a new recipe with its ingredients`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val recipeRepository = FakeRecipeRepository()
        val foodItemId = foodItemRepository.upsert(chickenBreast())
        recipeRepository.seedFoodItem(chickenBreast(id = foodItemId))
        val viewModel = buildViewModel(recipeRepository, foodItemRepository)
        viewModel.startCollecting()
        advanceUntilIdle()

        viewModel.onNameChanged("Chicken stir-fry")
        viewModel.onServingsChanged("2")
        viewModel.addIngredientRow()
        viewModel.updateIngredientRow(0, RecipeIngredientFormRow(foodItemId = foodItemId, quantity = "200", unit = MeasurementUnit.GRAM))
        viewModel.saveRecipe()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.recipes.size)
        assertEquals("Chicken stir-fry", state.recipes.first().name)
        assertEquals(2, state.recipes.first().servings)
    }

    @Test
    fun `loadRecipeForEdit populates formState from the persisted recipe and ingredients`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val recipeRepository = FakeRecipeRepository()
        val foodItemId = foodItemRepository.upsert(chickenBreast())
        recipeRepository.seedFoodItem(chickenBreast(id = foodItemId))
        val id = recipeRepository.upsertRecipeWithIngredients(
            Recipe(name = "Chicken stir-fry", servings = 2, createdAt = Instant.EPOCH),
            listOf(RecipeIngredient(recipeId = 0, foodItemId = foodItemId, quantity = 200.0, unit = MeasurementUnit.GRAM, sortOrder = 0)),
        )
        val viewModel = buildViewModel(recipeRepository, foodItemRepository)
        viewModel.startCollecting()

        viewModel.loadRecipeForEdit(id)
        advanceUntilIdle()

        val form = viewModel.formState.value
        assertEquals("Chicken stir-fry", form.name)
        assertEquals("2", form.servings)
        assertEquals(1, form.ingredients.size)
        assertEquals(foodItemId, form.ingredients.first().foodItemId)
        assertEquals("200.0", form.ingredients.first().quantity)
    }

    @Test
    fun `deleteRecipe removes the recipe from uiState`() = runTest {
        val recipeRepository = FakeRecipeRepository()
        val id = recipeRepository.upsertRecipeWithIngredients(
            Recipe(name = "Chicken stir-fry", servings = 2, createdAt = Instant.EPOCH),
            emptyList(),
        )
        val viewModel = buildViewModel(recipeRepository)
        viewModel.startCollecting()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.recipes.size)

        viewModel.deleteRecipe(Recipe(id = id, name = "Chicken stir-fry", servings = 2, createdAt = Instant.EPOCH))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.recipes.isEmpty())
    }

    @Test
    fun `removeIngredientRow drops a middle row and saveRecipe persists the remaining rows in order`() = runTest {
        val foodItemRepository = FakeFoodItemRepository()
        val recipeRepository = FakeRecipeRepository()
        val chickenId = foodItemRepository.upsert(chickenBreast(id = 1L))
        recipeRepository.seedFoodItem(chickenBreast(id = chickenId))
        val riceItem = chickenBreast(id = 2L).copy(name = "Rice")
        val riceId = foodItemRepository.upsert(riceItem)
        recipeRepository.seedFoodItem(riceItem.copy(id = riceId))
        val beansItem = chickenBreast(id = 3L).copy(name = "Beans")
        val beansId = foodItemRepository.upsert(beansItem)
        recipeRepository.seedFoodItem(beansItem.copy(id = beansId))
        val viewModel = buildViewModel(recipeRepository, foodItemRepository)
        viewModel.startCollecting()
        advanceUntilIdle()

        // GIVEN three ingredient rows: Chicken, Rice, Beans
        viewModel.onNameChanged("Bowl")
        viewModel.addIngredientRow()
        viewModel.updateIngredientRow(0, RecipeIngredientFormRow(foodItemId = chickenId, quantity = "150", unit = MeasurementUnit.GRAM))
        viewModel.addIngredientRow()
        viewModel.updateIngredientRow(1, RecipeIngredientFormRow(foodItemId = riceId, quantity = "200", unit = MeasurementUnit.GRAM))
        viewModel.addIngredientRow()
        viewModel.updateIngredientRow(2, RecipeIngredientFormRow(foodItemId = beansId, quantity = "100", unit = MeasurementUnit.GRAM))

        // WHEN the middle row (Rice) is removed and the recipe is saved
        viewModel.removeIngredientRow(1)
        viewModel.saveRecipe()
        advanceUntilIdle()

        // THEN only Chicken and Beans remain, in their original relative order, each with the
        // correct quantity and a contiguous 0-based sortOrder (not a gap left by the removed row)
        val saved = recipeRepository.observeRecipeWithIngredients(viewModel.uiState.value.recipes.first().id).first()
        requireNotNull(saved)
        assertEquals(2, saved.ingredients.size)
        assertEquals("Chicken breast", saved.ingredients[0].foodItem.name)
        assertEquals(150.0, saved.ingredients[0].ingredient.quantity, 0.0001)
        assertEquals(0, saved.ingredients[0].ingredient.sortOrder)
        assertEquals("Beans", saved.ingredients[1].foodItem.name)
        assertEquals(100.0, saved.ingredients[1].ingredient.quantity, 0.0001)
        assertEquals(1, saved.ingredients[1].ingredient.sortOrder)
    }

    @Test
    fun `startNewRecipe resets formState to a blank recipe`() = runTest {
        val recipeRepository = FakeRecipeRepository()
        val id = recipeRepository.upsertRecipeWithIngredients(
            Recipe(name = "Chicken stir-fry", servings = 2, createdAt = Instant.EPOCH),
            emptyList(),
        )
        val viewModel = buildViewModel(recipeRepository)
        viewModel.startCollecting()
        viewModel.loadRecipeForEdit(id)
        advanceUntilIdle()
        assertEquals("Chicken stir-fry", viewModel.formState.value.name)

        viewModel.startNewRecipe()

        assertEquals("", viewModel.formState.value.name)
        assertEquals(0L, viewModel.formState.value.id)
        assertNull(viewModel.formState.value.ingredients.firstOrNull())
    }

    @Test
    fun `saveRecipe emits an errorEvent instead of crashing when the repository throws`() = runTest {
        val failingRepository = object : RecipeRepository {
            override fun observeAll(): Flow<List<Recipe>> = MutableStateFlow(emptyList())
            override fun observeRecipeWithIngredients(id: Long): Flow<RecipeWithIngredients?> = MutableStateFlow(null)
            override suspend fun upsertRecipeWithIngredients(recipe: Recipe, ingredients: List<RecipeIngredient>): Long =
                throw IllegalStateException("simulated repository failure")
            override suspend fun delete(recipe: Recipe) = throw IllegalStateException("simulated repository failure")
        }
        val viewModel = buildViewModel(failingRepository)
        viewModel.startCollecting()

        val errorDeferred = async { viewModel.errorEvent.first() }
        advanceUntilIdle()

        viewModel.onNameChanged("Chicken stir-fry")
        viewModel.saveRecipe()
        advanceUntilIdle()

        assertEquals("simulated repository failure", errorDeferred.await())
    }
}
