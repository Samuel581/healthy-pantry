package com.healthypantry.feature.planning.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.healthypantry.app.theme.HealthyPantryTheme
import com.healthypantry.feature.planning.domain.model.MealSlot
import com.healthypantry.feature.planning.domain.model.PlanEntry
import com.healthypantry.feature.planning.domain.model.PlanEntryType
import com.healthypantry.feature.planning.ui.vm.PlanEntryUi
import com.healthypantry.feature.planning.ui.vm.PlanUiState
import com.healthypantry.feature.planning.ui.vm.currentWeekRange
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compiles and packages as an instrumented test (`./gradlew connectedDebugAndroidTest`) but is
 * NOT executed in this sandbox — no emulator/device available (same precedent as
 * `PantryListScreenTest`/`RecipeScreenTest`). Drives [WeekPlanContent], the stateless/
 * presentational half of [WeekPlanScreen], directly against hand-built [PlanUiState] fixtures.
 */
class WeekPlanScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val weekRange = currentWeekRange()

    private fun quickAddEntry(id: Long = 1L, displayName: String = "Rice", eaten: Boolean = false) = PlanEntryUi(
        entry = PlanEntry(
            id = id,
            dateEpochDay = weekRange.startEpochDay,
            mealSlot = MealSlot.BREAKFAST,
            type = PlanEntryType.ITEM,
            foodItemId = 1L,
            quantity = 2.0,
            eaten = eaten,
        ),
        displayName = displayName,
    )

    @Test
    fun listRendersEachPlanEntryDisplayNameAndMealSlot() {
        val state = PlanUiState(weekRange = weekRange, entries = listOf(quickAddEntry(displayName = "Rice")), isLoading = false)

        composeTestRule.setContent {
            HealthyPantryTheme {
                WeekPlanContent(uiState = state, onAssignRecipe = { _, _, _, _ -> }, onQuickAdd = { _, _, _, _ -> }, onMarkEaten = {}, onDeleteEntry = {})
            }
        }

        composeTestRule.onNodeWithText("Rice").assertIsDisplayed()
        composeTestRule.onNodeWithText("${LocalDate.ofEpochDay(weekRange.startEpochDay).dayOfWeek} · BREAKFAST").assertIsDisplayed()
    }

    @Test
    fun emptyStateMessageShownWhenNoEntriesAndNotLoading() {
        composeTestRule.setContent {
            HealthyPantryTheme {
                WeekPlanContent(
                    uiState = PlanUiState(weekRange = weekRange, entries = emptyList(), isLoading = false),
                    onAssignRecipe = { _, _, _, _ -> },
                    onQuickAdd = { _, _, _, _ -> },
                    onMarkEaten = {},
                    onDeleteEntry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("No plan entries yet this week.").assertIsDisplayed()
    }

    @Test
    fun markEatenButtonInvokesCallbackWithTheTappedEntry() {
        var marked: PlanEntry? = null
        val target = quickAddEntry(id = 42L, displayName = "Rice")
        val state = PlanUiState(weekRange = weekRange, entries = listOf(target), isLoading = false)

        composeTestRule.setContent {
            HealthyPantryTheme {
                WeekPlanContent(
                    uiState = state,
                    onAssignRecipe = { _, _, _, _ -> },
                    onQuickAdd = { _, _, _, _ -> },
                    onMarkEaten = { marked = it },
                    onDeleteEntry = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Mark Rice eaten").performClick()

        assertEquals(target.entry, marked)
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
                    onAssignRecipe = { _, _, _, _ -> },
                    onQuickAdd = { _, _, _, _ -> },
                    onMarkEaten = {},
                    onDeleteEntry = { deleted = it },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Delete Rice entry").performClick()

        assertEquals(target.entry, deleted)
    }

    @Test
    fun alreadyEatenEntryShowsEatenLabelInsteadOfMarkEatenButton() {
        val state = PlanUiState(weekRange = weekRange, entries = listOf(quickAddEntry(displayName = "Rice", eaten = true)), isLoading = false)

        composeTestRule.setContent {
            HealthyPantryTheme {
                WeekPlanContent(uiState = state, onAssignRecipe = { _, _, _, _ -> }, onQuickAdd = { _, _, _, _ -> }, onMarkEaten = {}, onDeleteEntry = {})
            }
        }

        composeTestRule.onNodeWithText("Eaten").assertIsDisplayed()
    }

    @Test
    fun loadingIndicatorShownWhileLoading() {
        composeTestRule.setContent {
            HealthyPantryTheme {
                WeekPlanContent(
                    uiState = PlanUiState(weekRange = weekRange, isLoading = true),
                    onAssignRecipe = { _, _, _, _ -> },
                    onQuickAdd = { _, _, _, _ -> },
                    onMarkEaten = {},
                    onDeleteEntry = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Loading week plan").assertIsDisplayed()
    }

    @Test
    fun addAssignmentFormShowsRecipeAndQuickAddToggle() {
        composeTestRule.setContent {
            HealthyPantryTheme {
                WeekPlanContent(
                    uiState = PlanUiState(weekRange = weekRange, isLoading = false),
                    onAssignRecipe = { _, _, _, _ -> },
                    onQuickAdd = { _, _, _, _ -> },
                    onMarkEaten = {},
                    onDeleteEntry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Assign to this week").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add").assertIsDisplayed()
    }
}
