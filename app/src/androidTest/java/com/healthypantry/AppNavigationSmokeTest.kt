package com.healthypantry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.healthypantry.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end golden-path smoke test across the Phase 11 nav graph (`HealthyPantryNavHost` +
 * `HealthyPantryBottomBar`): launch -> land on the Pantry tab -> open the add-item form -> save
 * -> see the new item back on the list -> switch tabs and back (bottom-nav save/restore-state).
 *
 * Runs against the real Hilt DI graph with only [com.healthypantry.di.TestDatabaseModule]'s
 * in-memory `AppDatabase` swapped in (`@TestInstallIn`) — every repository/DAO/use-case above it
 * is the real production implementation, not a hand-written fake, so this is the one place in the
 * suite that exercises the actual wiring the app uses at runtime, complementing the per-feature
 * Compose tests that drive stateless `*Content` composables directly (`PantryListScreenTest`,
 * `RecipeScreenTest`, `WeekPlanScreenTest`).
 *
 * Compiles/packages as an instrumented test (`./gradlew connectedDebugAndroidTest`) but is NOT
 * executed in this sandbox — no emulator/device is available here, same precedent as every other
 * `androidTest` in this project (see `HarnessInstrumentedSmokeTest`, `PantryListScreenTest`).
 */
@HiltAndroidTest
class AppNavigationSmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun addItemThenSwitchTabsAndBackShowsItInPantryList() {
        // Launches directly on the Pantry tab (empty state - in-memory DB starts with no items).
        composeTestRule.onNodeWithText("No pantry items yet. Tap + to add one.").assertIsDisplayed()

        // Navigate to the add-item form via the list's FAB.
        composeTestRule.onNodeWithText("+").performClick()
        composeTestRule.onNodeWithText("Add item").assertIsDisplayed()

        // Fill just the name; macro fields default to 0.0 when blank (see
        // ItemFormUiState.toFoodItem), so this alone is enough to save a valid FoodItem.
        composeTestRule.onNodeWithText("Name").performTextInput("Smoke Test Item")
        composeTestRule.onNodeWithText("Save").performClick()

        // Saving navigates back to the Pantry list, where the new item is now visible.
        composeTestRule.onNodeWithText("Smoke Test Item").assertIsDisplayed()

        // Switching to another tab and back preserves the Pantry tab's state (bottom-nav
        // save/restore-state convention in HealthyPantryBottomBar) - the item is still there.
        composeTestRule.onNodeWithText("Recipes").performClick()
        composeTestRule.onNodeWithText("Pantry").performClick()
        composeTestRule.onNodeWithText("Smoke Test Item").assertIsDisplayed()
    }
}
