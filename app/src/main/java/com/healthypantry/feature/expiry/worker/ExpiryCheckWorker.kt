package com.healthypantry.feature.expiry.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.healthypantry.feature.expiry.data.repo.ExpiryAlertRepository
import com.healthypantry.feature.expiry.notification.ExpiryNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Daily background check (spec "Expiry Notification Scheduling"): reads
 * [ExpiryAlertRepository.observeExpiringSoon] once per run and, when it finds any batch expiring
 * within the lookahead window or already expired, posts a local notification via
 * [ExpiryNotifier]. Does nothing (no notification) when the list is empty.
 *
 * The in-app fallback (spec "Notification-Denied Fallback") does NOT depend on this worker
 * having run — [com.healthypantry.feature.expiry.ui.vm.ExpiryAlertViewModel] observes the same
 * [ExpiryAlertRepository] reactively, independently of this worker's schedule.
 */
@HiltWorker
class ExpiryCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val expiryAlertRepository: ExpiryAlertRepository,
    private val expiryNotifier: ExpiryNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val expiringItems = expiryAlertRepository.observeExpiringSoon().first()
        if (expiringItems.isNotEmpty()) {
            expiryNotifier.notifyExpiringItems(expiringItems)
        }
        Result.success()
    } catch (e: Exception) {
        // A transient failure (e.g. a Room I/O hiccup) must not silently drop this day's check —
        // Result.retry() lets WorkManager's default backoff policy reschedule instead of the
        // implicit Result.failure() an uncaught exception out of doWork() would otherwise cause.
        // CancellationException is rethrown (same convention as PantryViewModel.launchOnIo) so
        // WorkManager cancelling this worker isn't mistaken for a real failure.
        if (e is CancellationException) throw e
        Result.retry()
    }

    companion object {
        /** Name used with `WorkManager.enqueueUniquePeriodicWork` so scheduling is idempotent. */
        const val UNIQUE_WORK_NAME = "expiry_check_worker"

        fun periodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS).build()
    }
}
