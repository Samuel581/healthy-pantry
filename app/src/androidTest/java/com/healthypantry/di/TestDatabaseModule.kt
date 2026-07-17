package com.healthypantry.di

import android.content.Context
import androidx.room.Room
import com.healthypantry.core.database.AppDatabase
import com.healthypantry.core.database.DatabaseModule
import com.healthypantry.feature.pantry.data.dao.FoodItemDao
import com.healthypantry.feature.pantry.data.dao.StockBatchDao
import com.healthypantry.feature.pantry.data.dao.UnitConversionDao
import com.healthypantry.feature.planning.data.dao.PlanEntryDao
import com.healthypantry.feature.recipes.data.dao.RecipeDao
import com.healthypantry.feature.recipes.data.dao.RecipeIngredientDao
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Test-only replacement for [DatabaseModule] (Phase 11 `AppNavigationSmokeTest`): an in-memory
 * [AppDatabase] instead of the real on-device file, so the instrumented smoke test exercises the
 * real repository/DAO/DI graph without touching persistent device state. [TestInstallIn] swaps
 * this in for every `@HiltAndroidTest` automatically — no `@UninstallModules` needed per test.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Provides
    fun provideFoodItemDao(database: AppDatabase): FoodItemDao = database.foodItemDao()

    @Provides
    fun provideUnitConversionDao(database: AppDatabase): UnitConversionDao = database.unitConversionDao()

    @Provides
    fun provideStockBatchDao(database: AppDatabase): StockBatchDao = database.stockBatchDao()

    @Provides
    fun provideRecipeDao(database: AppDatabase): RecipeDao = database.recipeDao()

    @Provides
    fun provideRecipeIngredientDao(database: AppDatabase): RecipeIngredientDao = database.recipeIngredientDao()

    @Provides
    fun providePlanEntryDao(database: AppDatabase): PlanEntryDao = database.planEntryDao()
}
