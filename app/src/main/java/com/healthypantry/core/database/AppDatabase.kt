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
 * design.md "Room schema"). v3 (verify-report.md CRITICAL fix, "Missing macro data on an item")
 * widens `FoodItemEntity`'s four macro columns from non-null to nullable `Double?`, so unknown
 * macros can be distinguished from a verified `0.0` instead of being coerced to zero at save
 * time. No [androidx.room.migration.Migration] is required for this or the v1->v2 bump: per
 * design.md "Migration / Rollout" this is a greenfield local Room DB with no installed clients
 * yet — Room only needs a migration path for an app that must upgrade an existing on-device
 * database. Widening a column from non-null to nullable is additionally backward-compatible at
 * the SQL level even when a migration path does exist (SQLite has no column-level NOT NULL
 * enforcement subtlety here that would require one), so this precedent still applies.
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
    version = 3,
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
