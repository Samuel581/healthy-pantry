package com.healthypantry.feature.nutrition.domain.model

/**
 * A single nutrition lookup match, normalized from either Open Food Facts or USDA FoodData
 * Central so callers of
 * [com.healthypantry.feature.nutrition.data.NutritionLookupRepository] don't need to know
 * which source answered (spec "Barcode Scan, Manual Entry with USDA Fallback").
 *
 * Macro fields are expressed per 100 grams/milliliters, matching both source APIs; the caller
 * (form pre-fill, PR6) converts to a [com.healthypantry.feature.pantry.domain.model.FoodItem]'s
 * per-unit fields as needed.
 *
 * Macro fields are nullable because source APIs (especially community-sourced Open Food Facts
 * entries) frequently omit them. `null` means "not reported by the source" and MUST be kept
 * distinct from a verified `0.0` — collapsing the two would misrepresent an unknown macro as a
 * confirmed zero-calorie/zero-macro food.
 *
 * [brand], [servingSize], [servingSizeUnit], and [householdServingFullText] are display-only
 * fields for rendering a search result row (brand suffix, per-serving macro line) — they are
 * separate from the per-100 macro fields above, which remain the only fields the form pre-fill
 * flow ([com.healthypantry.feature.pantry.ui.vm.ItemFormUiState]'s `prefillFrom`) reads from.
 */
data class NutritionResult(
    val name: String,
    val caloriesPer100: Double?,
    val proteinGramsPer100: Double?,
    val carbsGramsPer100: Double?,
    val fatGramsPer100: Double?,
    val source: NutritionSource,
    val externalId: String? = null,
    val brand: String? = null,
    val servingSize: Double? = null,
    val servingSizeUnit: String? = null,
    val householdServingFullText: String? = null,
)

/** Which backing source answered a [NutritionResult]. */
enum class NutritionSource {
    OPEN_FOOD_FACTS,
    USDA_FOOD_DATA_CENTRAL,
}
