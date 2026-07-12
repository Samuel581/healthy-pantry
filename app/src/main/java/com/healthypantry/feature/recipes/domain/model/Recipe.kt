package com.healthypantry.feature.recipes.domain.model

import java.time.Instant

/**
 * Domain-facing representation of a recipe: an ordered list of ingredients, each referencing a
 * pantry item + quantity + unit (see spec "Recipe CRUD with Ingredients"). The ordered
 * ingredient list itself lives in [RecipeIngredient.sortOrder], not on this model.
 */
data class Recipe(
    val id: Long = 0,
    val name: String,
    val servings: Int,
    val notes: String? = null,
    val createdAt: Instant,
)
