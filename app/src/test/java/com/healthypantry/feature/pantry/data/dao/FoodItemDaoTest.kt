package com.healthypantry.feature.pantry.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthypantry.core.database.AppDatabase
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.data.entity.FoodItemEntity
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec: Item and Stock Batch CRUD
 * (openspec/changes/pantry-tracker/specs/pantry-stock/spec.md)
 *
 * Robolectric + in-memory Room per design.md "Testing strategy" — fast DAO-correctness
 * feedback without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodItemDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: FoodItemDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.foodItemDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun chickenBreast() = FoodItemEntity(
        name = "Chicken breast",
        canonicalUnit = MeasurementUnit.GRAM,
        source = FoodItemSource.MANUAL,
        caloriesPerUnit = 1.65,
        proteinGramsPerUnit = 0.31,
        carbsGramsPerUnit = 0.0,
        fatGramsPerUnit = 0.036,
        barcode = null,
        externalSourceId = null,
    )

    private fun oatsFromBarcode() = FoodItemEntity(
        name = "Rolled oats",
        canonicalUnit = MeasurementUnit.GRAM,
        source = FoodItemSource.BARCODE,
        caloriesPerUnit = 3.89,
        proteinGramsPerUnit = 0.169,
        carbsGramsPerUnit = 0.663,
        fatGramsPerUnit = 0.069,
        barcode = "0123456789012",
        externalSourceId = "off:0123456789012",
    )

    @Test
    fun `insert persists a manually-entered food item and observeById returns it`() = runBlocking {
        val id = dao.insert(chickenBreast())

        val stored = dao.observeById(id).first()

        assertEquals("Chicken breast", stored?.name)
        assertEquals(MeasurementUnit.GRAM, stored?.canonicalUnit)
        assertEquals(FoodItemSource.MANUAL, stored?.source)
        assertNull(stored?.barcode)
    }

    @Test
    fun `insert persists a barcode-sourced food item with its external source id`() = runBlocking {
        val id = dao.insert(oatsFromBarcode())

        val stored = dao.observeById(id).first()

        assertEquals(FoodItemSource.BARCODE, stored?.source)
        assertEquals("0123456789012", stored?.barcode)
        assertEquals("off:0123456789012", stored?.externalSourceId)
    }

    @Test
    fun `observeAll returns every inserted food item`() = runBlocking {
        dao.insert(chickenBreast())
        dao.insert(oatsFromBarcode())

        val all = dao.observeAll().first()

        assertEquals(2, all.size)
        assertEquals(setOf("Chicken breast", "Rolled oats"), all.map { it.name }.toSet())
    }

    @Test
    fun `update overwrites macros for an existing food item`() = runBlocking {
        val id = dao.insert(chickenBreast())
        val stored = dao.observeById(id).first()!!

        dao.update(stored.copy(caloriesPerUnit = 2.0))

        val updated = dao.observeById(id).first()
        assertEquals(2.0, updated?.caloriesPerUnit ?: -1.0, 0.0001)
    }

    @Test
    fun `delete removes the food item`() = runBlocking {
        val id = dao.insert(chickenBreast())
        val stored = dao.observeById(id).first()!!

        dao.delete(stored)

        assertNull(dao.observeById(id).first())
    }
}
