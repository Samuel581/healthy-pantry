package com.healthypantry.feature.pantry.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthypantry.core.database.AppDatabase
import com.healthypantry.core.unit.ConversionFactor
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.data.entity.FoodItemEntity
import com.healthypantry.feature.pantry.data.entity.UnitConversionEntity
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec: "Unit Conversion Correctness" (sdd/pantry-tracker/spec) — repository-level guarantee that
 * [UnitConversionRepositoryImpl] maps [UnitConversionEntity] rows to domain-facing
 * [ConversionFactor]s without leaking Room types, for use by the planning-domain macro-rollup/
 * weekly-needs/mark-eaten use-cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnitConversionRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: UnitConversionRepository
    private var riceId: Long = 0

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = UnitConversionRepositoryImpl(database.unitConversionDao())

        riceId = database.foodItemDao().insert(
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
    fun `observeForFoodItem maps persisted conversion rows to domain ConversionFactors`() = runTest {
        database.unitConversionDao().insert(
            UnitConversionEntity(foodItemId = riceId, fromUnit = MeasurementUnit.CUP, toUnit = MeasurementUnit.GRAM, factor = 185.0),
        )

        val factors = repository.observeForFoodItem(riceId).first()

        assertEquals(listOf(ConversionFactor(fromUnit = MeasurementUnit.CUP, toUnit = MeasurementUnit.GRAM, factor = 185.0)), factors)
    }

    @Test
    fun `observeForFoodItem returns an empty list when no conversion is registered`() = runTest {
        val factors = repository.observeForFoodItem(riceId).first()

        assertEquals(emptyList<ConversionFactor>(), factors)
    }
}
