package com.healthypantry.feature.recipes.domain.model

import com.healthypantry.feature.pantry.domain.model.FoodItem

/**
 * A [Recipe] together with its ordered [RecipeIngredient] lines, each paired with the
 * [FoodItem] it references — so macro-rollup domain use-cases (PR8) can compute a recipe's
 * total macros without a second query per ingredient (see spec "Recipe CRUD with Ingredients",
 * "Item-to-Day Macro Rollup"). [ingredients] preserves entered order ([RecipeIngredient.sortOrder]).
 */
data class RecipeWithIngredients(
    val recipe: Recipe,
    val ingredients: List<RecipeIngredientDetail>,
)

/** One [RecipeWithIngredients] ingredient line paired with the [FoodItem] it references. */
data class RecipeIngredientDetail(
    val ingredient: RecipeIngredient,
    val foodItem: FoodItem,
)
