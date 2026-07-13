package com.healthypantry.feature.expiry.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.expiry.data.repo.ExpiryAlertRepository
import com.healthypantry.feature.expiry.domain.model.ExpiringBatch
import com.healthypantry.feature.expiry.domain.model.ExpiryStatus
import com.healthypantry.feature.expiry.notification.ExpiryNotifier
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.pantry.domain.model.StockBatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * Spec: Expiry Notification Scheduling
 * (openspec/changes/pantry-tracker/specs/expiry-reminders/spec.md).
 *
 * Uses WorkManager's [androidx.work.testing.TestDriver] (per this task's explicit instruction)
 * to run [ExpiryCheckWorker] through a real periodic-work enqueue rather than constructing it
 * directly, so scheduling wiring (`ExpiryCheckWorker.periodicRequest`) is exercised too. A
 * hand-written [WorkerFactory] substitutes fake [ExpiryAlertRepository]/[ExpiryNotifier]
 * implementations for the worker's non-`@Assisted` dependencies, bypassing Hilt (same
 * hand-written-fake convention as `PantryViewModelTest`) — the assertion is on the notifier side
 * effect, matching this task's explicit "or the correct side effect" allowance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpiryCheckWorkerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private val fakeRepository = FakeExpiryAlertRepository()
    private val fakeNotifier = FakeExpiryNotifier()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? =
                if (workerClassName == ExpiryCheckWorker::class.java.name) {
                    ExpiryCheckWorker(appContext, workerParameters, fakeRepository, fakeNotifier)
                } else {
                    null
                }
        }

        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(workerFactory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    private fun sampleExpiringBatch() = ExpiringBatch(
        foodItem = FoodItem(
            id = 1L,
            name = "Yogurt",
            canonicalUnit = MeasurementUnit.GRAM,
            source = FoodItemSource.MANUAL,
            caloriesPerUnit = 1.0,
            proteinGramsPerUnit = 1.0,
            carbsGramsPerUnit = 1.0,
            fatGramsPerUnit = 1.0,
        ),
        stockBatch = StockBatch(
            id = 1L,
            foodItemId = 1L,
            quantity = 200.0,
            expiryDate = LocalDate.now().plusDays(2),
            addedAt = Instant.EPOCH,
        ),
        status = ExpiryStatus.EXPIRING_SOON,
    )

    @Test
    fun `worker posts a notification when items are expiring soon`() {
        fakeRepository.result = listOf(sampleExpiringBatch())

        val request = ExpiryCheckWorker.periodicRequest()
        workManager.enqueue(request).result.get()
        val testDriver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        testDriver.setPeriodDelayMet(request.id)

        val workInfo = workManager.getWorkInfoById(request.id).get()
        assertEquals(WorkInfo.State.SUCCEEDED, workInfo.state)
        assertEquals(1, fakeNotifier.notifiedBatches.size)
        assertEquals("Yogurt", fakeNotifier.notifiedBatches.single().single().foodItem.name)
    }

    @Test
    fun `worker does nothing when no items are expiring`() {
        fakeRepository.result = emptyList()

        val request = ExpiryCheckWorker.periodicRequest()
        workManager.enqueue(request).result.get()
        val testDriver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        testDriver.setPeriodDelayMet(request.id)

        val workInfo = workManager.getWorkInfoById(request.id).get()
        assertEquals(WorkInfo.State.SUCCEEDED, workInfo.state)
        assertTrue(fakeNotifier.notifiedBatches.isEmpty())
    }

    private class FakeExpiryAlertRepository : ExpiryAlertRepository {
        var result: List<ExpiringBatch> = emptyList()
        override fun observeExpiringSoon(lookaheadDays: Long): Flow<List<ExpiringBatch>> = flowOf(result)
    }

    private class FakeExpiryNotifier : ExpiryNotifier {
        val notifiedBatches = mutableListOf<List<ExpiringBatch>>()
        override fun notifyExpiringItems(items: List<ExpiringBatch>) {
            notifiedBatches += items
        }
    }
}
