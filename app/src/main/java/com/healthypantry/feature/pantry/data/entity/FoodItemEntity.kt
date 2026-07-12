package com.healthypantry.feature.pantry.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.domain.model.FoodItemSource

/**
 * Room persistence model for a pantry food item (spec "Item and Stock Batch CRUD"). Mapped
 * to/from [com.healthypantry.feature.pantry.domain.model.FoodItem] by
 * `feature/pantry/data/repo` mappers — never returned directly from a repository.
 *
 * Macro fields are expressed per one unit of [canonicalUnit].
 */
@Entity(tableName = "food_item")
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val canonicalUnit: MeasurementUnit,
    val source: FoodItemSource,
    val caloriesPerUnit: Double,
    val proteinGramsPerUnit: Double,
    val carbsGramsPerUnit: Double,
    val fatGramsPerUnit: Double,
    val barcode: String?,
    val externalSourceId: String?,
)
