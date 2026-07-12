package com.healthypantry.feature.pantry.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.healthypantry.app.theme.HealthyPantryTheme
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.ui.vm.ItemFormUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compiles and packages as an instrumented test (`./gradlew connectedDebugAndroidTest`) but is
 * NOT executed in this sandbox - no emulator/device is available here (same precedent as
 * `PantryListScreenTest`). Drives [ItemFormContent], the stateless/presentational half of
 * [ItemFormScreen], directly against hand-built [ItemFormUiState] fixtures - no Hilt/scanner/
 * network wiring needed, following the same "extract the pure/stateless part and test that"
 * principle as the project's mock-hygiene convention.
 */
class ItemFormScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        uiState: ItemFormUiState = ItemFormUiState(),
        isEditing: Boolean = false,
        onNameChanged: (String) -> Unit = {},
        onCanonicalUnitChanged: (MeasurementUnit) -> Unit = {},
        onCaloriesChanged: (String) -> Unit = {},
        onScanBarcodeClick: () -> Unit = {},
        onSearchUsdaClick: (String) -> Unit = {},
        onSaveClick: () -> Unit = {},
        onCancelClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            HealthyPantryTheme {
                ItemFormContent(
                    uiState = uiState,
                    isEditing = isEditing,
                    onNameChanged = onNameChanged,
                    onCanonicalUnitChanged = onCanonicalUnitChanged,
                    onCaloriesChanged = onCaloriesChanged,
                    onProteinChanged = {},
                    onCarbsChanged = {},
                    onFatChanged = {},
                    onUsdaQueryChanged = {},
                    onScanBarcodeClick = onScanBarcodeClick,
                    onSearchUsdaClick = onSearchUsdaClick,
                    onSaveClick = onSaveClick,
                    onCancelClick = onCancelClick,
                )
            }
        }
    }

    @Test
    fun addModeShowsAddTitleAndCoreFormFields() {
        setContent()

        composeTestRule.onNodeWithText("Add item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Calories per unit").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Scan barcode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unit: GRAM").assertIsDisplayed()
    }

    @Test
    fun editModeShowsEditTitleInstead() {
        setContent(isEditing = true)

        composeTestRule.onNodeWithText("Edit item").assertIsDisplayed()
    }

    @Test
    fun scanBarcodeButtonInvokesCallback() {
        var scanned = false
        setContent(onScanBarcodeClick = { scanned = true })

        composeTestRule.onNodeWithContentDescription("Scan barcode").performClick()

        assertTrue(scanned)
    }

    @Test
    fun typingInNameFieldInvokesOnNameChanged() {
        var typed: String? = null
        setContent(onNameChanged = { typed = it })

        composeTestRule.onNodeWithText("Name").performTextInput("Chicken breast")

        assertEquals("Chicken breast", typed)
    }

    @Test
    fun searchUsdaButtonInvokesCallbackWithCurrentQueryText() {
        var searched: String? = null
        setContent(
            uiState = ItemFormUiState(usdaQuery = "banana raw"),
            onSearchUsdaClick = { searched = it },
        )

        composeTestRule.onNodeWithText("Search").performClick()

        assertEquals("banana raw", searched)
    }

    @Test
    fun loadingIndicatorShownWhileLookingUp() {
        setContent(uiState = ItemFormUiState(isLookingUp = true))

        composeTestRule.onNodeWithContentDescription("Looking up nutrition data").assertIsDisplayed()
    }

    @Test
    fun lookupErrorMessageDisplayedWhenPresent() {
        setContent(uiState = ItemFormUiState(lookupError = "No match found. You can enter macros manually."))

        composeTestRule.onNodeWithText("No match found. You can enter macros manually.").assertIsDisplayed()
    }

    @Test
    fun saveButtonInvokesCallback() {
        var saved = false
        setContent(onSaveClick = { saved = true })

        composeTestRule.onNodeWithText("Save").performClick()

        assertTrue(saved)
    }

    @Test
    fun cancelButtonInvokesCallback() {
        var cancelled = false
        setContent(onCancelClick = { cancelled = true })

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertTrue(cancelled)
    }
}
