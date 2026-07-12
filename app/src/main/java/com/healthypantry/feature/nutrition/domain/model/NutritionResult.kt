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
 */
data class NutritionResult(
    val name: String,
    val caloriesPer100: Double,
    val proteinGramsPer100: Double,
    val carbsGramsPer100: Double,
    val fatGramsPer100: Double,
    val source: NutritionSource,
    val externalId: String? = null,
)

/** Which backing source answered a [NutritionResult]. */
enum class NutritionSource {
    OPEN_FOOD_FACTS,
    USDA_FOOD_DATA_CENTRAL,
}
