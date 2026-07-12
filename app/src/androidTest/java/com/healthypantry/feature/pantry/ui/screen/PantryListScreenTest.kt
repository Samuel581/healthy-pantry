package com.healthypantry.feature.pantry.ui.screen

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.healthypantry.app.theme.HealthyPantryTheme
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.pantry.ui.vm.PantryItemUi
import com.healthypantry.feature.pantry.ui.vm.PantryUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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

    private fun foodItem(id: Long = 1L, name: String = "Chicken breast") = FoodItem(
        id = id,
        name = name,
        canonicalUnit = MeasurementUnit.GRAM,
        source = FoodItemSource.MANUAL,
        caloriesPerUnit = 1.65,
        proteinGramsPerUnit = 0.31,
        carbsGramsPerUnit = 0.0,
        fatGramsPerUnit = 0.036,
    )

    @Test
    fun listRendersEachPantryItemNameAndStock() {
        val state = PantryUiState(
            items = listOf(
                PantryItemUi(
                    foodItem(id = 1L, name = "Chicken breast"),
                    actualStock = 500.0,
                    projectedStock = 500.0,
                ),
                PantryItemUi(
                    foodItem(id = 2L, name = "Brown rice"),
                    actualStock = 1200.0,
                    projectedStock = 950.0,
                ),
            ),
            isLoading = false,
        )

        composeTestRule.setContent {
            HealthyPantryTheme {
                PantryListContent(uiState = state, onAddItem = {}, onDeleteItem = {})
            }
        }

        composeTestRule.onNodeWithText("Chicken breast").assertIsDisplayed()
        composeTestRule.onNodeWithText("Brown rice").assertIsDisplayed()
        composeTestRule.onNodeWithText("Actual: 500.0 · Projected: 500.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Actual: 1200.0 · Projected: 950.0").assertIsDisplayed()
    }

    @Test
    fun emptyStateMessageShownWhenNoItemsAndNotLoading() {
        composeTestRule.setContent {
            HealthyPantryTheme {
                PantryListContent(
                    uiState = PantryUiState(items = emptyList(), isLoading = false),
                    onAddItem = {},
                    onDeleteItem = {},
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
                PantryListContent(uiState = state, onAddItem = {}, onDeleteItem = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("Loading pantry items").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chicken breast").assertDoesNotExist()
    }

    @Test
    fun tappingDeleteInvokesCallbackWithTheTappedItemsFoodItem() {
        var deleted: FoodItem? = null
        val target = foodItem(id = 42L, name = "Chicken breast")
        val state = PantryUiState(
            items = listOf(PantryItemUi(target, actualStock = 500.0, projectedStock = 500.0)),
            isLoading = false,
        )

        composeTestRule.setContent {
            HealthyPantryTheme {
                PantryListContent(uiState = state, onAddItem = {}, onDeleteItem = { deleted = it })
            }
        }

        composeTestRule.onNodeWithContentDescription("Delete Chicken breast").performClick()

        assertEquals(target, deleted)
    }
}
