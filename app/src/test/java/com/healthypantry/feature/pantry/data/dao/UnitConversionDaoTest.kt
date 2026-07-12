package com.healthypantry.feature.pantry.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthypantry.core.database.AppDatabase
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.data.entity.FoodItemEntity
import com.healthypantry.feature.pantry.data.entity.UnitConversionEntity
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec: Unit Conversion Correctness — persistence side. Backs PR2's [com.healthypantry.core.unit.UnitConverter]
 * with real per-`FoodItem` conversion factors (never a global table).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnitConversionDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var foodItemDao: FoodItemDao
    private lateinit var dao: UnitConversionDao
    private var riceId: Long = 0

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        foodItemDao = database.foodItemDao()
        dao = database.unitConversionDao()

        riceId = foodItemDao.insert(
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
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert persists a per-item conversion factor and observeForFoodItem returns it`() = runBlocking {
        dao.insert(
            UnitConversionEntity(
                foodItemId = riceId,
                fromUnit = MeasurementUnit.CUP,
                toUnit = MeasurementUnit.GRAM,
                factor = 185.0,
            ),
        )

        val factors = dao.observeForFoodItem(riceId).first()

        assertEquals(1, factors.size)
        assertEquals(MeasurementUnit.CUP, factors.first().fromUnit)
        assertEquals(185.0, factors.first().factor, 0.0001)
    }

    @Test
    fun `observeForFoodItem does not leak conversion factors registered for a different item`() = runBlocking {
        val flourId = foodItemDao.insert(
            FoodItemEntity(
                name = "Flour",
                canonicalUnit = MeasurementUnit.GRAM,
                source = FoodItemSource.MANUAL,
                caloriesPerUnit = 3.64,
                proteinGramsPerUnit = 0.1,
                carbsGramsPerUnit = 0.76,
                fatGramsPerUnit = 0.01,
                barcode = null,
                externalSourceId = null,
            ),
        )
        dao.insert(UnitConversionEntity(foodItemId = riceId, fromUnit = MeasurementUnit.CUP, toUnit = MeasurementUnit.GRAM, factor = 185.0))
        dao.insert(UnitConversionEntity(foodItemId = flourId, fromUnit = MeasurementUnit.CUP, toUnit = MeasurementUnit.GRAM, factor = 120.0))

        val riceFactors = dao.observeForFoodItem(riceId).first()

        assertEquals(1, riceFactors.size)
        assertEquals(185.0, riceFactors.first().factor, 0.0001)
    }

    @Test
    fun `delete removes a registered conversion factor`() = runBlocking {
        val id = dao.insert(
            UnitConversionEntity(
                foodItemId = riceId,
                fromUnit = MeasurementUnit.CUP,
                toUnit = MeasurementUnit.GRAM,
                factor = 185.0,
            ),
        )
        val stored = dao.observeForFoodItem(riceId).first().first { it.id == id }

        dao.delete(stored)

        assertTrue(dao.observeForFoodItem(riceId).first().isEmpty())
    }
}
