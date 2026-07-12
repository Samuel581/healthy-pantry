package com.healthypantry.feature.planning.domain.model

/**
 * Domain-facing representation of one day/meal-slot planning entry (see spec "Weekly Plan
 * Assignment and Quick-Add"). Exactly one of [recipeId] (with [servings]) or [foodItemId] (with
 * [quantity]) is set, matching [type]:
 * - [PlanEntryType.RECIPE]: [recipeId] + [servings] set, [foodItemId]/[quantity] null.
 * - [PlanEntryType.ITEM]: [foodItemId] + [quantity] set, [recipeId]/[servings] null.
 *
 * [eaten]/[eatenAt] track the "mark eaten" action that moves a planned quantity from projected
 * stock into an actual-stock decrement (spec "Projected vs Actual Stock").
 */
data class PlanEntry(
    val id: Long = 0,
    val dateEpochDay: Long,
    val mealSlot: MealSlot,
    val type: PlanEntryType,
    val recipeId: Long? = null,
    val foodItemId: Long? = null,
    val quantity: Double? = null,
    val servings: Double? = null,
    val eaten: Boolean = false,
    val eatenAt: Long? = null,
)
