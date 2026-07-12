package com.healthypantry.feature.planning.domain.usecase

import com.healthypantry.core.common.Result
import com.healthypantry.core.unit.ConversionFactor
import com.healthypantry.core.unit.UnitConversionError
import com.healthypantry.core.unit.UnitConverter
import com.healthypantry.feature.planning.domain.model.PlanEntry
import com.healthypantry.feature.planning.domain.model.PlanEntryType
import com.healthypantry.feature.recipes.domain.model.RecipeWithIngredients
import javax.inject.Inject

/**
 * Spec: "Weekly Plan Assignment and Quick-Add", "Projected vs Actual Stock" (sdd/pantry-tracker/spec).
 *
 * Produces the `FoodItemId -> committed quantity` map that
 * [ComputeProjectedStockUseCase.observeProjected][com.healthypantry.feature.pantry.domain.usecase.ComputeProjectedStockUseCase.observeProjected]
 * already accepts as its `committedQuantities` parameter — closing the gap that use-case's own
 * KDoc flagged ("PR8's mark-eaten use-case will compute that input from real PlanEntry rows once
 * they exist"). Wiring this output into a ViewModel is deliberately left to Phase 9 (this PR only
 * adds the use-case itself).
 *
 * Pure JVM: accepts already-resolved [weekEntries] (from
 * `PlanEntryRepository.observeWeek`) plus each referenced recipe's [RecipeWithIngredients] (keyed
 * by `Recipe.id`) and each referenced FoodItem's registered [ConversionFactor]s — no repository
 * access happens inside this use-case.
 *
 * Only entries with [PlanEntry.eaten] == `false` contribute: an eaten entry's quantity is already
 * reflected in actual stock via a real decrement (`MarkPlanEntryEatenUseCase`), not projected
 * deficit — design.md: "projected = actual − planned-not-eaten".
 */
class ComputeWeeklyNeedsUseCase @Inject constructor(
    private val unitConverter: UnitConverter,
) {

    fun compute(
        weekEntries: List<PlanEntry>,
        recipesById: Map<Long, RecipeWithIngredients>,
        conversionFactorsByFoodItemId: Map<Long, List<ConversionFactor>>,
    ): Result<Map<Long, Double>, UnitConversionError> {
        val needs = mutableMapOf<Long, Double>()

        for (entry in weekEntries) {
            if (entry.eaten) continue

            when (entry.type) {
                PlanEntryType.ITEM -> {
                    val foodItemId = entry.foodItemId ?: continue
                    val quantity = entry.quantity ?: continue
                    needs[foodItemId] = (needs[foodItemId] ?: 0.0) + quantity
                }

                PlanEntryType.RECIPE -> {
                    val recipeId = entry.recipeId ?: continue
                    val requestedServings = entry.servings ?: continue
                    // A recipe missing from the caller's resolved map (e.g. deleted between the
                    // caller's lookup and this call) contributes nothing rather than failing the
                    // whole week's computation — the caller is expected to resolve every recipeId
                    // present in weekEntries, so this is a defensive no-op, not the normal path.
                    val recipeWithIngredients = recipesById[recipeId] ?: continue
                    val servingsRatio = requestedServings / recipeWithIngredients.recipe.servings

                    for (detail in recipeWithIngredients.ingredients) {
                        val factors = conversionFactorsByFoodItemId[detail.foodItem.id].orEmpty()
                        val converted = unitConverter.convertScaledIngredient(detail, servingsRatio, factors)
                        when (converted) {
                            is Result.Failure -> return converted
                            is Result.Success -> {
                                val foodItemId = detail.foodItem.id
                                needs[foodItemId] = (needs[foodItemId] ?: 0.0) + converted.value
                            }
                        }
                    }
                }
            }
        }

        return Result.success(needs)
    }
}
