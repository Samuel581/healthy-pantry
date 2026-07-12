package com.healthypantry.feature.recipes.data.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.healthypantry.feature.pantry.data.entity.FoodItemEntity
import com.healthypantry.feature.recipes.data.entity.RecipeEntity
import com.healthypantry.feature.recipes.data.entity.RecipeIngredientEntity

/**
 * Room relation: a [RecipeIngredientEntity] row joined with the [FoodItemEntity] it references.
 * Not a [androidx.room.Entity] itself — Room populates [foodItem] via the `entity =` override on
 * the outer [RecipeWithIngredientsEntity.ingredients] relation (Room's documented nested/
 * transitive relation pattern).
 */
data class RecipeIngredientWithFoodItemEntity(
    @Embedded val ingredient: RecipeIngredientEntity,
    @Relation(parentColumn = "foodItemId", entityColumn = "id")
    val foodItem: FoodItemEntity,
)

/**
 * Room relation: a [RecipeEntity] joined with its ordered ingredient rows, each paired with the
 * [FoodItemEntity] they reference (spec "Recipe CRUD with Ingredients"). Returned by
 * [RecipeDao.observeRecipeWithIngredients]; mapped to the domain-facing
 * [com.healthypantry.feature.recipes.domain.model.RecipeWithIngredients] by
 * `feature/recipes/data/repo` — never returned directly from a repository.
 */
data class RecipeWithIngredientsEntity(
    @Embedded val recipe: RecipeEntity,
    @Relation(
        entity = RecipeIngredientEntity::class,
        parentColumn = "id",
        entityColumn = "recipeId",
    )
    val ingredients: List<RecipeIngredientWithFoodItemEntity>,
)
