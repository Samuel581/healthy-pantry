package com.healthypantry.feature.pantry.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Domain-facing representation of a quantity of a [FoodItem] added to the pantry at a point in
 * time, with an optional expiry date (see spec "Item and Stock Batch CRUD").
 *
 * [quantity] is expressed in the owning [FoodItem]'s canonical unit.
 */
data class StockBatch(
    val id: Long = 0,
    val foodItemId: Long,
    val quantity: Double,
    val expiryDate: LocalDate? = null,
    val addedAt: Instant,
)
