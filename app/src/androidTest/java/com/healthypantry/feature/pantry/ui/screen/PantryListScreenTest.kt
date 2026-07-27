package com.healthypantry.feature.pantry.ui.screen

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.healthypantry.app.theme.HealthyPantryTheme
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.expiry.domain.model.ExpiringBatch
import com.healthypantry.feature.expiry.domain.model.ExpiryStatus
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.pantry.domain.model.StockBatch
import com.healthypantry.feature.pantry.ui.vm.PantryItemUi
import com.healthypantry.feature.pantry.ui.vm.PantryUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Compiles and packages as an instrumented test (`./gradlew connectedDebugAndroidTest`) but is
 * NOT executed in this sandbox — no emulator/device is available here (same precedent as PR1's
 * `HarnessInstrumentedSmokeTest`). Drives [PantryListContent], the stateless/presentational half
 * of [PantryListScreen], directly against hand-built [PantryUiState] fixtures — no Hilt/ViewModel
 * wiring needed, following the same "extract the pure/stateless part and test that" principle as
 * the project's mock-hygiene convention.
 */
class PantryListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Macro values are deliberately exact at one decimal place (not e.g. 1.65) so
    // "%.1f".format(...) can't hit a rounding-tie ambiguity (1.65 is stored as the double
    // 1.64999999999999991..., which "%.1f" rounds DOWN to "1.6", not "1.7").
    private fun foodItem(id: Long = 1L, name: String = "Chicken breast") = FoodItem(
        id = id,
        name = name,
        canonicalUnit = MeasurementUnit.GRAM,
        source = FoodItemSource.MANUAL,
        caloriesPerUnit = 1.5,
        proteinGramsPerUnit = 0.3,
        carbsGramsPerUnit = 0.0,
        fatGramsPerUnit = 0.1,
    )

    @Test
    fun listRendersEachPantryItemNameAndProjectedStockByDefault() {
        val state = PantryUiState(
            items = listOf(
                PantryItemUi(foodItem(id = 1L, name = "Chicken breast"), actualStock = 500.0, projectedStock = 300.0, batchCount = 1),
                PantryItemUi(foodItem(id = 2L, name = "Brown rice"), actualStock = 1200.0, projectedStock = 950.0, batchCount = 2),
            ),
            isLoading = false,
        )

        composeTestRule.setContent {
            HealthyPantryTheme {
                PantryListContent(uiState = state, onAddItem = {}, onEditItem = {})
            }
        }

        composeTestRule.onNodeWithText("Chicken breast").assertIsDisplayed()
        composeTestRule.onNodeWithText("Brown rice").assertIsDisplayed()
        // Defaults to the Projected view (mockup: "Projected" segment checked initially).
        composeTestRule.onNodeWithText("300.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("950.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 batch").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 batches").assertIsDisplayed()
    }

    @Test
    fun tappingActualSwitchesEachCardToActualStock() {
        val state = PantryUiState(
            items = listOf(
                PantryItemUi(foodItem(id = 1L, name = "Chicken breast"), actualStock = 500.0, projectedStock = 300.0),
            ),
            isLoading = false,
        )

        composeTestRule.setContent {
            HealthyPantryTheme {
                PantryListContent(uiState = state, onAddItem = {}, onEditItem = {})
            }
        }

        composeTestRule.onNodeWithText("300.0").assertIsDisplayed()

        composeTestRule.onNodeWithText("Actual").performClick()

        composeTestRule.onNodeWithText("500.0").assertIsDisplayed()
    }

    @Test
    fun cardShowsMacroTagsAsPerUnitValuesNotPer100() {
        val state = PantryUiState(
            items = listOf(PantryItemUi(foodItem(), actualStock = 500.0, projectedStock = 500.0)),
            isLoading = false,
        )

        composeTestRule.setContent {
            HealthyPantryTheme {
                PantryListContent(uiState = state, onAddItem = {}, onEditItem = {})
            }
        }

        // FoodItem.caloriesPerUnit etc. are per ONE canonical unit, not per-100 - the card must
        // display the raw per-unit value ("1.5 kcal/g"), never a x100-scaled label.
        composeTestRule.onNodeWithText("1.5 kcal/g").assertIsDisplayed()
        composeTestRule.onNodeWithText("0.3g P").assertIsDisplayed()
    }

    @Test
    fun emptyStateMessageShownWhenNoItemsAndNotLoading() {
        composeTestRule.setContent {
            HealthyPantryTheme {
                PantryListContent(
                    uiState = PantryUiState(items = emptyList(), isLoading = false),
                    onAddItem = {},
                    onEditItem = {},
                )
            }
        }

        composeTestRule.onNodeWithText("No pantry items yet. Tap + to add one.").assertIsDisplayed()
    }

    @Test
    fun loadingIndicatorShownAndListHiddenWhileLoading() {
        val state = PantryUiState(
            items = listOf(PantryItemUi(foodItem(), actualStock = 500.0, projectedStock = 500.0)),
            isLoading = true,
        )

        composeTestRule.setContent {
            HealthyPantryTheme {
                PantryListContent(uiState = state, onAddItem = {}, onEditItem = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("Loading pantry items").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chicken breast").assertDoesNotExist()
    }

    @Test
    fun tappingACardInvokesOnEditItemWithTheTappedItemsFoodItem() {
        var opened: FoodItem? = null
        val target = foodItem(id = 42L, name = "Chicken breast")
        val state = PantryUiState(
            items = listOf(PantryItemUi(target, actualStock = 500.0, projectedStock = 500.0)),
            isLoading = false,
        )

        composeTestRule.setContent {
            HealthyPantryTheme {
                PantryListContent(uiState = state, onAddItem = {}, onEditItem = { opened = it })
            }
        }

        composeTestRule.onNodeWithContentDescription("Open Chicken breast").performClick()

        assertEquals(target, opened)
    }

    @Test
    fun tappingTheFabInvokesOnAddItem() {
        var added = false
        composeTestRule.setContent {
            HealthyPantryTheme {
                PantryListContent(
                    uiState = PantryUiState(items = emptyList(), isLoading = false),
                    onAddItem = { added = true },
                    onEditItem = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Add item").performClick()

        assertEquals(true, added)
    }

    @Test
    fun bellShowsABadgeDotOnlyWhenAnItemIsExpiringSoon() {
        val expiring = listOf(
            ExpiringBatch(
                foodItem = foodItem(),
                stockBatch = StockBatch(
                    id = 1L,
                    foodItemId = 1L,
                    quantity = 200.0,
                    expiryDate = LocalDate.now().plusDays(1),
                    addedAt = Instant.EPOCH,
                ),
                status = ExpiryStatus.EXPIRING_SOON,
            ),
        )
        val state = PantryUiState(
            items = listOf(PantryItemUi(foodItem(), actualStock = 500.0, projectedStock = 500.0)),
            isLoading = false,
        )

        composeTestRule.setContent {
            HealthyPantryTheme {
                PantryListContent(uiState = state, expiringItems = expiring, onAddItem = {}, onEditItem = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("Items expiring soon").assertIsDisplayed()
    }

    @Test
    fun bellShowsNoBadgeDotWhenNothingIsExpiring() {
        val state = PantryUiState(
            items = listOf(PantryItemUi(foodItem(), actualStock = 500.0, projectedStock = 500.0)),
            isLoading = false,
        )

        composeTestRule.setContent {
            HealthyPantryTheme {
                PantryListContent(uiState = state, expiringItems = emptyList(), onAddItem = {}, onEditItem = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("Items expiring soon").assertDoesNotExist()
    }

    @Test
    fun cardTagsTheSameItemsExpiringBatchAlongsideTheBanner() {
        val target = foodItem(id = 1L, name = "Chicken breast")
        val expiring = listOf(
            ExpiringBatch(
                foodItem = target,
                stockBatch = StockBatch(
                    id = 1L,
                    foodItemId = 1L,
                    quantity = 200.0,
                    expiryDate = LocalDate.now().plusDays(2),
                    addedAt = Instant.EPOCH,
                ),
                status = ExpiryStatus.EXPIRING_SOON,
            ),
        )
        val state = PantryUiState(
            items = listOf(PantryItemUi(target, actualStock = 500.0, projectedStock = 500.0)),
            isLoading = false,
        )

        composeTestRule.setContent {
            HealthyPantryTheme {
                PantryListContent(
                    uiState = state,
                    expiringItems = expiring,
                    onAddItem = {},
                    onEditItem = {},
                    today = LocalDate.now(),
                )
            }
        }

        composeTestRule.onNodeWithText("2d left").assertIsDisplayed()
    }
}
