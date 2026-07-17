package com.healthypantry.feature.pantry.ui.vm

import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.nutrition.domain.model.NutritionResult
import com.healthypantry.feature.nutrition.domain.model.NutritionSource
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Spec: "Barcode Scan via Open Food Facts", "Manual Entry with USDA FDC Fallback", and the
 * "Manual macro override always wins" rule (nutrition-lookup domain,
 * openspec/changes/pantry-tracker/specs/nutrition-lookup/spec.md).
 *
 * Pure reducer tests for [ItemFormUiState.prefillFrom]/[ItemFormUiState.toFoodItem] - no
 * ViewModel/coroutines involved (see [ItemFormViewModelTest] for the async wiring).
 */
class ItemFormUiStateTest {

    private fun bananaResult(
        calories: Double? = 89.0,
        protein: Double? = 1.1,
        carbs: Double? = 23.0,
        fat: Double? = 0.3,
    ) = NutritionResult(
        name = "Banana, raw",
        caloriesPer100 = calories,
        proteinGramsPer100 = protein,
        carbsGramsPer100 = carbs,
        fatGramsPer100 = fat,
        source = NutritionSource.USDA_FOOD_DATA_CENTRAL,
        externalId = "12345",
    )

    @Test
    fun `prefillFrom fills every untouched field from the lookup result`() {
        val state = ItemFormUiState().prefillFrom(
            bananaResult(),
            source = FoodItemSource.BARCODE,
            barcode = "0123456789",
        )

        assertEquals("Banana, raw", state.name)
        assertEquals(MeasurementUnit.GRAM, state.canonicalUnit)
        assertEquals("0.89", state.caloriesPerUnit)
        assertEquals("0.011", state.proteinGramsPerUnit)
        assertEquals("0.23", state.carbsGramsPerUnit)
        assertEquals("0.003", state.fatGramsPerUnit)
        assertEquals(FoodItemSource.BARCODE, state.source)
        assertEquals("0123456789", state.barcode)
        assertEquals("12345", state.externalSourceId)
    }

    @Test
    fun `prefillFrom does not overwrite a field the user already touched`() {
        val edited = ItemFormUiState(
            caloriesPerUnit = "5.0",
            touchedFields = setOf(ItemFormField.CALORIES),
        )

        val prefilled = edited.prefillFrom(bananaResult(), source = FoodItemSource.BARCODE)

        assertEquals("5.0", prefilled.caloriesPerUnit)
        // untouched fields still prefill normally
        assertEquals("Banana, raw", prefilled.name)
        assertEquals("0.011", prefilled.proteinGramsPerUnit)
    }

    @Test
    fun `a manual edit made after a prefill wins over a later prefill`() {
        val prefilledOnce = ItemFormUiState().prefillFrom(bananaResult(), source = FoodItemSource.BARCODE)
        val userEdited = prefilledOnce.copy(
            caloriesPerUnit = "1.0",
            touchedFields = prefilledOnce.touchedFields + ItemFormField.CALORIES,
        )

        val prefilledAgain = userEdited.prefillFrom(bananaResult(calories = 999.0), source = FoodItemSource.BARCODE)

        assertEquals("1.0", prefilledAgain.caloriesPerUnit)
    }

    @Test
    fun `a null macro from the lookup result leaves the field as-is instead of coercing to zero`() {
        val state = ItemFormUiState(fatGramsPerUnit = "").prefillFrom(
            bananaResult(fat = null),
            source = FoodItemSource.BARCODE,
        )

        assertEquals("", state.fatGramsPerUnit)
    }

    @Test
    fun `prefillFrom does not overwrite an already-selected canonical unit`() {
        val edited = ItemFormUiState(
            canonicalUnit = MeasurementUnit.PIECE,
            touchedFields = setOf(ItemFormField.UNIT),
        )

        val prefilled = edited.prefillFrom(bananaResult(), source = FoodItemSource.BARCODE)

        assertEquals(MeasurementUnit.PIECE, prefilled.canonicalUnit)
    }

    @Test
    fun `toFoodItem parses macro strings and treats blank or unparseable input as unknown, not zero`() {
        // Spec: "Item-to-Day Macro Rollup" -> "Missing macro data on an item" — a blank/unparseable
        // field must save as null ("unknown"), never coerced to a verified 0.0.
        val state = ItemFormUiState(
            name = "Rice",
            canonicalUnit = MeasurementUnit.GRAM,
            caloriesPerUnit = "1.3",
            proteinGramsPerUnit = "",
            carbsGramsPerUnit = "0.28",
            fatGramsPerUnit = "not-a-number",
        )

        val item = state.toFoodItem(id = 7L)

        assertEquals(7L, item.id)
        assertEquals("Rice", item.name)
        assertEquals(FoodItemSource.MANUAL, item.source)
        assertEquals(1.3, item.caloriesPerUnit)
        assertNull(item.proteinGramsPerUnit)
        assertEquals(0.28, item.carbsGramsPerUnit)
        assertNull(item.fatGramsPerUnit)
    }

    @Test
    fun `toFoodItem parses an explicit zero as a known 0_0, distinct from a blank field`() {
        // Given the user actually typed 0 for protein (a real "verified zero"), not left it blank
        val state = ItemFormUiState(
            name = "Egg white",
            canonicalUnit = MeasurementUnit.GRAM,
            caloriesPerUnit = "0.52",
            proteinGramsPerUnit = "0.11",
            carbsGramsPerUnit = "0",
            fatGramsPerUnit = "0.0",
        )

        val item = state.toFoodItem(id = 3L)

        // Then it saves as a real, known 0.0 — not null
        assertEquals(0.0, item.carbsGramsPerUnit)
        assertEquals(0.0, item.fatGramsPerUnit)
    }
}
