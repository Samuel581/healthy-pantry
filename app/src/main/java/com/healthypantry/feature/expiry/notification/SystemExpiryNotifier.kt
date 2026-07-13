package com.healthypantry.feature.expiry.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.healthypantry.feature.expiry.domain.model.ExpiringBatch
import com.healthypantry.feature.expiry.domain.model.ExpiryStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Production [ExpiryNotifier]: posts a single summary notification on the
 * [EXPIRY_CHANNEL_ID] channel (created once in `HealthyPantryApp.onCreate`).
 *
 * On API 33+, [Manifest.permission.POST_NOTIFICATIONS] may be denied — per spec
 * "Notification-Denied Fallback" this MUST NOT crash (calling
 * [NotificationManagerCompat.notify] without the permission throws [SecurityException]); it
 * silently no-ops instead, since the in-app banner
 * ([com.healthypantry.feature.expiry.ui.vm.ExpiryAlertViewModel]/
 * [com.healthypantry.feature.expiry.ui.ExpiryBanner]) is the mandatory fallback surface, driven
 * by the same [com.healthypantry.feature.expiry.data.repo.ExpiryAlertRepository] independently of
 * whether this notification was posted.
 */
class SystemExpiryNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExpiryNotifier {

    override fun notifyExpiringItems(items: List<ExpiringBatch>) {
        if (items.isEmpty()) return
        if (!canPostNotifications()) return

        val notification = NotificationCompat.Builder(context, EXPIRY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(contentTitle(items))
            .setContentText(contentText(items))
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText(items)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(EXPIRY_NOTIFICATION_ID, notification)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun contentTitle(items: List<ExpiringBatch>): String = when {
        items.size == 1 -> "${items.first().foodItem.name} is expiring soon"
        items.any { it.status == ExpiryStatus.EXPIRED } -> "${items.size} pantry items need attention"
        else -> "${items.size} pantry items expiring soon"
    }

    private fun contentText(items: List<ExpiringBatch>): String =
        items.joinToString(", ") { item ->
            if (item.status == ExpiryStatus.EXPIRED) "${item.foodItem.name} (expired)" else item.foodItem.name
        }

    companion object {
        const val EXPIRY_CHANNEL_ID = "expiry_alerts"
        private const val EXPIRY_NOTIFICATION_ID = 1001
    }
}
