package com.healthypantry.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.healthypantry.feature.expiry.notification.SystemExpiryNotifier
import com.healthypantry.feature.expiry.worker.ExpiryCheckWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Hilt generates the top-level DI container ([dagger.hilt.android.internal.managers.ApplicationComponentManager])
 * rooted here; feature modules request their dependencies via constructor injection.
 *
 * Implements [Configuration.Provider] so WorkManager's on-demand (`androidx.startup`) init picks
 * up [HiltWorkerFactory] automatically — [ExpiryCheckWorker] is a `@HiltWorker`, so it needs Hilt
 * to supply its non-`@Assisted` dependencies.
 */
@HiltAndroidApp
class HealthyPantryApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        createExpiryNotificationChannel()
        scheduleExpiryCheckWorker()
    }

    /**
     * Spec "Expiry Notification Scheduling": the channel must exist before
     * [SystemExpiryNotifier] ever posts to it. minSdk is already 26 ([NotificationChannel]'s
     * introduction API), so no version guard is needed.
     */
    private fun createExpiryNotificationChannel() {
        val channel = NotificationChannel(
            SystemExpiryNotifier.EXPIRY_CHANNEL_ID,
            "Expiry alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Alerts for pantry items nearing or past their expiry date"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Idempotent: `KEEP` preserves an already-scheduled run across process restarts. */
    private fun scheduleExpiryCheckWorker() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ExpiryCheckWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            ExpiryCheckWorker.periodicRequest(),
        )
    }
}
