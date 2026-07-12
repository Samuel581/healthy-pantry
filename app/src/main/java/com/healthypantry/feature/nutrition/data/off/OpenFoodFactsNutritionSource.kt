package com.healthypantry.feature.nutrition.data.off

import com.healthypantry.core.common.Result
import com.healthypantry.feature.nutrition.domain.model.NutritionLookupError
import com.healthypantry.feature.nutrition.domain.model.NutritionResult
import com.healthypantry.feature.nutrition.domain.model.NutritionSource
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * Open Food Facts barcode lookup (spec scenario "Successful barcode scan and OFF lookup").
 *
 * [com.healthypantry.feature.nutrition.data.NutritionLookupRepositoryImpl] tries this source
 * first for any barcode scan. A miss here does **not** trigger an automatic USDA query — per
 * spec scenario "Barcode not found in Open Food Facts", the caller falls back to the manual
 * entry flow, which performs its own USDA name-search.
 */
class OpenFoodFactsNutritionSource @Inject constructor(
    private val api: OpenFoodFactsApi,
) {

    suspend fun lookupByBarcode(barcode: String): Result<NutritionResult, NutritionLookupError> =
        try {
            val response = api.getProduct(barcode)
            val product = response.product
            if (response.status != 1 || product == null) {
                Result.failure(NutritionLookupError.NotFound)
            } else {
                Result.success(product.toDomain())
            }
        } catch (e: HttpException) {
            // A 404 is Open Food Facts telling us the barcode has no entry at all (distinct
            // from the 200+status:0 "empty product" miss shape handled above) - both are a
            // lookup miss, not an API error, per spec "barcode-miss must be distinguishable
            // from a transport/network failure".
            if (e.code() == 404) {
                Result.failure(NutritionLookupError.NotFound)
            } else {
                Result.failure(NutritionLookupError.ApiError(e.code(), e.message()))
            }
        } catch (e: IOException) {
            Result.failure(NutritionLookupError.NetworkError(e.message))
        }
}

private fun OffProduct.toDomain(): NutritionResult {
    val macros = nutriments ?: OffNutriments()
    return NutritionResult(
        name = productName?.takeIf { it.isNotBlank() } ?: "Unknown product",
        caloriesPer100 = macros.energyKcal100g,
        proteinGramsPer100 = macros.proteins100g,
        carbsGramsPer100 = macros.carbohydrates100g,
        fatGramsPer100 = macros.fat100g,
        source = NutritionSource.OPEN_FOOD_FACTS,
    )
}
