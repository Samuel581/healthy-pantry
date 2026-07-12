package com.healthypantry.feature.planning.domain.usecase

import com.healthypantry.core.common.Result
import com.healthypantry.core.unit.ConversionFactor
import com.healthypantry.core.unit.UnitConversionError
import com.healthypantry.core.unit.UnitConverter
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.planning.domain.model.MacroTotals
import com.healthypantry.feature.planning.domain.model.ResolvedDayEntry
import com.healthypantry.feature.recipes.domain.model.RecipeWithIngredients
import javax.inject.Inject

/**
 * Spec: "Item-to-Day Macro Rollup", "Recipe total from ingredients", "Day total across recipe and
 * quick-add" (sdd/pantry-tracker/spec).
 *
 * Pure JVM domain logic, matching the "pure domain use-cases... TDD-testable in the JVM"
 * convention established by
 * [ComputeProjectedStockUseCase][com.healthypantry.feature.pantry.domain.usecase.ComputeProjectedStockUseCase]:
 * every method accepts already-resolved [RecipeWithIngredients]/[FoodItem] data plus each
 * referenced item's registered [ConversionFactor]s, and performs no repository or Room access.
 *
 * [FoodItem] macro fields are expressed per ONE unit of [FoodItem.canonicalUnit] — NOT per-100g
 * (see [FoodItem] KDoc) — so every quantity is converted to that unit before being multiplied by
 * them.
 */
class ComputeMacroTotalsUseCase @Inject constructor(
    private val unitConverter: UnitConverter,
) {

    /** Macros for [quantity] canonical units of a single quick-add [foodItem] — no conversion needed. */
    fun computeForQuickAdd(foodItem: FoodItem, quantity: Double): MacroTotals = MacroTotals(
        calories = foodItem.caloriesPerUnit * quantity,
        proteinGrams = foodItem.proteinGramsPerUnit * quantity,
        carbsGrams = foodItem.carbsGramsPerUnit * quantity,
        fatGrams = foodItem.fatGramsPerUnit * quantity,
    )

    /**
     * Macros for [recipeWithIngredients] scaled to [requestedServings] (spec "Recipe total from
     * ingredients"). Each ingredient's quantity+unit is converted to its own FoodItem's canonical
     * unit using [conversionFactorsByFoodItemId] (that item's registered factors, keyed by
     * FoodItemId), then scaled by `requestedServings / recipe.servings` — the ratio between how
     * many servings are actually wanted and the recipe's own default batch size.
     *
     * Surfaces the first [UnitConversionError] encountered instead of silently treating an
     * unresolved conversion as zero (spec "Unit Conversion Correctness").
     */
    fun computeForRecipe(
        recipeWithIngredients: RecipeWithIngredients,
        requestedServings: Double,
        conversionFactorsByFoodItemId: Map<Long, List<ConversionFactor>>,
    ): Result<MacroTotals, UnitConversionError> {
        val servingsRatio = requestedServings / recipeWithIngredients.recipe.servings
        var total = MacroTotals.ZERO

        for (detail in recipeWithIngredients.ingredients) {
            val factors = conversionFactorsByFoodItemId[detail.foodItem.id].orEmpty()
            val converted = unitConverter.convertScaledIngredient(detail, servingsRatio, factors)
            when (converted) {
                is Result.Failure -> return converted
                is Result.Success -> total += computeForQuickAdd(detail.foodItem, converted.value)
            }
        }

        return Result.success(total)
    }

    /**
     * A day's total macro rollup across every resolved [ResolvedDayEntry] (spec "Day total across
     * recipe and quick-add") — always computed on read, never persisted as its own row.
     */
    fun computeDayTotal(
        entries: List<ResolvedDayEntry>,
        conversionFactorsByFoodItemId: Map<Long, List<ConversionFactor>>,
    ): Result<MacroTotals, UnitConversionError> {
        var total = MacroTotals.ZERO

        for (entry in entries) {
            val entryTotal: Result<MacroTotals, UnitConversionError> = when (entry) {
                is ResolvedDayEntry.QuickAddEntry ->
                    Result.success(computeForQuickAdd(entry.foodItem, entry.quantity))
                is ResolvedDayEntry.RecipeEntry ->
                    computeForRecipe(entry.recipeWithIngredients, entry.requestedServings, conversionFactorsByFoodItemId)
            }
            when (entryTotal) {
                is Result.Failure -> return entryTotal
                is Result.Success -> total += entryTotal.value
            }
        }

        return Result.success(total)
    }
}
