package com.healthypantry.feature.pantry.ui.vm

import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.common.MainDispatcherRule
import com.healthypantry.core.common.Result
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.nutrition.data.NutritionLookupRepository
import com.healthypantry.feature.nutrition.domain.model.NutritionLookupError
import com.healthypantry.feature.nutrition.domain.model.NutritionResult
import com.healthypantry.feature.nutrition.domain.model.NutritionSource
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Spec: "Barcode Scan via Open Food Facts", "Manual Entry with USDA FDC Fallback", "Manual macro
 * override always wins" (nutrition-lookup domain,
 * openspec/changes/pantry-tracker/specs/nutrition-lookup/spec.md).
 *
 * Hand-written fake for [NutritionLookupRepository] (same convention as
 * [PantryViewModelTest][com.healthypantry.feature.pantry.ui.vm.PantryViewModelTest]'s fake
 * repositories).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcherProvider = object : DispatcherProvider {
        private val dispatcher = UnconfinedTestDispatcher()
        override val main get() = dispatcher
        override val io get() = dispatcher
        override val default get() = dispatcher
    }

    private class FakeNutritionLookupRepository(
        private val barcodeResult: Result<NutritionResult, NutritionLookupError>,
        private val searchResult: Result<NutritionResult, NutritionLookupError> = barcodeResult,
    ) : NutritionLookupRepository {
        var lastBarcodeQueried: String? = null
        var lastNameQueried: String? = null

        override suspend fun lookupByBarcode(barcode: String): Result<NutritionResult, NutritionLookupError> {
            lastBarcodeQueried = barcode
            return barcodeResult
        }

        override suspend fun searchByName(name: String): Result<NutritionResult, NutritionLookupError> {
            lastNameQueried = name
            return searchResult
        }
    }

    private fun bananaResult(calories: Double? = 89.0) = NutritionResult(
        name = "Banana, raw",
        caloriesPer100 = calories,
        proteinGramsPer100 = 1.1,
        carbsGramsPer100 = 23.0,
        fatGramsPer100 = 0.3,
        source = NutritionSource.USDA_FOOD_DATA_CENTRAL,
        externalId = "12345",
    )

    private fun buildViewModel(repository: NutritionLookupRepository) =
        ItemFormViewModel(nutritionLookupRepository = repository, dispatcherProvider = testDispatcherProvider)

    @Test
    fun `onBarcodeScanned prefills fields on a successful OFF match`() = runTest {
        val repository = FakeNutritionLookupRepository(barcodeResult = Result.success(bananaResult()))
        val viewModel = buildViewModel(repository)

        viewModel.onBarcodeScanned("0123456789")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("0123456789", repository.lastBarcodeQueried)
        assertEquals("Banana, raw", state.name)
        assertEquals(FoodItemSource.BARCODE, state.source)
        assertEquals(false, state.isLookingUp)
        assertNull(state.lookupError)
    }

    @Test
    fun `onBarcodeScanned falls back to manual entry without blocking on a miss`() = runTest {
        val repository = FakeNutritionLookupRepository(barcodeResult = Result.failure(NutritionLookupError.NotFound))
        val viewModel = buildViewModel(repository)

        viewModel.onBarcodeScanned("0000000000")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertEquals(false, state.isLookingUp)
        assertTrue(state.lookupError!!.isNotBlank())

        // Still fully editable/savable manually - a lookup miss must never block the form.
        viewModel.onNameChanged("Homemade granola")
        viewModel.onCaloriesChanged("4.5")
        val item = viewModel.buildFoodItem()
        assertEquals("Homemade granola", item.name)
        assertEquals(4.5, item.caloriesPerUnit!!, 0.0001)
    }

    @Test
    fun `onUsdaSearch prefills fields on success`() = runTest {
        val repository = FakeNutritionLookupRepository(
            barcodeResult = Result.failure(NutritionLookupError.NotFound),
            searchResult = Result.success(bananaResult()),
        )
        val viewModel = buildViewModel(repository)

        viewModel.onUsdaSearch("banana raw")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("banana raw", repository.lastNameQueried)
        assertEquals("Banana, raw", state.name)
        // FoodItemSource has no USDA-specific variant - USDA-search-assisted entry saves as MANUAL.
        assertEquals(FoodItemSource.MANUAL, state.source)
    }

    @Test
    fun `onUsdaSearch surfaces a rate-limit error without blocking manual entry`() = runTest {
        val repository = FakeNutritionLookupRepository(
            barcodeResult = Result.failure(NutritionLookupError.NotFound),
            searchResult = Result.failure(NutritionLookupError.RateLimited),
        )
        val viewModel = buildViewModel(repository)

        viewModel.onUsdaSearch("banana raw")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLookingUp)
        assertTrue(state.lookupError!!.contains("rate-limited", ignoreCase = true))

        viewModel.onFatChanged("0.3")
        assertEquals(0.3, viewModel.buildFoodItem().fatGramsPerUnit!!, 0.0001)
    }

    @Test
    fun `a manual edit made after a successful lookup is what gets saved`() = runTest {
        val repository = FakeNutritionLookupRepository(barcodeResult = Result.success(bananaResult()))
        val viewModel = buildViewModel(repository)

        viewModel.onBarcodeScanned("0123456789")
        advanceUntilIdle()
        viewModel.onCaloriesChanged("1.0")

        val item = viewModel.buildFoodItem()
        assertEquals(1.0, item.caloriesPerUnit!!, 0.0001)
    }

    @Test
    fun `an unexpected exception from a lookup surfaces as an error instead of crashing`() = runTest {
        val repository = object : NutritionLookupRepository {
            override suspend fun lookupByBarcode(barcode: String): Result<NutritionResult, NutritionLookupError> {
                throw IllegalStateException("boom")
            }

            override suspend fun searchByName(name: String): Result<NutritionResult, NutritionLookupError> {
                throw IllegalStateException("boom")
            }
        }
        val viewModel = buildViewModel(repository)

        viewModel.onBarcodeScanned("0123456789")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLookingUp)
        assertTrue(state.lookupError!!.isNotBlank())

        // Still fully editable/savable manually - an unexpected lookup failure must never block the form.
        viewModel.onNameChanged("Homemade granola")
        assertEquals("Homemade granola", viewModel.buildFoodItem().name)
    }

    @Test
    fun `loadExisting seeds the form and marks every field touched so a stray lookup cannot clobber it`() = runTest {
        val repository = FakeNutritionLookupRepository(barcodeResult = Result.success(bananaResult(calories = 999.0)))
        val viewModel = buildViewModel(repository)
        val existing = FoodItem(
            id = 9L,
            name = "Rice",
            canonicalUnit = MeasurementUnit.GRAM,
            source = FoodItemSource.MANUAL,
            caloriesPerUnit = 1.3,
            proteinGramsPerUnit = 0.028,
            carbsGramsPerUnit = 0.28,
            fatGramsPerUnit = 0.003,
        )

        viewModel.loadExisting(existing)
        viewModel.onBarcodeScanned("0123456789")
        advanceUntilIdle()

        val item = viewModel.buildFoodItem(existingId = 9L)
        assertEquals(9L, item.id)
        assertEquals("Rice", item.name)
        assertEquals(1.3, item.caloriesPerUnit!!, 0.0001)
    }

    @Test
    fun `loadExisting seeds an unknown macro as a blank field, not the literal text null, and it round-trips back to null on save`() = runTest {
        // Given a previously-saved item where fat was never entered (spec "Missing macro data on an item")
        val repository = FakeNutritionLookupRepository(barcodeResult = Result.failure(NutritionLookupError.NotFound))
        val viewModel = buildViewModel(repository)
        val existing = FoodItem(
            id = 11L,
            name = "Homemade granola",
            canonicalUnit = MeasurementUnit.GRAM,
            source = FoodItemSource.MANUAL,
            caloriesPerUnit = 4.5,
            proteinGramsPerUnit = 0.1,
            carbsGramsPerUnit = 0.6,
            fatGramsPerUnit = null,
        )

        // When the edit form is seeded from it
        viewModel.loadExisting(existing)

        // Then the fat field renders blank, not the string "null"
        assertEquals("", viewModel.uiState.value.fatGramsPerUnit)

        // And saving without touching that field round-trips it back to null, not 0.0
        val resaved = viewModel.buildFoodItem(existingId = 11L)
        assertNull(resaved.fatGramsPerUnit)
        assertEquals(4.5, resaved.caloriesPerUnit!!, 0.0001)
    }
}
