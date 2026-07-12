package com.healthypantry.feature.nutrition.data.usda

import kotlinx.serialization.Serializable

/**
 * Response shape of `GET /v1/foods/search` on the USDA FoodData Central API. An empty
 * [foods] list means the query had no match; the source class treats that as a lookup miss.
 */
@Serializable
data class UsdaSearchResponse(
    val totalHits: Int = 0,
    val foods: List<UsdaFood> = emptyList(),
)

@Serializable
data class UsdaFood(
    val fdcId: Long,
    val description: String,
    val foodNutrients: List<UsdaFoodNutrient> = emptyList(),
)

/**
 * A single reported nutrient for a [UsdaFood], e.g. `{"nutrientId": 1008, "nutrientName":
 * "Energy", "unitName": "KCAL", "value": 34.0}`. USDA FDC reports macros per 100g, matching
 * [com.healthypantry.feature.nutrition.domain.model.NutritionResult]'s convention.
 */
@Serializable
data class UsdaFoodNutrient(
    val nutrientId: Int? = null,
    val nutrientName: String? = null,
    val value: Double? = null,
)
