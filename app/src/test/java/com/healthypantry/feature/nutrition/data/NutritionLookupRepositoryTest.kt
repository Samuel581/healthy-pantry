package com.healthypantry.feature.nutrition.data

import com.healthypantry.feature.nutrition.data.off.OffProduct
import com.healthypantry.feature.nutrition.data.off.OffProductResponse
import com.healthypantry.feature.nutrition.data.off.OpenFoodFactsApi
import com.healthypantry.feature.nutrition.data.off.OpenFoodFactsNutritionSource
import com.healthypantry.feature.nutrition.data.usda.UsdaFood
import com.healthypantry.feature.nutrition.data.usda.UsdaFoodDataCentralApi
import com.healthypantry.feature.nutrition.data.usda.UsdaNutritionSource
import com.healthypantry.feature.nutrition.data.usda.UsdaSearchResponse
import com.healthypantry.feature.nutrition.domain.model.NutritionLookupError
import com.healthypantry.feature.nutrition.domain.model.NutritionSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec: Barcode Scan, Manual Entry with USDA Fallback
 * (openspec/changes/pantry-tracker/specs/nutrition-lookup/spec.md)
 *
 * Exercises [NutritionLookupRepositoryImpl] against hand-written fake [OpenFoodFactsApi]/
 * [UsdaFoodDataCentralApi] implementations (same "hand-written fake" pattern as
 * `ComputeProjectedStockUseCaseTest`) wrapped in the *real* [OpenFoodFactsNutritionSource]/
 * [UsdaNutritionSource] classes. This proves only the repository's delegation/composition
 * rule — mapping and error-handling correctness for each source already have dedicated
 * MockWebServer coverage in `OpenFoodFactsNutritionSourceTest`/`UsdaNutritionSourceTest`.
 */
class NutritionLookupRepositoryTest {

    /** Hand-written fake — always returns [response] regardless of the requested barcode. */
    private class FakeOpenFoodFactsApi(private val response: OffProductResponse) : OpenFoodFactsApi {
        override suspend fun getProduct(barcode: String): OffProductResponse = response
    }

    /** Hand-written fake — always returns [response] regardless of the requested query. */
    private class FakeUsdaFoodDataCentralApi(private val response: UsdaSearchResponse) : UsdaFoodDataCentralApi {
        override suspend fun searchFoods(query: String, apiKey: String): UsdaSearchResponse = response
    }

    private val offHitResponse = OffProductResponse(
        status = 1,
        product = OffProduct(productName = "Peanut Butter"),
    )
    private val offMissResponse = OffProductResponse(status = 0, product = null)

    private val usdaHitResponse = UsdaSearchResponse(
        totalHits = 1,
        foods = listOf(UsdaFood(fdcId = 747447, description = "Broccoli, raw")),
    )

    @Test
    fun `lookupByBarcode delegates to Open Food Facts and returns its result as-is on a hit`() = runTest {
        // Given Open Food Facts has a match for the scanned barcode
        val repository = NutritionLookupRepositoryImpl(
            openFoodFactsSource = OpenFoodFactsNutritionSource(FakeOpenFoodFactsApi(offHitResponse)),
            usdaSource = UsdaNutritionSource(FakeUsdaFoodDataCentralApi(UsdaSearchResponse())),
        )

        // When the user scans the barcode
        val result = repository.lookupByBarcode("0123456789012")

        // Then the repository passes through Open Food Facts' successful result unchanged
        val value = result.getOrNull()
        assertEquals("Peanut Butter", value?.name)
        assertEquals(NutritionSource.OPEN_FOOD_FACTS, value?.source)
    }

    @Test
    fun `lookupByBarcode surfaces an Open Food Facts miss as-is without querying USDA`() = runTest {
        // Given Open Food Facts has no match for the scanned barcode (spec scenario "Barcode
        // not found in Open Food Facts") and USDA would have answered if queried
        val repository = NutritionLookupRepositoryImpl(
            openFoodFactsSource = OpenFoodFactsNutritionSource(FakeOpenFoodFactsApi(offMissResponse)),
            usdaSource = UsdaNutritionSource(FakeUsdaFoodDataCentralApi(usdaHitResponse)),
        )

        // When the user scans the barcode
        val result = repository.lookupByBarcode("0000000000000")

        // Then the miss surfaces to the caller unchanged; USDA (which has no barcode search) is
        // never consulted here — the caller is responsible for routing to manual name-search
        assertEquals(NutritionLookupError.NotFound, result.errorOrNull())
    }

    @Test
    fun `searchByName delegates to USDA FoodData Central`() = runTest {
        // Given USDA has a match for the manually entered name
        val repository = NutritionLookupRepositoryImpl(
            openFoodFactsSource = OpenFoodFactsNutritionSource(FakeOpenFoodFactsApi(offMissResponse)),
            usdaSource = UsdaNutritionSource(FakeUsdaFoodDataCentralApi(usdaHitResponse)),
        )

        // When the user searches by name (spec scenario "Manual name search against USDA
        // FoodData Central")
        val result = repository.searchByName("raw broccoli")

        // Then the repository returns USDA's match
        val value = result.getOrNull()
        assertEquals("Broccoli, raw", value?.name)
        assertEquals(NutritionSource.USDA_FOOD_DATA_CENTRAL, value?.source)
    }
}
