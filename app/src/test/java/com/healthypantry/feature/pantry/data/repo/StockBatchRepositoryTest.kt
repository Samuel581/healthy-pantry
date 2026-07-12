package com.healthypantry.feature.pantry.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.database.AppDatabase
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.pantry.domain.model.StockBatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * Spec: Item and Stock Batch CRUD — repository-level guarantee that a deleted [StockBatch] is
 * never counted in a future stock calculation (openspec/changes/pantry-tracker/specs/pantry-stock/spec.md).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StockBatchRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var foodItemRepository: FoodItemRepository
    private lateinit var repository: StockBatchRepository
    private var chickenBreastId: Long = 0

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
        repository = StockBatchRepositoryImpl(database.stockBatchDao(), testDispatcherProvider)

        chickenBreastId = foodItemRepository.upsert(
            FoodItem(
                name = "Chicken breast",
                canonicalUnit = MeasurementUnit.GRAM,
                source = FoodItemSource.MANUAL,
                caloriesPerUnit = 1.65,
                proteinGramsPerUnit = 0.31,
                carbsGramsPerUnit = 0.0,
                fatGramsPerUnit = 0.036,
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `adding a new stock batch for an existing item is reflected in total on-hand quantity`() = runTest {
        // Given a FoodItem "Chicken breast" already exists (see setUp)
        // When the user adds a stock batch of 500g with an expiry date
        repository.upsert(
            StockBatch(
                foodItemId = chickenBreastId,
                quantity = 500.0,
                expiryDate = LocalDate.of(2026, 7, 20),
                addedAt = Instant.parse("2026-07-12T00:00:00Z"),
            ),
        )

        // Then the item's total on-hand quantity reflects the addition
        assertEquals(500.0, repository.observeTotalOnHand(chickenBreastId).first(), 0.0001)
        val batches = repository.observeForFoodItem(chickenBreastId).first()
        assertEquals(1, batches.size)
        assertEquals(LocalDate.of(2026, 7, 20), batches.first().expiryDate)
    }

    @Test
    fun `deleting a stock batch is not counted in any future stock calculation`() = runTest {
        // Given a StockBatch exists with remaining quantity > 0 (plus another that stays)
        val keptId = repository.upsert(
            StockBatch(
                foodItemId = chickenBreastId,
                quantity = 300.0,
                addedAt = Instant.parse("2026-07-12T00:00:00Z"),
            ),
        )
        val toDeleteId = repository.upsert(
            StockBatch(
                foodItemId = chickenBreastId,
                quantity = 500.0,
                expiryDate = LocalDate.of(2026, 7, 20),
                addedAt = Instant.parse("2026-07-13T00:00:00Z"),
            ),
        )
        val toDelete = repository.observeForFoodItem(chickenBreastId).first().first { it.id == toDeleteId }
        assertEquals(800.0, repository.observeTotalOnHand(chickenBreastId).first(), 0.0001)

        // When the user deletes it
        repository.delete(toDelete)

        // Then it MUST NOT be counted in any future stock calculation (projected or actual)
        assertEquals(300.0, repository.observeTotalOnHand(chickenBreastId).first(), 0.0001)
        val remaining = repository.observeForFoodItem(chickenBreastId).first()
        assertEquals(listOf(keptId), remaining.map { it.id })
    }

    @Test
    fun `upsert with an existing id updates the batch instead of inserting a duplicate`() = runTest {
        val id = repository.upsert(
            StockBatch(foodItemId = chickenBreastId, quantity = 200.0, addedAt = Instant.parse("2026-07-12T00:00:00Z")),
        )
        val stored = repository.observeForFoodItem(chickenBreastId).first().first { it.id == id }

        repository.upsert(stored.copy(quantity = 250.0))

        val batches = repository.observeForFoodItem(chickenBreastId).first()
        assertEquals(1, batches.size)
        assertEquals(250.0, batches.first().quantity, 0.0001)
    }
}
