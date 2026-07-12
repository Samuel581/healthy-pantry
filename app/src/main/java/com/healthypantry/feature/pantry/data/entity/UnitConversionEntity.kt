package com.healthypantry.feature.pantry.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.healthypantry.core.unit.MeasurementUnit

/**
 * Room persistence model for a per-[FoodItemEntity] unit conversion factor (spec "Unit
 * Conversion Correctness"). Backs [com.healthypantry.core.unit.UnitConverter] with real,
 * per-item data instead of the [com.healthypantry.core.unit.ConversionFactor] list PR2 accepted
 * as a plain parameter — the system MUST NOT apply a global or hardcoded conversion table.
 *
 * `onDelete = CASCADE`: a conversion factor has no meaning once its item is gone.
 */
@Entity(
    tableName = "unit_conversion",
    foreignKeys = [
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("foodItemId")],
)
data class UnitConversionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodItemId: Long,
    val fromUnit: MeasurementUnit,
    val toUnit: MeasurementUnit,
    val factor: Double,
)
