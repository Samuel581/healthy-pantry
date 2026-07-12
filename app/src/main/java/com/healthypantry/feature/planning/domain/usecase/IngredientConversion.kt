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
 * result by [servingsRatio] — the ratio between how many servings are actually wanted for one
 * plan-entry slot and the recipe's own default batch size
 * ([com.healthypantry.feature.recipes.domain.model.Recipe.servings]). A [servingsRatio] of `1.0`
 * leaves the converted quantity unscaled.
 *
 * Shared by [ComputeMacroTotalsUseCase], [ComputeWeeklyNeedsUseCase], and
 * `MarkPlanEntryEatenUseCase` — every one of them needs to turn "this many servings of this
 * recipe" into "this many canonical units of each referenced FoodItem", so the scale+convert step
 * is factored out once here instead of tripled across three use-cases.
 */
internal fun UnitConverter.convertScaledIngredient(
    detail: RecipeIngredientDetail,
    servingsRatio: Double,
    registeredFactors: List<ConversionFactor>,
): Result<Double, UnitConversionError> = convert(
    quantity = detail.ingredient.quantity,
    fromUnit = detail.ingredient.unit,
    toUnit = detail.foodItem.canonicalUnit,
    foodItemLabel = detail.foodItem.name,
    registeredFactors = registeredFactors,
).map { convertedQuantity -> convertedQuantity * servingsRatio }
