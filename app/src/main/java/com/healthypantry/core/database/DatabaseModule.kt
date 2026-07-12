package com.healthypantry.core.database

import android.content.Context
import androidx.room.Room
import com.healthypantry.feature.pantry.data.dao.FoodItemDao
import com.healthypantry.feature.pantry.data.dao.StockBatchDao
import com.healthypantry.feature.pantry.data.dao.UnitConversionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the single [AppDatabase] instance and its DAOs for constructor injection. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "healthy_pantry.db"

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).build()

    @Provides
    fun provideFoodItemDao(database: AppDatabase): FoodItemDao = database.foodItemDao()

    @Provides
    fun provideUnitConversionDao(database: AppDatabase): UnitConversionDao = database.unitConversionDao()

    @Provides
    fun provideStockBatchDao(database: AppDatabase): StockBatchDao = database.stockBatchDao()
}
