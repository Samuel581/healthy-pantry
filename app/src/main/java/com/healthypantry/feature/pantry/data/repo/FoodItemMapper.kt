package com.healthypantry.feature.pantry.data.repo

import com.healthypantry.feature.pantry.data.entity.FoodItemEntity
import com.healthypantry.feature.pantry.domain.model.FoodItem

internal fun FoodItemEntity.toDomain(): FoodItem = FoodItem(
    id = id,
    name = name,
    canonicalUnit = canonicalUnit,
    source = source,
    caloriesPerUnit = caloriesPerUnit,
    proteinGramsPerUnit = proteinGramsPerUnit,
    carbsGramsPerUnit = carbsGramsPerUnit,
    fatGramsPerUnit = fatGramsPerUnit,
    barcode = barcode,
    externalSourceId = externalSourceId,
)

internal fun FoodItem.toEntity(): FoodItemEntity = FoodItemEntity(
    id = id,
    name = name,
    canonicalUnit = canonicalUnit,
    source = source,
    caloriesPerUnit = caloriesPerUnit,
    proteinGramsPerUnit = proteinGramsPerUnit,
    carbsGramsPerUnit = carbsGramsPerUnit,
    fatGramsPerUnit = fatGramsPerUnit,
    barcode = barcode,
    externalSourceId = externalSourceId,
)
