package com.healthypantry.feature.planning.domain.usecase

import com.healthypantry.core.common.Result
import com.healthypantry.core.common.map
import com.healthypantry.core.unit.ConversionFactor
import com.healthypantry.core.unit.UnitConversionError
import com.healthypantry.core.unit.UnitConverter
import com.healthypantry.feature.recipes.domain.model.RecipeIngredientDetail

/**
 * Converts one [RecipeIngredientDetail]'s quantity (expressed in [RecipeIngredientDetail]'s own
 * ingredient unit) into its [RecipeIngredientDetail.foodItem]'s canonical unit, then scales the
 * result by the ratio between [requestedServings] — how many servings are actually wanted for one
 * plan-entry slot — and [recipeServings] — the recipe's own default batch size
 * ([com.healthypantry.feature.recipes.domain.model.Recipe.servings]). A [requestedServings] equal
 * to [recipeServings] leaves the converted quantity unscaled.
 *
 * [recipeServings] has no domain/DB validation preventing a non-positive value (see
 * [UnitConversionError.InvalidRecipeServings]); this function guards the division itself instead
 * of leaving every call site to duplicate the check, since `requestedServings / recipeServings`
 * would otherwise silently produce `Infinity`/`NaN` for `recipeServings <= 0`.
 *
 * Shared by [ComputeMacroTotalsUseCase], [ComputeWeeklyNeedsUseCase], and
 * `MarkPlanEntryEatenUseCase` — every one of them needs to turn "this many servings of this
 * recipe" into "this many canonical units of each referenced FoodItem", so the scale+convert step
 * (including the guarded ratio) is factored out once here instead of tripled across three
 * use-cases.
 */
internal fun UnitConverter.convertScaledIngredient(
    detail: RecipeIngredientDetail,
    requestedServings: Double,
    recipeServings: Int,
    registeredFactors: List<ConversionFactor>,
): Result<Double, UnitConversionError> {
    if (recipeServings <= 0) {
        return Result.failure(UnitConversionError.InvalidRecipeServings(recipeServings))
    }

    val servingsRatio = requestedServings / recipeServings
    return convert(
        quantity = detail.ingredient.quantity,
        fromUnit = detail.ingredient.unit,
        toUnit = detail.foodItem.canonicalUnit,
        foodItemLabel = detail.foodItem.name,
        registeredFactors = registeredFactors,
    ).map { convertedQuantity -> convertedQuantity * servingsRatio }
}
