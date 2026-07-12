package com.healthypantry.feature.recipes.data.repo

import com.healthypantry.feature.pantry.data.repo.toDomain
import com.healthypantry.feature.recipes.data.dao.RecipeIngredientWithFoodItemRelation
import com.healthypantry.feature.recipes.data.dao.RecipeWithIngredientsRelation
import com.healthypantry.feature.recipes.data.entity.RecipeEntity
import com.healthypantry.feature.recipes.data.entity.RecipeIngredientEntity
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.domain.model.RecipeIngredient
import com.healthypantry.feature.recipes.domain.model.RecipeIngredientDetail
import com.healthypantry.feature.recipes.domain.model.RecipeWithIngredients

internal fun RecipeEntity.toDomain(): Recipe = Recipe(
    id = id,
    name = name,
    servings = servings,
    notes = notes,
    createdAt = createdAt,
)

internal fun Recipe.toEntity(): RecipeEntity = RecipeEntity(
    id = id,
    name = name,
    servings = servings,
    notes = notes,
    createdAt = createdAt,
)

internal fun RecipeIngredientEntity.toDomain(): RecipeIngredient = RecipeIngredient(
    id = id,
    recipeId = recipeId,
    foodItemId = foodItemId,
    quantity = quantity,
    unit = unit,
    sortOrder = sortOrder,
)

internal fun RecipeIngredient.toEntity(): RecipeIngredientEntity = RecipeIngredientEntity(
    id = id,
    recipeId = recipeId,
    foodItemId = foodItemId,
    quantity = quantity,
    unit = unit,
    sortOrder = sortOrder,
)

internal fun RecipeIngredientWithFoodItemRelation.toDomain(): RecipeIngredientDetail =
    RecipeIngredientDetail(
        ingredient = ingredient.toDomain(),
        foodItem = foodItem.toDomain(),
    )

internal fun RecipeWithIngredientsRelation.toDomain(): RecipeWithIngredients = RecipeWithIngredients(
    recipe = recipe.toDomain(),
    // Room's @Relation has no orderBy — it does not guarantee row order, so the entered-order
    // contract (RecipeWithIngredients.ingredients KDoc) must be enforced here, not left to
    // whatever order the underlying query happens to return.
    ingredients = ingredients.map { it.toDomain() }.sortedBy { it.ingredient.sortOrder },
)
