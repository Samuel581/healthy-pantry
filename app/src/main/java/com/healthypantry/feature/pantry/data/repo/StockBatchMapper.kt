package com.healthypantry.feature.pantry.data.repo

import com.healthypantry.feature.pantry.data.entity.StockBatchEntity
import com.healthypantry.feature.pantry.domain.model.StockBatch

internal fun StockBatchEntity.toDomain(): StockBatch = StockBatch(
    id = id,
    foodItemId = foodItemId,
    quantity = quantity,
    expiryDate = expiryDate,
    addedAt = addedAt,
)

internal fun StockBatch.toEntity(): StockBatchEntity = StockBatchEntity(
    id = id,
    foodItemId = foodItemId,
    quantity = quantity,
    expiryDate = expiryDate,
    addedAt = addedAt,
)
