package com.healthypantry.feature.nutrition.data.usda

import com.healthypantry.BuildConfig
import com.healthypantry.core.common.Result
import com.healthypantry.feature.nutrition.domain.model.NutritionLookupError
import com.healthypantry.feature.nutrition.domain.model.NutritionResult
import com.healthypantry.feature.nutrition.domain.model.NutritionSource
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * USDA FoodData Central name-search lookup (spec scenario "Manual name search against USDA
 * FoodData Central").
 *
 * [com.healthypantry.feature.nutrition.data.NutritionLookupRepositoryImpl] uses this source for
 * the manual-entry name-search flow, either as a direct user action or as the fallback after an
 * Open Food Facts barcode miss (spec scenario "Barcode not found in Open Food Facts").
 *
 * A `429` response means the shared `DEMO_KEY` quota is exhausted and MUST be surfaced as
 * [NutritionLookupError.RateLimited], distinct from an empty result set
 * ([NutritionLookupError.NotFound]), per spec scenario "USDA API key not configured" — otherwise
 * a rate-limited user would wrongly believe their search simply had no matches.
 */
class UsdaNutritionSource @Inject constructor(
    private val api: UsdaFoodDataCentralApi,
) {

    suspend fun searchByName(query: String): Result<NutritionResult, NutritionLookupError> =
        try {
            val response = api.searchFoods(query = query, apiKey = BuildConfig.USDA_FDC_API_KEY)
            val food = response.foods.firstOrNull()
            if (food == null) {
                Result.failure(NutritionLookupError.NotFound)
            } else {
                Result.success(food.toDomain())
            }
        } catch (e: HttpException) {
            if (e.code() == 429) {
                Result.failure(NutritionLookupError.RateLimited)
            } else {
                Result.failure(NutritionLookupError.ApiError(e.code(), e.message()))
            }
        } catch (e: IOException) {
            Result.failure(NutritionLookupError.NetworkError(e.message))
        }
}

// USDA FoodData Central nutrient IDs (stable across the API), per-100g.
private const val ENERGY_NUTRIENT_ID = 1008
private const val PROTEIN_NUTRIENT_ID = 1003
private const val CARBS_NUTRIENT_ID = 1005
private const val FAT_NUTRIENT_ID = 1004

private fun UsdaFood.toDomain(): NutritionResult =
    NutritionResult(
        name = description.takeIf { it.isNotBlank() } ?: "Unknown food",
        caloriesPer100 = nutrientValue(ENERGY_NUTRIENT_ID),
        proteinGramsPer100 = nutrientValue(PROTEIN_NUTRIENT_ID),
        carbsGramsPer100 = nutrientValue(CARBS_NUTRIENT_ID),
        fatGramsPer100 = nutrientValue(FAT_NUTRIENT_ID),
        source = NutritionSource.USDA_FOOD_DATA_CENTRAL,
        externalId = fdcId.toString(),
    )

private fun UsdaFood.nutrientValue(nutrientId: Int): Double? =
    foodNutrients.firstOrNull { it.nutrientId == nutrientId }?.value
