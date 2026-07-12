package com.healthypantry.feature.pantry.domain.model

import com.healthypantry.core.unit.MeasurementUnit

/**
 * Domain-facing representation of a pantry food item — no Room annotations, so this is what
 * repositories return to the domain/UI layers (see spec "Item and Stock Batch CRUD").
 *
 * Macro fields ([caloriesPerUnit], [proteinGramsPerUnit], [carbsGramsPerUnit],
 * [fatGramsPerUnit]) are expressed per one unit of [canonicalUnit] (e.g. per gram), so they
 * scale directly with any [com.healthypantry.feature.pantry.domain.model.StockBatch] quantity
 * without a separate conversion.
 */
data class FoodItem(
    val id: Long = 0,
    val name: String,
    val canonicalUnit: MeasurementUnit,
    val source: FoodItemSource,
    val caloriesPerUnit: Double,
    val proteinGramsPerUnit: Double,
    val carbsGramsPerUnit: Double,
    val fatGramsPerUnit: Double,
    val barcode: String? = null,
    val externalSourceId: String? = null,
)
