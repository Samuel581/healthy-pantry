package com.healthypantry.feature.pantry.ui.vm

import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.nutrition.domain.model.NutritionResult
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource

/** Which [ItemFormUiState] field the user has directly edited. */
enum class ItemFormField {
    NAME, UNIT, CALORIES, PROTEIN, CARBS, FAT
}

private const val MACRO_LOOKUP_BASIS_GRAMS = 100.0

/**
 * Editable state for [ItemFormViewModel]/`ItemFormScreen`. Macro fields are kept as raw [String]
 * (not [Double]) so an `OutlinedTextField` can bind to them directly while the user is mid-edit
 * (e.g. a trailing "." or an empty field); they are only parsed at save time, by [toFoodItem].
 *
 * [touchedFields] is the single source of truth for spec scenario "Manual macro override always
 * wins" (nutrition-lookup domain): [prefillFrom] only ever writes into a field the user hasn't
 * already edited, so a lookup that runs before or after a manual edit can never clobber it.
 */
data class ItemFormUiState(
    val name: String = "",
    val canonicalUnit: MeasurementUnit = MeasurementUnit.GRAM,
    val caloriesPerUnit: String = "",
    val proteinGramsPerUnit: String = "",
    val carbsGramsPerUnit: String = "",
    val fatGramsPerUnit: String = "",
    val source: FoodItemSource = FoodItemSource.MANUAL,
    val barcode: String? = null,
    val externalSourceId: String? = null,
    val touchedFields: Set<ItemFormField> = emptySet(),
    val usdaQuery: String = "",
    val isLookingUp: Boolean = false,
    val lookupError: String? = null,
)

/**
 * Pre-fills blank/untouched fields from a barcode or USDA lookup [result]. A `null` macro on
 * [result] means "not reported" (see [NutritionResult] KDoc), so it leaves the corresponding
 * field exactly as it was rather than coercing an unknown macro to zero.
 *
 * [result]'s macros are per 100 g/ml; lookup-sourced items default their canonical unit to
 * [MeasurementUnit.GRAM] (unless the user already picked a different one) so those per-100 values
 * convert directly into this form's per-canonical-unit fields.
 */
fun ItemFormUiState.prefillFrom(
    result: NutritionResult,
    source: FoodItemSource,
    barcode: String? = this.barcode,
): ItemFormUiState = copy(
    name = if (ItemFormField.NAME in touchedFields) name else result.name,
    canonicalUnit = if (ItemFormField.UNIT in touchedFields) canonicalUnit else MeasurementUnit.GRAM,
    caloriesPerUnit = prefilledMacro(ItemFormField.CALORIES, caloriesPerUnit, result.caloriesPer100),
    proteinGramsPerUnit = prefilledMacro(ItemFormField.PROTEIN, proteinGramsPerUnit, result.proteinGramsPer100),
    carbsGramsPerUnit = prefilledMacro(ItemFormField.CARBS, carbsGramsPerUnit, result.carbsGramsPer100),
    fatGramsPerUnit = prefilledMacro(ItemFormField.FAT, fatGramsPerUnit, result.fatGramsPer100),
    source = source,
    barcode = barcode,
    externalSourceId = result.externalId,
)

private fun ItemFormUiState.prefilledMacro(field: ItemFormField, current: String, per100: Double?): String {
    if (field in touchedFields || per100 == null) return current
    return (per100 / MACRO_LOOKUP_BASIS_GRAMS).toString()
}

/**
 * Builds the [FoodItem] to persist from this form state. [FoodItem]'s macro fields are
 * non-nullable [Double]s (no `manualOverride`/nullable-macro columns exist on the entity), so a
 * blank or unparseable macro field saves as `0.0`.
 */
fun ItemFormUiState.toFoodItem(id: Long = 0L): FoodItem = FoodItem(
    id = id,
    name = name,
    canonicalUnit = canonicalUnit,
    source = source,
    caloriesPerUnit = caloriesPerUnit.toDoubleOrNull() ?: 0.0,
    proteinGramsPerUnit = proteinGramsPerUnit.toDoubleOrNull() ?: 0.0,
    carbsGramsPerUnit = carbsGramsPerUnit.toDoubleOrNull() ?: 0.0,
    fatGramsPerUnit = fatGramsPerUnit.toDoubleOrNull() ?: 0.0,
    barcode = barcode,
    externalSourceId = externalSourceId,
)
