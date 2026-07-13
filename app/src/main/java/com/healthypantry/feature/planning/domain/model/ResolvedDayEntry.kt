package com.healthypantry.feature.planning.domain.model

import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.recipes.domain.model.RecipeWithIngredients

/**
 * One day's [PlanEntry] with its referenced [RecipeWithIngredients] or [FoodItem] already
 * resolved by the caller (spec "Day total across recipe and quick-add"). Kept as its own type
 * (rather than passing [PlanEntry] + a lookup map) so
 * [com.healthypantry.feature.planning.domain.usecase.ComputeMacroTotalsUseCase.computeDayTotal]
 * stays a pure function with no repository/Room dependency — the caller does all resolution.
 */
sealed interface ResolvedDayEntry {

    /** A recipe assigned to this day/slot, not yet eaten or already eaten — caller decides which to include. */
    data class RecipeEntry(
        val recipeWithIngredients: RecipeWithIngredients,
        val requestedServings: Double,
    ) : ResolvedDayEntry

    /** A single pantry item quick-added to this day/slot. [quantity] is already in [foodItem]'s canonical unit. */
    data class QuickAddEntry(
        val foodItem: FoodItem,
        val quantity: Double,
    ) : ResolvedDayEntry
}
