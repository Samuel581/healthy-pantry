package com.healthypantry.feature.planning.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.healthypantry.app.theme.HealthyPantryTheme
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.planning.domain.model.MacroTotals
import com.healthypantry.feature.planning.domain.model.MealSlot
import com.healthypantry.feature.planning.ui.vm.currentWeekRange
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.ui.vm.RecipeMacroSummary
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compiles and packages as an instrumented test (`./gradlew connectedDebugAndroidTest`) but is
 * NOT executed in this sandbox — no emulator/device available (same precedent as
 * `WeekPlanScreenTest`/`RecipeScreenTest`). Drives [PlanAssignContent], the stateless/
 * presentational half of [PlanAssignScreen], directly against hand-built fixtures.
 */
class PlanAssignScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val day = currentWeekRange().startEpochDay

    private fun recipe(id: Long = 1L, name: String = "Chicken stir-fry", servings: Int = 2) = Recipe(
        id = id,
        name = name,
        servings = servings,
        createdAt = Instant.EPOCH,
    )

    private fun foodItem(id: Long = 1L, name: String = "Rice") = FoodItem(
        id = id,
        name = name,
        canonicalUnit = MeasurementUnit.GRAM,
        source = FoodItemSource.MANUAL,
        caloriesPerUnit = 1.3,
        proteinGramsPerUnit = 0.03,
        carbsGramsPerUnit = 0.28,
        fatGramsPerUnit = 0.003,
    )

    @Test
    fun headerShowsDayAndMealSlot() {
        composeTestRule.setContent {
            HealthyPantryTheme {
                PlanAssignContent(
                    day = day,
                    mealSlot = MealSlot.LUNCH,
                    recipes = emptyList(),
                    foodItems = emptyList(),
                    macroSummariesByRecipeId = emptyMap(),
                    isSaving = false,
                    onBack = {},
                    onAssignRecipe = { _, _ -> },
                    onQuickAdd = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithText("· Lunch", substring = true).assertIsDisplayed()
    }

    @Test
    fun assignButtonDisabledUntilARecipeIsSelected() {
        composeTestRule.setContent {
            HealthyPantryTheme {
                PlanAssignContent(
                    day = day,
                    mealSlot = MealSlot.BREAKFAST,
                    recipes = listOf(recipe()),
                    foodItems = emptyList(),
                    macroSummariesByRecipeId = emptyMap(),
                    isSaving = false,
                    onBack = {},
                    onAssignRecipe = { _, _ -> },
                    onQuickAdd = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Assign to breakfast").assertIsNotEnabled()
    }

    @Test
    fun selectingARecipeThenAssignInvokesOnAssignRecipeWithItsDefaultServings() {
        var assigned: Pair<Long, Double>? = null
        val target = recipe(id = 9L, name = "Chicken stir-fry", servings = 3)

        composeTestRule.setContent {
            HealthyPantryTheme {
                PlanAssignContent(
                    day = day,
                    mealSlot = MealSlot.DINNER,
                    recipes = listOf(target),
                    foodItems = emptyList(),
                    macroSummariesByRecipeId = mapOf(
                        9L to RecipeMacroSummary(
                            ingredientCount = 2,
                            macroTotals = MacroTotals(calories = 600.0, proteinGrams = 40.0, carbsGrams = 10.0, fatGrams = 20.0),
                        ),
                    ),
                    isSaving = false,
                    onBack = {},
                    onAssignRecipe = { recipeId, servings -> assigned = recipeId to servings },
                    onQuickAdd = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Select recipe Chicken stir-fry").performClick()
        composeTestRule.onNodeWithContentDescription("Assign to dinner").performClick()

        assertEquals(9L to 3.0, assigned)
    }

    @Test
    fun quickAddModePickingItemAndQuantityThenAssignInvokesOnQuickAdd() {
        var added: Pair<Long, Double>? = null

        composeTestRule.setContent {
            HealthyPantryTheme {
                PlanAssignContent(
                    day = day,
                    mealSlot = MealSlot.SNACK,
                    recipes = emptyList(),
                    foodItems = listOf(foodItem(id = 5L, name = "Rice")),
                    macroSummariesByRecipeId = emptyMap(),
                    isSaving = false,
                    onBack = {},
                    onAssignRecipe = { _, _ -> },
                    onQuickAdd = { foodItemId, quantity -> added = foodItemId to quantity },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Quick add mode").performClick()
        composeTestRule.onNodeWithContentDescription("Pantry item picker").performClick()
        composeTestRule.onNodeWithText("Rice").performClick()
        composeTestRule.onNodeWithText("Quantity").performTextReplacement("2.5")
        composeTestRule.onNodeWithContentDescription("Assign to snack").performClick()

        assertEquals(5L to 2.5, added)
    }

    @Test
    fun backButtonInvokesOnBack() {
        var backPressed = false
        composeTestRule.setContent {
            HealthyPantryTheme {
                PlanAssignContent(
                    day = day,
                    mealSlot = MealSlot.BREAKFAST,
                    recipes = emptyList(),
                    foodItems = emptyList(),
                    macroSummariesByRecipeId = emptyMap(),
                    isSaving = false,
                    onBack = { backPressed = true },
                    onAssignRecipe = { _, _ -> },
                    onQuickAdd = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(backPressed)
    }

    @Test
    fun savingDisablesTheAssignButtonEvenWithASelection() {
        val target = recipe(id = 9L)
        composeTestRule.setContent {
            HealthyPantryTheme {
                PlanAssignContent(
                    day = day,
                    mealSlot = MealSlot.LUNCH,
                    recipes = listOf(target),
                    foodItems = emptyList(),
                    macroSummariesByRecipeId = emptyMap(),
                    isSaving = true,
                    onBack = {},
                    onAssignRecipe = { _, _ -> },
                    onQuickAdd = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Select recipe Chicken stir-fry").performClick()

        composeTestRule.onNodeWithContentDescription("Assign to lunch").assertIsNotEnabled()
    }
}
