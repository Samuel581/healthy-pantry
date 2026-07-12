package com.healthypantry.feature.nutrition.data.di

import com.healthypantry.feature.nutrition.data.off.OpenFoodFactsApi
import com.healthypantry.feature.nutrition.data.usda.UsdaFoodDataCentralApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Configures the shared base [Retrofit] (see [com.healthypantry.core.network.NetworkModule])
 * with each nutrition-lookup source's own base URL, since [com.healthypantry.core.network.NetworkModule]
 * intentionally provides no endpoints itself.
 */
@Module
@InstallIn(SingletonComponent::class)
object NutritionNetworkModule {

    private const val OPEN_FOOD_FACTS_BASE_URL = "https://world.openfoodfacts.org/"
    private const val USDA_FOOD_DATA_CENTRAL_BASE_URL = "https://api.nal.usda.gov/fdc/"

    @Provides
    @Singleton
    fun provideOpenFoodFactsApi(retrofit: Retrofit): OpenFoodFactsApi =
        retrofit.newBuilder()
            .baseUrl(OPEN_FOOD_FACTS_BASE_URL)
            .build()
            .create(OpenFoodFactsApi::class.java)

    @Provides
    @Singleton
    fun provideUsdaFoodDataCentralApi(retrofit: Retrofit): UsdaFoodDataCentralApi =
        retrofit.newBuilder()
            .baseUrl(USDA_FOOD_DATA_CENTRAL_BASE_URL)
            .build()
            .create(UsdaFoodDataCentralApi::class.java)
}
