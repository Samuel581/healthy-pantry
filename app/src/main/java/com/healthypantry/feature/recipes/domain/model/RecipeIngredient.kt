package com.healthypantry.feature.recipes.domain.model

import com.healthypantry.core.unit.MeasurementUnit

/**
 * Domain-facing representation of one ingredient line of a [Recipe] (see spec "Recipe CRUD with
 * Ingredients"): a quantity+unit of a referenced pantry item, placed at [sortOrder] within the
 * recipe's entered ingredient order. [quantity] is expressed in [unit], not necessarily the
 * referenced food item's canonical unit — conversion is a macro-rollup domain concern (PR8).
 */
data class RecipeIngredient(
    val id: Long = 0,
    val recipeId: Long,
    val foodItemId: Long,
    val quantity: Double,
    val unit: MeasurementUnit,
    val sortOrder: Int,
)
