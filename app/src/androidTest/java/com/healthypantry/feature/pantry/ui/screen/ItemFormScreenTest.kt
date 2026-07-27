package com.healthypantry.feature.pantry.ui.screen

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.healthypantry.app.theme.HealthyPantryTheme
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.ui.vm.ConversionRowState
import com.healthypantry.feature.pantry.ui.vm.ItemFormUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Compiles and packages as an instrumented test (`./gradlew connectedDebugAndroidTest`) but is
 * NOT executed in this sandbox - no emulator/device is available here (same precedent as
 * `PantryListScreenTest`). Drives [ItemFormContent], the stateless/presentational half of
 * [ItemFormScreen], directly against hand-built [ItemFormUiState] fixtures - no Hilt/scanner/
 * network wiring needed, following the same "extract the pure/stateless part and test that"
 * principle as the project's mock-hygiene convention.
 *
 * A brand-new item (`isEditing = false`) starts at the "Scan barcode" / "Manual entry" choice
 * (`FormMode.CHOICE`, private to `ItemFormScreen.kt`); an edit (`isEditing = true`) skips straight
 * to manual mode, pre-filled - most tests below exercise manual-mode fields via
 * `isEditing = true` (matching real edit behavior) rather than clicking through "Manual entry"
 * every time; [addModeStartsAtTheScanOrManualChoice] and [manualEntryCardEntersManualMode] cover
 * the add-mode choice step itself.
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
        onAddConversionRow: () -> Unit = {},
        onUpdateConversionRow: (Int, ConversionRowState) -> Unit = { _, _ -> },
        onRemoveConversionRow: (Int) -> Unit = {},
        onSaveClick: (Double?, LocalDate?) -> Unit = { _, _ -> },
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
                    onResultSelected = {},
                    onAddConversionRow = onAddConversionRow,
                    onUpdateConversionRow = onUpdateConversionRow,
                    onRemoveConversionRow = onRemoveConversionRow,
                    onSaveClick = onSaveClick,
                    onCancelClick = onCancelClick,
                )
            }
        }
    }

    @Test
    fun addModeStartsAtTheScanOrManualChoice() {
        setContent()

        composeTestRule.onNodeWithText("Add item").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Scan barcode").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Manual entry").assertIsDisplayed()
        composeTestRule.onNodeWithText("Name").assertDoesNotExist()
    }

    @Test
    fun manualEntryCardEntersManualMode() {
        setContent()

        composeTestRule.onNodeWithContentDescription("Manual entry").performClick()

        composeTestRule.onNodeWithText("Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kcal").assertIsDisplayed()
    }

    @Test
    fun scanBarcodeCardInvokesCallback() {
        var scanned = false
        setContent(onScanBarcodeClick = { scanned = true })

        composeTestRule.onNodeWithContentDescription("Scan barcode").performClick()

        assertTrue(scanned)
    }

    @Test
    fun editModeShowsEditTitleAndSkipsStraightToManualFields() {
        setContent(isEditing = true)

        composeTestRule.onNodeWithText("Edit item").assertIsDisplayed()
        composeTestRule.onNodeWithText("Name").assertIsDisplayed()
    }

    @Test
    fun typingInNameFieldInvokesOnNameChanged() {
        var typed: String? = null
        setContent(isEditing = true, onNameChanged = { typed = it })

        composeTestRule.onNodeWithText("Name").performTextInput("Chicken breast")

        assertEquals("Chicken breast", typed)
    }

    @Test
    fun searchUsdaButtonInvokesCallbackWithCurrentQueryText() {
        var searched: String? = null
        setContent(
            uiState = ItemFormUiState(usdaQuery = "banana raw"),
            isEditing = true,
            onSearchUsdaClick = { searched = it },
        )

        composeTestRule.onNodeWithText("Search").performClick()

        assertEquals("banana raw", searched)
    }

    @Test
    fun loadingIndicatorShownWhileLookingUp() {
        setContent(uiState = ItemFormUiState(isLookingUp = true), isEditing = true)

        composeTestRule.onNodeWithContentDescription("Looking up nutrition data").assertIsDisplayed()
    }

    @Test
    fun lookupErrorMessageDisplayedWhenPresent() {
        setContent(
            uiState = ItemFormUiState(lookupError = "No match found. You can enter macros manually."),
            isEditing = true,
        )

        composeTestRule.onNodeWithText("No match found. You can enter macros manually.").assertIsDisplayed()
    }

    @Test
    fun addConversionRowButtonInvokesCallback() {
        var added = false
        setContent(isEditing = true, onAddConversionRow = { added = true })

        composeTestRule.onNodeWithText("+ Add conversion").performClick()

        assertTrue(added)
    }

    @Test
    fun saveButtonOnNewItemLabelledAddToPantryAndInvokesCallbackWithNoStartingBatchByDefault() {
        var savedQuantity: Double? = -1.0
        var savedExpiry: LocalDate? = LocalDate.MIN
        setContent(
            isEditing = false,
            onSaveClick = { quantity, expiry -> savedQuantity = quantity; savedExpiry = expiry },
        )

        composeTestRule.onNodeWithContentDescription("Manual entry").performClick()
        composeTestRule.onNodeWithText("Add to pantry").performClick()

        assertNull(savedQuantity)
        assertNull(savedExpiry)
    }

    @Test
    fun saveButtonOnExistingItemLabelledSaveChanges() {
        setContent(isEditing = true)

        composeTestRule.onNodeWithText("Save changes").assertIsDisplayed()
    }

    @Test
    fun cancelButtonInvokesCallback() {
        var cancelled = false
        setContent(isEditing = true, onCancelClick = { cancelled = true })

        composeTestRule.onNodeWithText("Cancel").performClick()

        assertTrue(cancelled)
    }

    @Test
    fun backButtonInHeaderAlsoInvokesCancelCallback() {
        var cancelled = false
        setContent(onCancelClick = { cancelled = true })

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(cancelled)
    }
}
