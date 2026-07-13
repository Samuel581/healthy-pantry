package com.healthypantry.feature.expiry.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.database.AppDatabase
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.expiry.domain.model.ExpiryStatus
import com.healthypantry.feature.pantry.data.repo.FoodItemRepositoryImpl
import com.healthypantry.feature.pantry.data.repo.StockBatchRepositoryImpl
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.pantry.domain.model.StockBatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Spec: Expiry Notification Scheduling, Notification-Denied Fallback
 * (openspec/changes/pantry-tracker/specs/expiry-reminders/spec.md).
 *
 * In-memory Room (Robolectric), same convention as `StockBatchRepositoryTest` — needed here
 * because the behavior under test is the `stock_batch`/`food_item` join + `WHERE expiryDate IS
 * NOT NULL` query, not something a hand-written fake DAO would meaningfully exercise. "Today" is
 * pinned via an injected [Clock.fixed] instead of [Clock.systemDefaultZone] so scenarios are
 * deterministic regardless of when the test runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpiryAlertRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var foodItemRepository: FoodItemRepositoryImpl
    private lateinit var stockBatchRepository: StockBatchRepositoryImpl
    private lateinit var repository: ExpiryAlertRepository

    /** Fixed "today" for every scenario below. */
    private val today = LocalDate.of(2026, 7, 12)
    private val fixedClock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    private val testDispatcherProvider = object : DispatcherProvider {
        private val dispatcher = UnconfinedTestDispatcher()
        override val main get() = dispatcher
        override val io get() = dispatcher
        override val default get() = dispatcher
    }

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        foodItemRepository = FoodItemRepositoryImpl(database.foodItemDao(), testDispatcherProvider)
        stockBatchRepository = StockBatchRepositoryImpl(database.stockBatchDao(), testDispatcherProvider)
        repository = ExpiryAlertRepositoryImpl(database.stockBatchDao(), database.foodItemDao(), fixedClock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun addFoodItem(name: String): Long = foodItemRepository.upsert(
        FoodItem(
            name = name,
            canonicalUnit = MeasurementUnit.GRAM,
            source = FoodItemSource.MANUAL,
            caloriesPerUnit = 1.0,
            proteinGramsPerUnit = 1.0,
            carbsGramsPerUnit = 1.0,
            fatGramsPerUnit = 1.0,
        ),
    )

    @Test
    fun `item expiring within the lookahead window is flagged as expiring soon`() = runTest {
        // Given a StockBatch of "Yogurt" has an expiry date 2 days from now and the lookahead
        // window is 3 days
        val yogurtId = addFoodItem("Yogurt")
        stockBatchRepository.upsert(
            StockBatch(foodItemId = yogurtId, quantity = 500.0, expiryDate = today.plusDays(2), addedAt = Instant.EPOCH),
        )

        // When the daily ExpiryCheckWorker run executes
        val expiring = repository.observeExpiringSoon(lookaheadDays = 3).first()

        // Then it is flagged as expiring soon
        assertEquals(1, expiring.size)
        assertEquals("Yogurt", expiring.first().foodItem.name)
        assertEquals(ExpiryStatus.EXPIRING_SOON, expiring.first().status)
    }

    @Test
    fun `item outside the lookahead window is not flagged`() = runTest {
        // Given a StockBatch expires 10 days from now
        val riceId = addFoodItem("Rice")
        stockBatchRepository.upsert(
            StockBatch(foodItemId = riceId, quantity = 1000.0, expiryDate = today.plusDays(10), addedAt = Instant.EPOCH),
        )

        // When the daily worker run executes with a 3-day lookahead
        val expiring = repository.observeExpiringSoon(lookaheadDays = 3).first()

        // Then no batch is flagged on this run
        assertTrue(expiring.isEmpty())
    }

    @Test
    fun `an expiry date exactly on the lookahead boundary is still flagged`() = runTest {
        val riceId = addFoodItem("Rice")
        stockBatchRepository.upsert(
            StockBatch(foodItemId = riceId, quantity = 1000.0, expiryDate = today.plusDays(3), addedAt = Instant.EPOCH),
        )

        val expiring = repository.observeExpiringSoon(lookaheadDays = 3).first()

        assertEquals(1, expiring.size)
        assertEquals(ExpiryStatus.EXPIRING_SOON, expiring.first().status)
    }

    @Test
    fun `an already-past expiry date is flagged as expired rather than excluded`() = runTest {
        val milkId = addFoodItem("Milk")
        stockBatchRepository.upsert(
            StockBatch(foodItemId = milkId, quantity = 200.0, expiryDate = today.minusDays(1), addedAt = Instant.EPOCH),
        )

        val expiring = repository.observeExpiringSoon(lookaheadDays = 3).first()

        assertEquals(1, expiring.size)
        assertEquals(ExpiryStatus.EXPIRED, expiring.first().status)
    }

    @Test
    fun `a batch with no expiry date is never flagged`() = runTest {
        val flourId = addFoodItem("Flour")
        stockBatchRepository.upsert(
            StockBatch(foodItemId = flourId, quantity = 1000.0, expiryDate = null, addedAt = Instant.EPOCH),
        )

        val expiring = repository.observeExpiringSoon(lookaheadDays = 3).first()

        assertTrue(expiring.isEmpty())
    }

    @Test
    fun `emits an empty list when no stock batches exist`() = runTest {
        val expiring = repository.observeExpiringSoon().first()

        assertTrue(expiring.isEmpty())
    }
}
