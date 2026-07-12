package com.healthypantry.core.unit

/**
 * A single, per-[FoodItem][com.healthypantry.feature.pantry.domain.model] registered
 * conversion between two [MeasurementUnit]s (e.g. "1 cup = 185g" for rice).
 *
 * Multiplying a quantity expressed in [fromUnit] by [factor] yields the equivalent quantity
 * in [toUnit]. These factors are never shared across food items — see spec "Unit Conversion
 * Correctness": the same unit pair can have a different factor per item (a cup of rice is not
 * the same mass as a cup of flour).
 */
data class ConversionFactor(
    val fromUnit: MeasurementUnit,
    val toUnit: MeasurementUnit,
    val factor: Double,
)
