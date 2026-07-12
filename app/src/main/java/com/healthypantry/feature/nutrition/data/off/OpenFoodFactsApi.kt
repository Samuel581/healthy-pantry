package com.healthypantry.feature.nutrition.data.off

import retrofit2.http.GET
import retrofit2.http.Path

/** Retrofit service for the public Open Food Facts read API (no API key required). */
interface OpenFoodFactsApi {

    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduct(@Path("barcode") barcode: String): OffProductResponse
}
