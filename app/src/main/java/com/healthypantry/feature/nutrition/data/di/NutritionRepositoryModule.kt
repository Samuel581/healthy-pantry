package com.healthypantry.feature.nutrition.data.di

import com.healthypantry.feature.nutrition.data.NutritionLookupRepository
import com.healthypantry.feature.nutrition.data.NutritionLookupRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds [NutritionLookupRepository] to its production implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NutritionRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNutritionLookupRepository(
        impl: NutritionLookupRepositoryImpl,
    ): NutritionLookupRepository
}
