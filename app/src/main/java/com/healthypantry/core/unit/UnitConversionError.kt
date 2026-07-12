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
}
