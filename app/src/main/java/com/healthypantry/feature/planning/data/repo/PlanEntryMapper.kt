package com.healthypantry.feature.planning.data.repo

import com.healthypantry.feature.planning.data.entity.PlanEntryEntity
import com.healthypantry.feature.planning.domain.model.PlanEntry

internal fun PlanEntryEntity.toDomain(): PlanEntry = PlanEntry(
    id = id,
    dateEpochDay = dateEpochDay,
    mealSlot = mealSlot,
    type = type,
    recipeId = recipeId,
    foodItemId = foodItemId,
    quantity = quantity,
    servings = servings,
    eaten = eaten,
    eatenAt = eatenAt,
)

internal fun PlanEntry.toEntity(): PlanEntryEntity = PlanEntryEntity(
    id = id,
    dateEpochDay = dateEpochDay,
    mealSlot = mealSlot,
    type = type,
    recipeId = recipeId,
    foodItemId = foodItemId,
    quantity = quantity,
    servings = servings,
    eaten = eaten,
    eatenAt = eatenAt,
)
