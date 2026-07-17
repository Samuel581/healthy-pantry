package com.healthypantry.feature.planning.domain.model

/**
 * Total macros for a resolved quantity of food (spec "Item-to-Day Macro Rollup"): a recipe
 * scaled to a servings count, a single quick-add
 * [FoodItem][com.healthypantry.feature.pantry.domain.model.FoodItem] quantity, or a full day's
 * rollup across every entry. Never persisted — always derived on read from each referenced
 * `FoodItem`'s per-canonical-unit macro fields (design.md: "Macros: ComputeMacroTotalsUseCase ...
 * derived (never stored)").
 *
 * [isComplete] is `false` when at least one contributing `FoodItem` had an unknown (`null`)
 * macro field (spec "Item-to-Day Macro Rollup" -> "Missing macro data on an item"): that item's
 * unknown macro still contributes `0.0` to the numeric totals below (so the numbers stay
 * additive/well-defined), but callers MUST check [isComplete] before presenting the total as
 * accurate, rather than silently reporting a zero-inflated number as if it were exact.
 */
data class MacroTotals(
    val calories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val isComplete: Boolean = true,
) {
    operator fun plus(other: MacroTotals): MacroTotals = MacroTotals(
        calories = calories + other.calories,
        proteinGrams = proteinGrams + other.proteinGrams,
        carbsGrams = carbsGrams + other.carbsGrams,
        fatGrams = fatGrams + other.fatGrams,
        isComplete = isComplete && other.isComplete,
    )

    companion object {
        val ZERO = MacroTotals(calories = 0.0, proteinGrams = 0.0, carbsGrams = 0.0, fatGrams = 0.0, isComplete = true)
    }
}
