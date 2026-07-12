package com.healthypantry.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.healthypantry.feature.pantry.data.dao.FoodItemDao
import com.healthypantry.feature.pantry.data.dao.StockBatchDao
import com.healthypantry.feature.pantry.data.dao.UnitConversionDao
import com.healthypantry.feature.pantry.data.entity.FoodItemEntity
import com.healthypantry.feature.pantry.data.entity.StockBatchEntity
import com.healthypantry.feature.pantry.data.entity.UnitConversionEntity

/**
 * v1 (PR3): pantry-stock entities only. PR7 adds a migration for the meal-planning/recipes
 * tables (see design.md "Room schema").
 */
@Database(
    entities = [
        FoodItemEntity::class,
        UnitConversionEntity::class,
        StockBatchEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao
    abstract fun unitConversionDao(): UnitConversionDao
    abstract fun stockBatchDao(): StockBatchDao
}
