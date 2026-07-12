package com.healthypantry.feature.pantry.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Room persistence model for a stock batch (spec "Item and Stock Batch CRUD"): a quantity of a
 * [FoodItemEntity] added at a point in time, with an optional expiry date. [quantity] is
 * expressed in the owning item's canonical unit.
 *
 * `onDelete = CASCADE`: deleting a [FoodItemEntity] removes its batches too — there is no
 * meaningful "orphaned" stock batch once its item is gone.
 */
@Entity(
    tableName = "stock_batch",
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
data class StockBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodItemId: Long,
    val quantity: Double,
    val expiryDate: LocalDate?,
    val addedAt: Instant,
)
