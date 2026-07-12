package com.healthypantry.core.unit

import com.healthypantry.core.common.Result
import javax.inject.Inject

/**
 * Converts a quantity between two [MeasurementUnit]s for a specific food item, using only the
 * conversion factors registered for that item — never a global or hardcoded table.
 *
 * Spec: "Unit Conversion Correctness"
 * (openspec/changes/pantry-tracker/specs/pantry-stock/spec.md). Conversion factors come from
 * the item's own `UnitConversion` rows (represented here as [ConversionFactor], decoupled from
 * Room until PR3 wires persistence); if no factor — direct or inverse — connects the requested
 * units, this returns [Result.Failure] with [UnitConversionError.UnresolvedConversion] instead
 * of guessing a 1:1 conversion.
 *
 * `@Inject constructor()`: PR2 never needed Hilt to construct this (only plain-JUnit tests
 * called `UnitConverter()` directly). PR8's `ComputeMacroTotalsUseCase`/`ComputeWeeklyNeedsUseCase`/
 * `MarkPlanEntryEatenUseCase` are the first production `@Inject` sites for this class, so without
 * this annotation Hilt's compile-time graph validation would fail on those use-cases.
 */
class UnitConverter @Inject constructor() {

    fun convert(
        quantity: Double,
        fromUnit: MeasurementUnit,
        toUnit: MeasurementUnit,
        foodItemLabel: String,
        registeredFactors: List<ConversionFactor>,
    ): Result<Double, UnitConversionError> {
        if (fromUnit == toUnit) {
            return Result.success(quantity)
        }

        val directFactor = registeredFactors.firstOrNull { it.fromUnit == fromUnit && it.toUnit == toUnit }
        if (directFactor != null) {
            return Result.success(quantity * directFactor.factor)
        }

        val inverseFactor = registeredFactors.firstOrNull { it.fromUnit == toUnit && it.toUnit == fromUnit }
        if (inverseFactor != null) {
            return Result.success(quantity / inverseFactor.factor)
        }

        return Result.failure(
            UnitConversionError.UnresolvedConversion(
                foodItemLabel = foodItemLabel,
                fromUnit = fromUnit,
                toUnit = toUnit,
            ),
        )
    }
}
