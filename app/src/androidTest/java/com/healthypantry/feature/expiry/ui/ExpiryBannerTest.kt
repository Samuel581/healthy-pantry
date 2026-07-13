package com.healthypantry.feature.expiry.ui

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.healthypantry.app.theme.HealthyPantryTheme
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.expiry.domain.model.ExpiringBatch
import com.healthypantry.feature.expiry.domain.model.ExpiryStatus
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.pantry.domain.model.StockBatch
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Compiles and packages as an instrumented test (`./gradlew connectedDebugAndroidTest`) but is
 * NOT executed in this sandbox — no emulator/device available here (same precedent as
 * `PantryListScreenTest`).
 */
class ExpiryBannerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun expiringBatch(
        name: String = "Yogurt",
        status: ExpiryStatus = ExpiryStatus.EXPIRING_SOON,
    ) = ExpiringBatch(
        foodItem = FoodItem(
            id = 1L,
            name = name,
            canonicalUnit = MeasurementUnit.GRAM,
            source = FoodItemSource.MANUAL,
            caloriesPerUnit = 1.0,
            proteinGramsPerUnit = 1.0,
            carbsGramsPerUnit = 1.0,
            fatGramsPerUnit = 1.0,
        ),
        stockBatch = StockBatch(
            id = 1L,
            foodItemId = 1L,
            quantity = 200.0,
            expiryDate = LocalDate.now().plusDays(2),
            addedAt = Instant.EPOCH,
        ),
        status = status,
    )

    @Test
    fun bannerListsEachExpiringItemName() {
        composeTestRule.setContent {
            HealthyPantryTheme {
                ExpiryBanner(expiringItems = listOf(expiringBatch(name = "Yogurt"), expiringBatch(name = "Milk")))
            }
        }

        composeTestRule.onNodeWithText("Expiring soon (2)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Yogurt").assertIsDisplayed()
        composeTestRule.onNodeWithText("Milk").assertIsDisplayed()
    }

    @Test
    fun bannerLabelsAnExpiredItemDistinctlyFromExpiringSoon() {
        composeTestRule.setContent {
            HealthyPantryTheme {
                ExpiryBanner(expiringItems = listOf(expiringBatch(name = "Milk", status = ExpiryStatus.EXPIRED)))
            }
        }

        composeTestRule.onNodeWithText("Milk (expired)").assertIsDisplayed()
    }

    @Test
    fun bannerRendersNothingWhenNoItemsAreExpiring() {
        composeTestRule.setContent {
            HealthyPantryTheme {
                ExpiryBanner(expiringItems = emptyList())
            }
        }

        composeTestRule.onNodeWithContentDescription("Expiring soon banner").assertDoesNotExist()
    }
}
