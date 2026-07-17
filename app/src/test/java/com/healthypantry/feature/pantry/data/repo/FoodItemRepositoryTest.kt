package com.healthypantry.feature.pantry.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.database.AppDatabase
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec: Item and Stock Batch CRUD
 * (openspec/changes/pantry-tracker/specs/pantry-stock/spec.md)
 *
 * Exercises [FoodItemRepositoryImpl] against a real in-memory Room database (Robolectric) to
 * prove the repository maps [com.healthypantry.feature.pantry.data.entity.FoodItemEntity] rows
 * to the domain-facing [FoodItem] correctly and does not leak Room types.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodItemRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: FoodItemRepository

    private val testDispatcherProvider = object : DispatcherProvider {
        private val dispatcher = UnconfinedTestDispatcher()
        override val main get() = dispatcher
        override val io get() = dispatcher
        override val default get() = dispatcher
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = FoodItemRepositoryImpl(database.foodItemDao(), testDispatcherProvider)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun chickenBreast() = FoodItem(
        name = "Chicken breast",
        canonicalUnit = MeasurementUnit.GRAM,
        source = FoodItemSource.MANUAL,
        caloriesPerUnit = 1.65,
        proteinGramsPerUnit = 0.31,
        carbsGramsPerUnit = 0.0,
        fatGramsPerUnit = 0.036,
    )

    @Test
    fun `upsert creates a new food item and observeById returns the domain model`() = runTest {
        val id = repository.upsert(chickenBreast())

        val stored = repository.observeById(id).first()

        assertEquals("Chicken breast", stored?.name)
        assertEquals(MeasurementUnit.GRAM, stored?.canonicalUnit)
        assertEquals(FoodItemSource.MANUAL, stored?.source)
    }

    @Test
    fun `upsert with an existing id updates the food item instead of inserting a duplicate`() = runTest {
        val id = repository.upsert(chickenBreast())
        val stored = repository.observeById(id).first()!!

        repository.upsert(stored.copy(caloriesPerUnit = 2.0))

        val all = repository.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(2.0, all.first().caloriesPerUnit!!, 0.0001)
    }

    @Test
    fun `observeAll returns every persisted food item as a domain model`() = runTest {
        repository.upsert(chickenBreast())
        repository.upsert(chickenBreast().copy(name = "Rice", carbsGramsPerUnit = 0.28))

        val all = repository.observeAll().first()

        assertEquals(2, all.size)
        assertEquals(setOf("Chicken breast", "Rice"), all.map { it.name }.toSet())
        assertEquals(0.28, all.first { it.name == "Rice" }.carbsGramsPerUnit!!, 0.0001)
    }

    @Test
    fun `delete removes the food item so it no longer appears in observeAll`() = runTest {
        val id = repository.upsert(chickenBreast())
        val stored = repository.observeById(id).first()!!

        repository.delete(stored)

        assertTrue(repository.observeAll().first().isEmpty())
        assertNull(repository.observeById(id).first())
    }
}
