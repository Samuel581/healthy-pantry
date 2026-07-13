package com.healthypantry.feature.planning.domain.model

/**
 * Total macros for a resolved quantity of food (spec "Item-to-Day Macro Rollup"): a recipe
 * scaled to a servings count, a single quick-add
 * [FoodItem][com.healthypantry.feature.pantry.domain.model.FoodItem] quantity, or a full day's
 * rollup across every entry. Never persisted — always derived on read from each referenced
 * `FoodItem`'s per-canonical-unit macro fields (design.md: "Macros: ComputeMacroTotalsUseCase ...
 * derived (never stored)").
 */
data class MacroTotals(
    val calories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
) {
    operator fun plus(other: MacroTotals): MacroTotals = MacroTotals(
        calories = calories + other.calories,
        proteinGrams = proteinGrams + other.proteinGrams,
        carbsGrams = carbsGrams + other.carbsGrams,
        fatGrams = fatGrams + other.fatGrams,
    )

    companion object {
        val ZERO = MacroTotals(calories = 0.0, proteinGrams = 0.0, carbsGrams = 0.0, fatGrams = 0.0)
    }
}
