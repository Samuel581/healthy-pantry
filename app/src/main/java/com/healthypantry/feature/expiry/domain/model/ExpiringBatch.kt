package com.healthypantry.feature.expiry.domain.model

import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.StockBatch

/** Whether a [StockBatch] is already past its expiry date or merely approaching it. */
enum class ExpiryStatus {
    EXPIRING_SOON,
    EXPIRED,
}

/**
 * A [StockBatch] flagged by [com.healthypantry.feature.expiry.data.repo.ExpiryAlertRepository]
 * as within the expiry lookahead window (or already expired), joined with its owning [FoodItem]
 * so callers (notification content, in-app banner) have the item name without a second lookup
 * (spec "Expiry Notification Scheduling").
 */
data class ExpiringBatch(
    val foodItem: FoodItem,
    val stockBatch: StockBatch,
    val status: ExpiryStatus,
)

/**
 * Shared display label for a single [ExpiringBatch] — "<name> (expired)" once past due, otherwise
 * just the food item name. Used by both the in-app banner
 * ([com.healthypantry.feature.expiry.ui.ExpiryBanner]) and the system notification
 * ([com.healthypantry.feature.expiry.notification.SystemExpiryNotifier]) so the two surfaces
 * can't drift apart.
 */
fun ExpiringBatch.displayLabel(): String =
    if (status == ExpiryStatus.EXPIRED) "${foodItem.name} (expired)" else foodItem.name
