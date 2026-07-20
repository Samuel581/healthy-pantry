package com.healthypantry.feature.pantry.ui.vm

import com.healthypantry.core.unit.ConversionFactor
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.nutrition.domain.model.NutritionResult
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource

/** Which [ItemFormUiState] field the user has directly edited. */
enum class ItemFormField {
    NAME, UNIT, CALORIES, PROTEIN, CARBS, FAT
}

/**
 * One editable row inside [ItemFormUiState.conversions] — a registered [ConversionFactor] for
 * this item, still being edited (same "raw string field" convention as
 * `RecipeIngredientFormRow.quantity`: [factor] binds directly to an `OutlinedTextField` while the
 * user is mid-edit and is only parsed at save time, by [ItemFormUiState.toConversionFactors]).
 */
data class ConversionRowState(
    val fromUnit: MeasurementUnit = MeasurementUnit.GRAM,
    val toUnit: MeasurementUnit = MeasurementUnit.GRAM,
    val factor: String = "",
)

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
    val searchResults: List<NutritionResult> = emptyList(),
    val conversions: List<ConversionRowState> = emptyList(),
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
 * Builds the [FoodItem] to persist from this form state. [FoodItem]'s macro fields are nullable
 * [Double]s, so a blank or unparseable macro field saves as `null` ("unknown") instead of being
 * coerced to `0.0` — this preserves the "0 macros" vs. "unknown macros" distinction all the way
 * to persistence (spec "Item-to-Day Macro Rollup" -> "Missing macro data on an item"). A field
 * the user actually typed `0`/`0.0` into still parses to a real, known `0.0`.
 */
fun ItemFormUiState.toFoodItem(id: Long = 0L): FoodItem = FoodItem(
    id = id,
    name = name,
    canonicalUnit = canonicalUnit,
    source = source,
    caloriesPerUnit = caloriesPerUnit.toDoubleOrNull(),
    proteinGramsPerUnit = proteinGramsPerUnit.toDoubleOrNull(),
    carbsGramsPerUnit = carbsGramsPerUnit.toDoubleOrNull(),
    fatGramsPerUnit = fatGramsPerUnit.toDoubleOrNull(),
    barcode = barcode,
    externalSourceId = externalSourceId,
)

/**
 * Builds the [ConversionFactor]s to persist from this form state's [ItemFormUiState.conversions].
 * A row with a blank or unparseable [ConversionRowState.factor] is dropped rather than blocking
 * the whole save — same "best-effort parse, save what's valid" approach as [toFoodItem] and
 * `RecipeViewModel.saveRecipe`'s ingredient rows.
 *
 * This ViewModel never touches [com.healthypantry.feature.pantry.data.repo.UnitConversionRepository]
 * directly (same convention as [toFoodItem]/`FoodItemRepository`, see [ItemFormViewModel] KDoc) —
 * the caller (a later PR's screen, via `PantryViewModel`) reads this built list to persist it.
 */
fun ItemFormUiState.toConversionFactors(): List<ConversionFactor> = conversions.mapNotNull { row ->
    val factor = row.factor.toDoubleOrNull() ?: return@mapNotNull null
    ConversionFactor(fromUnit = row.fromUnit, toUnit = row.toUnit, factor = factor)
}
