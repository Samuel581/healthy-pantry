package com.healthypantry.feature.expiry.notification

import com.healthypantry.feature.expiry.domain.model.ExpiringBatch

/**
 * Posts a local system notification listing expiring/expired items (spec "Expiry Notification
 * Scheduling"). Extracted behind an interface so
 * [com.healthypantry.feature.expiry.worker.ExpiryCheckWorker] can be tested with a fake instead
 * of asserting on a real [android.app.NotificationManager] side effect.
 */
interface ExpiryNotifier {
    /** No-ops when [items] is empty; callers are not required to guard against that themselves. */
    fun notifyExpiringItems(items: List<ExpiringBatch>)
}
