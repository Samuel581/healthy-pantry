package com.healthypantry.core.unit

import com.healthypantry.core.common.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec: Unit Conversion Correctness
 * (openspec/changes/pantry-tracker/specs/pantry-stock/spec.md)
 *
 * Conversion factors are registered per [FoodItem][com.healthypantry.core.unit.ConversionFactor]
 * (never a global table), so the same [UnitConverter] instance must respect whatever factors
 * are passed in for a given item and MUST NOT guess or default to a 1:1 conversion.
 */
class UnitConverterTest {

    private val converter = UnitConverter()

    @Test
    fun `converts a recipe quantity to canonical unit using the item's registered factor`() {
        // Given: "Rice" has canonical unit grams and a conversion 1 cup = 185g
        val riceFactors = listOf(
            ConversionFactor(fromUnit = MeasurementUnit.CUP, toUnit = MeasurementUnit.GRAM, factor = 185.0),
        )

        // When: a recipe ingredient specifies 2 cups of rice
        val result = converter.convert(
            quantity = 2.0,
            fromUnit = MeasurementUnit.CUP,
            toUnit = MeasurementUnit.GRAM,
            foodItemLabel = "Rice",
            registeredFactors = riceFactors,
        )

        // Then: the system computes 370g against the "Rice" stock
        assertTrue(result is Result.Success)
        assertEquals(370.0, (result as Result.Success).value, 0.0001)
    }

    @Test
    fun `surfaces an unresolved-unit error when no conversion factor is registered for the requested unit pair`() {
        // Given: "Olive oil" has canonical unit ml with no registered conversion to "tablespoon"
        val oliveOilFactors = emptyList<ConversionFactor>()

        // When: a recipe ingredient specifies quantity in tablespoons
        val result = converter.convert(
            quantity = 3.0,
            fromUnit = MeasurementUnit.TABLESPOON,
            toUnit = MeasurementUnit.MILLILITER,
            foodItemLabel = "Olive oil",
            registeredFactors = oliveOilFactors,
        )

        // Then: the system surfaces an unresolved-unit error rather than guessing a 1:1 conversion
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertEquals(
            UnitConversionError.UnresolvedConversion(
                foodItemLabel = "Olive oil",
                fromUnit = MeasurementUnit.TABLESPOON,
                toUnit = MeasurementUnit.MILLILITER,
            ),
            error,
        )
    }

    @Test
    fun `resolves a conversion using the inverse of a registered factor`() {
        // Given: "Flour" registers its factor in one direction only (1 cup = 120g)
        val flourFactors = listOf(
            ConversionFactor(fromUnit = MeasurementUnit.CUP, toUnit = MeasurementUnit.GRAM, factor = 120.0),
        )

        // When: stock is expressed back in the opposite direction (grams to cups)
        val result = converter.convert(
            quantity = 240.0,
            fromUnit = MeasurementUnit.GRAM,
            toUnit = MeasurementUnit.CUP,
            foodItemLabel = "Flour",
            registeredFactors = flourFactors,
        )

        // Then: it derives the inverse (240g / 120 = 2 cups) instead of failing
        assertTrue(result is Result.Success)
        assertEquals(2.0, (result as Result.Success).value, 0.0001)
    }

    @Test
    fun `returns the same quantity unchanged when converting a unit to itself`() {
        val result = converter.convert(
            quantity = 42.0,
            fromUnit = MeasurementUnit.GRAM,
            toUnit = MeasurementUnit.GRAM,
            foodItemLabel = "Chicken breast",
            registeredFactors = emptyList(),
        )

        assertTrue(result is Result.Success)
        assertEquals(42.0, (result as Result.Success).value, 0.0001)
    }
}
