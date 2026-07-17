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
 *
 * Each macro field is nullable: `null` means "unknown / not yet entered" (e.g. a manually
 * created item where the lookup was skipped and the form field was left blank), distinct from a
 * verified `0.0`. This distinction must survive persistence (spec "Item-to-Day Macro Rollup" ->
 * "Missing macro data on an item") so
 * [com.healthypantry.feature.planning.domain.usecase.ComputeMacroTotalsUseCase] can flag a
 * rollup as incomplete instead of silently treating an unknown macro as zero.
 */
data class FoodItem(
    val id: Long = 0,
    val name: String,
    val canonicalUnit: MeasurementUnit,
    val source: FoodItemSource,
    val caloriesPerUnit: Double?,
    val proteinGramsPerUnit: Double?,
    val carbsGramsPerUnit: Double?,
    val fatGramsPerUnit: Double?,
    val barcode: String? = null,
    val externalSourceId: String? = null,
)
