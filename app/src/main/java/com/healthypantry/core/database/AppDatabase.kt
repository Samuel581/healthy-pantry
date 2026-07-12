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
import com.healthypantry.feature.planning.data.dao.PlanEntryDao
import com.healthypantry.feature.planning.data.entity.PlanEntryEntity
import com.healthypantry.feature.recipes.data.dao.RecipeDao
import com.healthypantry.feature.recipes.data.dao.RecipeIngredientDao
import com.healthypantry.feature.recipes.data.entity.RecipeEntity
import com.healthypantry.feature.recipes.data.entity.RecipeIngredientEntity

/**
 * v1 (PR3): pantry-stock entities only. v2 (PR7) adds the meal-planning/recipes tables (see
 * design.md "Room schema"). No [androidx.room.migration.Migration] is required for this bump:
 * per design.md "Migration / Rollout" this is a greenfield local Room DB with no installed
 * clients yet — Room only needs a migration path for an app that must upgrade an existing
 * on-device database.
 */
@Database(
    entities = [
        FoodItemEntity::class,
        UnitConversionEntity::class,
        StockBatchEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        PlanEntryEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao
    abstract fun unitConversionDao(): UnitConversionDao
    abstract fun stockBatchDao(): StockBatchDao
    abstract fun recipeDao(): RecipeDao
    abstract fun recipeIngredientDao(): RecipeIngredientDao
    abstract fun planEntryDao(): PlanEntryDao
}
