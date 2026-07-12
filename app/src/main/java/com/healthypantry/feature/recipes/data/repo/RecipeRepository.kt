package com.healthypantry.feature.recipes.data.repo

import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.feature.recipes.data.dao.RecipeDao
import com.healthypantry.feature.recipes.data.dao.RecipeIngredientDao
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.domain.model.RecipeIngredient
import com.healthypantry.feature.recipes.domain.model.RecipeWithIngredients
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Domain-facing CRUD access to [Recipe]s and their [RecipeIngredient] lines (spec "Recipe CRUD
 * with Ingredients"). Wraps [RecipeDao]/[RecipeIngredientDao] and never exposes Room entity
 * types to callers.
 *
 * A single repository owns both DAOs (unlike `FoodItemRepository`/`StockBatchRepository`, which
 * are split) because a [RecipeIngredient] has no independent lifecycle or query surface apart
 * from its owning [Recipe] — there is no spec scenario for querying/creating an ingredient line
 * outside the context of persisting its whole recipe.
 */
interface RecipeRepository {
    fun observeAll(): Flow<List<Recipe>>
    fun observeRecipeWithIngredients(id: Long): Flow<RecipeWithIngredients?>

    /**
     * Persists [recipe] together with its complete ordered [ingredients] list in one call (spec
     * "Recipe CRUD with Ingredients" — ingredients persist "in entered order").
     *
     * On create ([recipe.id] == 0) the recipe and every ingredient are freshly inserted. On edit
     * (existing id) every previous ingredient row for the recipe is replaced by [ingredients] —
     * a full replace, not a merge/diff, matching the requirement's "edit ... as an ordered list"
     * wording (the whole list is the unit of edit).
     *
     * Each ingredient's [RecipeIngredient.sortOrder] is overwritten to match its position in
     * [ingredients] regardless of what the caller passed, so entered order is always exactly
     * preserved on read.
     *
     * Returns the persisted recipe's id.
     */
    suspend fun upsertRecipeWithIngredients(recipe: Recipe, ingredients: List<RecipeIngredient>): Long

    suspend fun delete(recipe: Recipe)
}

class RecipeRepositoryImpl @Inject constructor(
    private val recipeDao: RecipeDao,
    private val recipeIngredientDao: RecipeIngredientDao,
    private val dispatcherProvider: DispatcherProvider,
) : RecipeRepository {

    override fun observeAll(): Flow<List<Recipe>> =
        recipeDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeRecipeWithIngredients(id: Long): Flow<RecipeWithIngredients?> =
        recipeDao.observeRecipeWithIngredients(id).map { it?.toDomain() }

    override suspend fun upsertRecipeWithIngredients(
        recipe: Recipe,
        ingredients: List<RecipeIngredient>,
    ): Long = withContext(dispatcherProvider.io) {
        val recipeId = if (recipe.id == 0L) {
            recipeDao.insert(recipe.toEntity())
        } else {
            recipeDao.update(recipe.toEntity())
            recipeIngredientDao.deleteAllForRecipe(recipe.id)
            recipe.id
        }

        val orderedEntities = ingredients.mapIndexed { index, ingredient ->
            ingredient.copy(recipeId = recipeId, sortOrder = index).toEntity()
        }
        if (orderedEntities.isNotEmpty()) {
            recipeIngredientDao.insertAll(orderedEntities)
        }

        recipeId
    }

    override suspend fun delete(recipe: Recipe) = withContext(dispatcherProvider.io) {
        recipeDao.delete(recipe.toEntity())
    }
}
