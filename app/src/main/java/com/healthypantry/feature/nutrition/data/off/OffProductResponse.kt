package com.healthypantry.feature.nutrition.data.off

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response shape of `GET /api/v2/product/{barcode}.json` on the Open Food Facts API.
 * `status == 0` and/or a missing [product] means the barcode has no match; the source class
 * treats either as a lookup miss.
 */
@Serializable
data class OffProductResponse(
    val status: Int = 0,
    val product: OffProduct? = null,
)

@Serializable
data class OffProduct(
    @SerialName("product_name") val productName: String? = null,
    val nutriments: OffNutriments? = null,
)

/** Per-100g/100ml macros, as reported by Open Food Facts. */
@Serializable
data class OffNutriments(
    @SerialName("energy-kcal_100g") val energyKcal100g: Double? = null,
    @SerialName("proteins_100g") val proteins100g: Double? = null,
    @SerialName("carbohydrates_100g") val carbohydrates100g: Double? = null,
    @SerialName("fat_100g") val fat100g: Double? = null,
)
