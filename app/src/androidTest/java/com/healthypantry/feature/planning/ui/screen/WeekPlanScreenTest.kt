package com.healthypantry.feature.planning.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.healthypantry.app.theme.HealthyPantryTheme
import com.healthypantry.feature.planning.domain.model.MealSlot
import com.healthypantry.feature.planning.domain.model.PlanEntry
import com.healthypantry.feature.planning.domain.model.PlanEntryType
import com.healthypantry.feature.planning.ui.vm.PlanEntryUi
import com.healthypantry.feature.planning.ui.vm.PlanUiState
import com.healthypantry.feature.planning.ui.vm.currentWeekRange
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Compiles and packages as an instrumented test (`./gradlew connectedDebugAndroidTest`) but is
 * NOT executed in this sandbox — no emulator/device available (same precedent as
 * `PantryListScreenTest`/`RecipeScreenTest`). Drives [WeekPlanContent], the stateless/
 * presentational half of [WeekPlanScreen], directly against hand-built [PlanUiState] fixtures.
 *
 * [WeekPlanContent] defaults its own day selection to `today` (real current date); every test here
 * pins [WeekPlanContent]'s `today` parameter to [mondayDate] (the fixture week's Monday) so the
 * selected day — and therefore which entries are visible — doesn't depend on which day the test
 * actually runs on (same rationale `PantryListContent`'s own `today` parameter documents).
 */
class WeekPlanScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val weekRange = currentWeekRange()
    private val mondayDate: LocalDate = LocalDate.ofEpochDay(weekRange.startEpochDay)
    private val tuesdayDate: LocalDate = mondayDate.plusDays(1)

    private fun quickAddEntry(
        id: Long = 1L,
        day: Long = weekRange.startEpochDay,
        mealSlot: MealSlot = MealSlot.BREAKFAST,
        displayName: String = "Rice",
        eaten: Boolean = false,
    ) = PlanEntryUi(
        entry = PlanEntry(
            id = id,
            dateEpochDay = day,
            mealSlot = mealSlot,
            type = PlanEntryType.ITEM,
            foodItemId = 1L,
            quantity = 2.0,
            eaten = eaten,
        ),
        displayName = displayName,
    )

    @Test
    fun loadingIndicatorShownWhileLoading() {
        composeTestRule.setContent {
            HealthyPantryTheme {
                WeekPlanContent(
                    uiState = PlanUiState(weekRange = weekRange, isLoading = true),
                    onMarkEaten = {},
                    onDeleteEntry = {},
                    onAddToSlot = { _, _ -> },
                    today = mondayDate,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Loading week plan").assertIsDisplayed()
    }

    @Test
    fun emptyDayShowsAddButtonForEveryMealSlot() {
        val state = PlanUiState(weekRange = weekRange, entries = emptyList(), isLoading = false)

        composeTestRule.setContent {
            HealthyPantryTheme {
                WeekPlanContent(
                    uiState = state,
                    onMarkEaten = {},
                    onDeleteEntry = {},
                    onAddToSlot = { _, _ -> },
                    today = mondayDate,
                )
            }
        }

        composeTestRule.onNodeWithText("+ Add Breakfast").assertIsDisplayed()
        composeTestRule.onNodeWithText("+ Add Snack").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun filledMealSlotShowsAssignedEntryDisplayName() {
        val state = PlanUiState(weekRange = weekRange, entries = listOf(quickAddEntry(displayName = "Rice")), isLoading = false)

        composeTestRule.setContent {
            HealthyPantryTheme {
                WeekPlanContent(
                    uiState = state,
                    onMarkEaten = {},
                    onDeleteEntry = {},
                    onAddToSlot = { _, _ -> },
                    today = mondayDate,
                )
            }
        }

        composeTestRule.onNodeWithText("Rice").assertIsDisplayed()
    }

    @Test
    fun addButtonInvokesOnAddToSlotWithSelectedDayAndMealSlot() {
        var added: Pair<Long, MealSlot>? = null
        val state = PlanUiState(weekRange = weekRange, entries = emptyList(), isLoading = false)

        composeTestRule.setContent {
            HealthyPantryTheme {
                WeekPlanContent(
                    uiState = state,
                    onMarkEaten = {},
                    onDeleteEntry = {},
                    onAddToSlot = { day, mealSlot -> added = day to mealSlot },
                    today = mondayDate,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Add Breakfast entry").performClick()

        assertEquals(weekRange.startEpochDay to MealSlot.BREAKFAST, added)
    }

    @Test
    fun markEatenToggleInvokesCallbackWithTheTappedEntryWhenNotYetEaten() {
        var marked: PlanEntry? = null
        val target = quickAddEntry(id = 42L, displayName = "Rice", eaten = false)
        val state = PlanUiState(weekRange = weekRange, entries = listOf(target), isLoading = false)

        composeTestRule.setContent {
            HealthyPantryTheme {
                WeekPlanContent(
                    uiState = state,
                    onMarkEaten = { marked = it },
                    onDeleteEntry = {},
                    onAddToSlot = { _, _ -> },
                    today = mondayDate,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Mark Rice eaten").performClick()

        assertEquals(target.entry, marked)
    }

    @Test
    fun alreadyEatenToggleIsDisabledAndNeverInvokesMarkEaten() {
        var marked: PlanEntry? = null
        val target = quickAddEntry(displayName = "Rice", eaten = true)
        val state = PlanUiState(weekRange = weekRange, entries = listOf(target), isLoading = false)

        composeTestRule.setContent {
            HealthyPantryTheme {
                WeekPlanContent(
                    uiState = state,
                    onMarkEaten = { marked = it },
                    onDeleteEntry = {},
                    onAddToSlot = { _, _ -> },
                    today = mondayDate,
                )
            }
        }

        // Deliberately one-way (see `EatenToggle` KDoc): no `unmarkEaten` exists anywhere in this
        // app, so an already-eaten entry's toggle must render disabled, not just visually "done".
        composeTestRule.onNodeWithContentDescription("Rice eaten").assertIsDisplayed().assertIsNotEnabled()
        assertNull(marked)
    }

    @Test
    fun deleteButtonInvokesCallbackWithTheTappedEntry() {
        var deleted: PlanEntry? = null
        val target = quickAddEntry(id = 7L, displayName = "Rice")
        val state = PlanUiState(weekRange = weekRange, entries = listOf(target), isLoading = false)

        composeTestRule.setContent {
            HealthyPantryTheme {
                WeekPlanContent(
                    uiState = state,
                    onMarkEaten = {},
                    onDeleteEntry = { deleted = it },
                    onAddToSlot = { _, _ -> },
                    today = mondayDate,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Remove Rice entry").performClick()

        assertEquals(target.entry, deleted)
    }

    @Test
    fun selectingADifferentDayChipSwitchesTheVisibleEntry() {
        val mondayEntry = quickAddEntry(id = 1L, day = weekRange.startEpochDay, displayName = "Rice")
        val tuesdayEntry = quickAddEntry(id = 2L, day = weekRange.startEpochDay + 1, displayName = "Eggs")
        val state = PlanUiState(weekRange = weekRange, entries = listOf(mondayEntry, tuesdayEntry), isLoading = false)

        composeTestRule.setContent {
            HealthyPantryTheme {
                WeekPlanContent(
                    uiState = state,
                    onMarkEaten = {},
                    onDeleteEntry = {},
                    onAddToSlot = { _, _ -> },
                    today = mondayDate,
                )
            }
        }

        composeTestRule.onNodeWithText("Rice").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Select ${tuesdayDate.dayOfWeek.name}").performClick()

        composeTestRule.onNodeWithText("Eggs").assertIsDisplayed()
    }
}
