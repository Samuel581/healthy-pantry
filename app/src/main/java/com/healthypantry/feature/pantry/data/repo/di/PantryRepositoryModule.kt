package com.healthypantry.feature.pantry.data.repo.di

import com.healthypantry.feature.pantry.data.repo.FoodItemRepository
import com.healthypantry.feature.pantry.data.repo.FoodItemRepositoryImpl
import com.healthypantry.feature.pantry.data.repo.StockBatchRepository
import com.healthypantry.feature.pantry.data.repo.StockBatchRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds pantry-stock repository interfaces to their production implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PantryRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFoodItemRepository(impl: FoodItemRepositoryImpl): FoodItemRepository

    @Binds
    @Singleton
    abstract fun bindStockBatchRepository(impl: StockBatchRepositoryImpl): StockBatchRepository
}
