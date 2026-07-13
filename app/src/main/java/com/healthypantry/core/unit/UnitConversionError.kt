package com.healthypantry.core.unit

/** Errors [UnitConverter] can surface. Kept as a sealed type so callers can exhaustively handle it. */
sealed interface UnitConversionError {

    /**
     * No registered [ConversionFactor] (in either direction) connects [fromUnit] to [toUnit]
     * for the food item identified by [foodItemLabel]. Per spec "Unit Conversion Correctness",
     * this MUST be surfaced to the user rather than silently defaulting to a 1:1 conversion.
     */
    data class UnresolvedConversion(
        val foodItemLabel: String,
        val fromUnit: MeasurementUnit,
        val toUnit: MeasurementUnit,
    ) : UnitConversionError

    /**
     * A recipe's [com.healthypantry.feature.recipes.domain.model.Recipe.servings] is `<= 0`, so a
     * servings ratio (`requestedServings / recipeServings`) cannot be computed without producing
     * `Infinity`/`NaN`. Surfaced instead of dividing, since `Recipe.servings` has no domain/DB
     * validation preventing a non-positive value.
     */
    data class InvalidRecipeServings(
        val recipeServings: Int,
    ) : UnitConversionError
}
