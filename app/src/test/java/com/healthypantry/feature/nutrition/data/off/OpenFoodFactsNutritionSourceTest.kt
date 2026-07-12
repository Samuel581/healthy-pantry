package com.healthypantry.feature.nutrition.data.off

import com.healthypantry.feature.nutrition.domain.model.NutritionLookupError
import com.healthypantry.feature.nutrition.domain.model.NutritionSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Spec: Barcode Scan, Manual Entry with USDA Fallback
 * (openspec/changes/pantry-tracker/specs/nutrition-lookup/spec.md)
 *
 * Exercises [OpenFoodFactsNutritionSource] against a fake HTTP server (MockWebServer, see
 * design.md "Testing strategy") so the DTO -> [com.healthypantry.feature.nutrition.domain.model.NutritionResult]
 * mapping and miss/error handling are verified without any real network call.
 */
class OpenFoodFactsNutritionSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: OpenFoodFactsNutritionSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        source = OpenFoodFactsNutritionSource(retrofit.create(OpenFoodFactsApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `lookupByBarcode returns a NutritionResult when Open Food Facts has a match`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "status": 1,
                  "product": {
                    "product_name": "Peanut Butter",
                    "nutriments": {
                      "energy-kcal_100g": 588.0,
                      "proteins_100g": 25.0,
                      "carbohydrates_100g": 20.0,
                      "fat_100g": 50.0
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = source.lookupByBarcode("0123456789012")

        val value = result.getOrNull()
        assertEquals("Peanut Butter", value?.name)
        assertEquals(588.0, value?.caloriesPer100 ?: -1.0, 0.0001)
        assertEquals(25.0, value?.proteinGramsPer100 ?: -1.0, 0.0001)
        assertEquals(20.0, value?.carbsGramsPer100 ?: -1.0, 0.0001)
        assertEquals(50.0, value?.fatGramsPer100 ?: -1.0, 0.0001)
        assertEquals(NutritionSource.OPEN_FOOD_FACTS, value?.source)
    }

    @Test
    fun `lookupByBarcode returns NotFound when Open Food Facts has no match`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"status": 0, "status_verbose": "product not found"}"""),
        )

        val result = source.lookupByBarcode("0000000000000")

        assertEquals(NutritionLookupError.NotFound, result.errorOrNull())
    }

    @Test
    fun `lookupByBarcode surfaces a network error distinctly from a miss`() = runTest {
        server.shutdown()

        val result = source.lookupByBarcode("0123456789012")

        assertTrue(result.errorOrNull() is NutritionLookupError.NetworkError)
    }

    @Test
    fun `lookupByBarcode returns NotFound for a 404 response, not an ApiError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = source.lookupByBarcode("0000000000000")

        assertEquals(NutritionLookupError.NotFound, result.errorOrNull())
    }

    @Test
    fun `lookupByBarcode leaves missing macro fields null instead of defaulting to zero`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "status": 1,
                  "product": {
                    "product_name": "Mystery Snack",
                    "nutriments": {
                      "energy-kcal_100g": 120.0
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = source.lookupByBarcode("0123456789012")

        val value = result.getOrNull()
        assertEquals(120.0, value?.caloriesPer100 ?: -1.0, 0.0001)
        assertEquals(null, value?.proteinGramsPer100)
        assertEquals(null, value?.carbsGramsPer100)
        assertEquals(null, value?.fatGramsPer100)
    }
}
