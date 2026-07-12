package com.healthypantry.feature.nutrition.data.usda

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service for the USDA FoodData Central name-search API
 * (`GET /v1/foods/search`). Requires an `api_key` query param — see
 * [com.healthypantry.feature.nutrition.data.di.NutritionNetworkModule] for how
 * `BuildConfig.USDA_FDC_API_KEY` (`DEMO_KEY` by default) is supplied.
 */
interface UsdaFoodDataCentralApi {

    @GET("v1/foods/search")
    suspend fun searchFoods(
        @Query("query") query: String,
        @Query("api_key") apiKey: String,
    ): UsdaSearchResponse
}
