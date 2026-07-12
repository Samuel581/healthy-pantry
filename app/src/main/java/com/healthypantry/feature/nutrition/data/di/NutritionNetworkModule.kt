package com.healthypantry.feature.nutrition.data.di

import com.healthypantry.feature.nutrition.data.off.OpenFoodFactsApi
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

    @Provides
    @Singleton
    fun provideOpenFoodFactsApi(retrofit: Retrofit): OpenFoodFactsApi =
        retrofit.newBuilder()
            .baseUrl(OPEN_FOOD_FACTS_BASE_URL)
            .build()
            .create(OpenFoodFactsApi::class.java)
}
