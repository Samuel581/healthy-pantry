package com.healthypantry.feature.pantry.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthypantry.core.database.AppDatabase
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.data.entity.FoodItemEntity
import com.healthypantry.feature.pantry.data.entity.StockBatchEntity
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * Spec: Item and Stock Batch CRUD
 * (openspec/changes/pantry-tracker/specs/pantry-stock/spec.md)
 *
 * Covers both spec scenarios directly at the DAO layer: adding a stock batch increases
 * total-on-hand, and deleting one MUST NOT be counted in any future stock calculation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StockBatchDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var foodItemDao: FoodItemDao
    private lateinit var dao: StockBatchDao
    private var chickenBreastId: Long = 0

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        foodItemDao = database.foodItemDao()
        dao = database.stockBatchDao()

        chickenBreastId = foodItemDao.insert(
            FoodItemEntity(
                name = "Chicken breast",
                canonicalUnit = MeasurementUnit.GRAM,
                source = FoodItemSource.MANUAL,
                caloriesPerUnit = 1.65,
                proteinGramsPerUnit = 0.31,
                carbsGramsPerUnit = 0.0,
                fatGramsPerUnit = 0.036,
                barcode = null,
                externalSourceId = null,
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `adding a new stock batch for an existing item is reflected in total on-hand quantity`() = runBlocking {
        // Given a FoodItem "Chicken breast" already exists (see setUp)
        // When the user adds a stock batch of 500g with an expiry date
        dao.insert(
            StockBatchEntity(
                foodItemId = chickenBreastId,
                quantity = 500.0,
                expiryDate = LocalDate.of(2026, 7, 20),
                addedAt = Instant.parse("2026-07-12T00:00:00Z"),
            ),
        )

        // Then the item's total on-hand quantity reflects the addition
        val total = dao.observeTotalOnHand(chickenBreastId).first()
        assertEquals(500.0, total, 0.0001)
    }

    @Test
    fun `total on-hand accumulates across multiple batches for the same item`() = runBlocking {
        dao.insert(
            StockBatchEntity(
                foodItemId = chickenBreastId,
                quantity = 500.0,
                expiryDate = LocalDate.of(2026, 7, 20),
                addedAt = Instant.parse("2026-07-12T00:00:00Z"),
            ),
        )
        dao.insert(
            StockBatchEntity(
                foodItemId = chickenBreastId,
                quantity = 300.0,
                expiryDate = null,
                addedAt = Instant.parse("2026-07-13T00:00:00Z"),
            ),
        )

        val total = dao.observeTotalOnHand(chickenBreastId).first()
        assertEquals(800.0, total, 0.0001)
    }

    @Test
    fun `deleting a stock batch removes it from total on-hand and from future stock calculations`() = runBlocking {
        // Given a StockBatch exists with remaining quantity > 0
        val keepId = dao.insert(
            StockBatchEntity(
                foodItemId = chickenBreastId,
                quantity = 300.0,
                expiryDate = null,
                addedAt = Instant.parse("2026-07-12T00:00:00Z"),
            ),
        )
        val toDeleteId = dao.insert(
            StockBatchEntity(
                foodItemId = chickenBreastId,
                quantity = 500.0,
                expiryDate = LocalDate.of(2026, 7, 20),
                addedAt = Instant.parse("2026-07-13T00:00:00Z"),
            ),
        )
        val toDelete = dao.observeForFoodItem(chickenBreastId).first().first { it.id == toDeleteId }
        assertEquals(800.0, dao.observeTotalOnHand(chickenBreastId).first(), 0.0001)

        // When the user deletes it
        dao.delete(toDelete)

        // Then it MUST NOT be counted in any future stock calculation
        val remaining = dao.observeForFoodItem(chickenBreastId).first()
        assertEquals(1, remaining.size)
        assertEquals(keepId, remaining.first().id)
        assertTrue(remaining.none { it.id == toDeleteId })
        assertEquals(300.0, dao.observeTotalOnHand(chickenBreastId).first(), 0.0001)
    }

    @Test
    fun `total on-hand is zero when the item has no stock batches`() = runBlocking {
        val total = dao.observeTotalOnHand(chickenBreastId).first()
        assertEquals(0.0, total, 0.0001)
    }

    @Test
    fun `getForFoodItem is a one-shot FIFO-ordered read matching observeForFoodItem`() = runBlocking {
        dao.insert(StockBatchEntity(foodItemId = chickenBreastId, quantity = 300.0, expiryDate = null, addedAt = Instant.parse("2026-07-12T00:00:00Z")))
        dao.insert(StockBatchEntity(foodItemId = chickenBreastId, quantity = 500.0, expiryDate = null, addedAt = Instant.parse("2026-07-10T00:00:00Z")))

        val batches = dao.getForFoodItem(chickenBreastId)

        assertEquals(2, batches.size)
        assertEquals(500.0, batches.first().quantity, 0.0001)
        assertEquals(300.0, batches[1].quantity, 0.0001)
    }

    @Test
    fun `observeForFoodItem does not include batches for a different food item`() = runBlocking {
        val riceId = foodItemDao.insert(
            FoodItemEntity(
                name = "Rice",
                canonicalUnit = MeasurementUnit.GRAM,
                source = FoodItemSource.MANUAL,
                caloriesPerUnit = 1.3,
                proteinGramsPerUnit = 0.027,
                carbsGramsPerUnit = 0.28,
                fatGramsPerUnit = 0.003,
                barcode = null,
                externalSourceId = null,
            ),
        )
        dao.insert(
            StockBatchEntity(
                foodItemId = chickenBreastId,
                quantity = 500.0,
                expiryDate = null,
                addedAt = Instant.parse("2026-07-12T00:00:00Z"),
            ),
        )
        dao.insert(
            StockBatchEntity(
                foodItemId = riceId,
                quantity = 1000.0,
                expiryDate = null,
                addedAt = Instant.parse("2026-07-12T00:00:00Z"),
            ),
        )

        val chickenBatches = dao.observeForFoodItem(chickenBreastId).first()

        assertEquals(1, chickenBatches.size)
        assertNull(chickenBatches.first().expiryDate)
        assertEquals(500.0, chickenBatches.first().quantity, 0.0001)
    }
}
