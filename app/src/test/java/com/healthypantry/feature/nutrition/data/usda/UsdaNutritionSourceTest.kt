package com.healthypantry.feature.nutrition.data.usda

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
 * Exercises [UsdaNutritionSource] against a fake HTTP server (MockWebServer, see design.md
 * "Testing strategy") so the DTO -> [com.healthypantry.feature.nutrition.domain.model.NutritionResult]
 * mapping, miss handling, and rate-limit handling are verified without any real network call.
 */
class UsdaNutritionSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: UsdaNutritionSource

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
        source = UsdaNutritionSource(retrofit.create(UsdaFoodDataCentralApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `searchByName returns a NutritionResult for the best match`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "totalHits": 1,
                  "foods": [
                    {
                      "fdcId": 747447,
                      "description": "Broccoli, raw",
                      "foodNutrients": [
                        {"nutrientId": 1008, "nutrientName": "Energy", "value": 34.0},
                        {"nutrientId": 1003, "nutrientName": "Protein", "value": 2.82},
                        {"nutrientId": 1005, "nutrientName": "Carbohydrate, by difference", "value": 6.64},
                        {"nutrientId": 1004, "nutrientName": "Total lipid (fat)", "value": 0.37}
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = source.searchByName("raw broccoli")

        val value = result.getOrNull()
        assertEquals("Broccoli, raw", value?.name)
        assertEquals(34.0, value?.caloriesPer100 ?: -1.0, 0.0001)
        assertEquals(2.82, value?.proteinGramsPer100 ?: -1.0, 0.0001)
        assertEquals(6.64, value?.carbsGramsPer100 ?: -1.0, 0.0001)
        assertEquals(0.37, value?.fatGramsPer100 ?: -1.0, 0.0001)
        assertEquals(NutritionSource.USDA_FOOD_DATA_CENTRAL, value?.source)
        assertEquals("747447", value?.externalId)
    }

    @Test
    fun `searchByName returns NotFound when USDA has no match`() = runTest {
        server.enqueue(MockResponse().setBody("""{"totalHits": 0, "foods": []}"""))

        val result = source.searchByName("a food that does not exist")

        assertEquals(NutritionLookupError.NotFound, result.errorOrNull())
    }

    @Test
    fun `searchByName returns RateLimited distinct from NotFound on a 429 response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))

        val result = source.searchByName("raw broccoli")

        assertEquals(NutritionLookupError.RateLimited, result.errorOrNull())
    }

    @Test
    fun `searchByName surfaces a network error distinctly from a miss`() = runTest {
        server.shutdown()

        val result = source.searchByName("raw broccoli")

        assertTrue(result.errorOrNull() is NutritionLookupError.NetworkError)
    }

    @Test
    fun `searchByName surfaces other non-2xx responses as ApiError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = source.searchByName("raw broccoli")

        val error = result.errorOrNull()
        assertTrue(error is NutritionLookupError.ApiError)
        assertEquals(500, (error as NutritionLookupError.ApiError).code)
    }

    @Test
    fun `searchByName leaves missing macro fields null instead of defaulting to zero`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "totalHits": 1,
                  "foods": [
                    {
                      "fdcId": 123456,
                      "description": "Mystery Ingredient",
                      "foodNutrients": [
                        {"nutrientId": 1008, "nutrientName": "Energy", "value": 120.0}
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = source.searchByName("mystery ingredient")

        val value = result.getOrNull()
        assertEquals(120.0, value?.caloriesPer100 ?: -1.0, 0.0001)
        assertEquals(null, value?.proteinGramsPer100)
        assertEquals(null, value?.carbsGramsPer100)
        assertEquals(null, value?.fatGramsPer100)
    }
}
