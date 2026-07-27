package com.healthypantry.feature.recipes.ui.screen

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.healthypantry.app.theme.HealthyPantryTheme
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.planning.domain.model.MacroTotals
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.ui.vm.RecipeFormState
import com.healthypantry.feature.recipes.ui.vm.RecipeMacroSummary
import com.healthypantry.feature.recipes.ui.vm.RecipeUiState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compiles and packages as an instrumented test (`./gradlew connectedDebugAndroidTest`) but is
 * NOT executed in this sandbox — no emulator/device available (same precedent as
 * `PantryListScreenTest`/`ItemFormScreenTest`). Drives [RecipeListContent]/[RecipeFormContent],
 * the stateless/presentational halves of [RecipeScreen], directly against hand-built fixtures.
 */
class RecipeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun recipe(id: Long = 1L, name: String = "Chicken stir-fry", servings: Int = 2) = Recipe(
        id = id,
        name = name,
        servings = servings,
        createdAt = Instant.EPOCH,
    )

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
    fun listRendersEachRecipeNameIngredientCountAndMacroTags() {
        val target = recipe(id = 1L, name = "Chicken stir-fry")
        val state = RecipeUiState(
            recipes = listOf(target),
            macroSummariesByRecipeId = mapOf(
                1L to RecipeMacroSummary(
                    ingredientCount = 4,
                    macroTotals = MacroTotals(calories = 330.0, proteinGrams = 62.0, carbsGrams = 0.0, fatGrams = 7.2),
                ),
            ),
            isLoading = false,
        )

        composeTestRule.setContent {
            HealthyPantryTheme {
                RecipeListContent(uiState = state, onAddClick = {}, onEditClick = {})
            }
        }

        composeTestRule.onNodeWithText("Chicken stir-fry").assertIsDisplayed()
        composeTestRule.onNodeWithText("4 ingredients").assertIsDisplayed()
        composeTestRule.onNodeWithText("330.0 kcal").assertIsDisplayed()
    }

    @Test
    fun emptyStateMessageShownWhenNoRecipesAndNotLoading() {
        composeTestRule.setContent {
            HealthyPantryTheme {
                RecipeListContent(
                    uiState = RecipeUiState(recipes = emptyList(), isLoading = false),
                    onAddClick = {},
                    onEditClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("No recipes yet. Tap + to add one.").assertIsDisplayed()
    }

    @Test
    fun tappingCardInvokesOnEditClickWithTheTappedRecipe() {
        var edited: Recipe? = null
        val target = recipe(id = 42L, name = "Chicken stir-fry")
        val state = RecipeUiState(recipes = listOf(target), isLoading = false)

        composeTestRule.setContent {
            HealthyPantryTheme {
                RecipeListContent(uiState = state, onAddClick = {}, onEditClick = { edited = it })
            }
        }

        composeTestRule.onNodeWithContentDescription("Open Chicken stir-fry").performClick()

        assertEquals(target, edited)
    }

    @Test
    fun addModeShowsNewRecipeTitleAndCoreFormFieldsWithNoDeleteButton() {
        composeTestRule.setContent {
            HealthyPantryTheme {
                RecipeFormContent(
                    formState = RecipeFormState(),
                    availableFoodItems = listOf(foodItem()),
                    onNameChanged = {},
                    onServingsChanged = {},
                    onNotesChanged = {},
                    onAddIngredientRow = {},
                    onIngredientRowChanged = { _, _ -> },
                    onRemoveIngredientRow = {},
                    onSaveClick = {},
                    onCancelClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("New recipe").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recipe name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Servings").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delete recipe").assertDoesNotExist()
    }

    @Test
    fun editModeShowsEditRecipeTitleAndDeleteButton() {
        composeTestRule.setContent {
            HealthyPantryTheme {
                RecipeFormContent(
                    formState = RecipeFormState(id = 7L, name = "Chicken stir-fry"),
                    availableFoodItems = emptyList(),
                    onNameChanged = {},
                    onServingsChanged = {},
                    onNotesChanged = {},
                    onAddIngredientRow = {},
                    onIngredientRowChanged = { _, _ -> },
                    onRemoveIngredientRow = {},
                    onSaveClick = {},
                    onCancelClick = {},
                    onDeleteClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Edit recipe").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delete recipe").assertIsDisplayed()
    }

    @Test
    fun tappingDeleteButtonInvokesCallback() {
        var deleted = false
        composeTestRule.setContent {
            HealthyPantryTheme {
                RecipeFormContent(
                    formState = RecipeFormState(id = 7L, name = "Chicken stir-fry"),
                    availableFoodItems = emptyList(),
                    onNameChanged = {},
                    onServingsChanged = {},
                    onNotesChanged = {},
                    onAddIngredientRow = {},
                    onIngredientRowChanged = { _, _ -> },
                    onRemoveIngredientRow = {},
                    onSaveClick = {},
                    onCancelClick = {},
                    onDeleteClick = { deleted = true },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Delete recipe").performClick()

        assertTrue(deleted)
    }

    @Test
    fun typingInNameFieldInvokesOnNameChanged() {
        var typed: String? = null
        composeTestRule.setContent {
            HealthyPantryTheme {
                RecipeFormContent(
                    formState = RecipeFormState(),
                    availableFoodItems = emptyList(),
                    onNameChanged = { typed = it },
                    onServingsChanged = {},
                    onNotesChanged = {},
                    onAddIngredientRow = {},
                    onIngredientRowChanged = { _, _ -> },
                    onRemoveIngredientRow = {},
                    onSaveClick = {},
                    onCancelClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Recipe name").performTextInput("Chicken stir-fry")

        assertEquals("Chicken stir-fry", typed)
    }

    @Test
    fun addIngredientButtonInvokesCallback() {
        var added = false
        composeTestRule.setContent {
            HealthyPantryTheme {
                RecipeFormContent(
                    formState = RecipeFormState(),
                    availableFoodItems = emptyList(),
                    onNameChanged = {},
                    onServingsChanged = {},
                    onNotesChanged = {},
                    onAddIngredientRow = { added = true },
                    onIngredientRowChanged = { _, _ -> },
                    onRemoveIngredientRow = {},
                    onSaveClick = {},
                    onCancelClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("+ Add ingredient").performClick()

        assertTrue(added)
    }

    @Test
    fun saveButtonInvokesCallback() {
        var saved = false
        composeTestRule.setContent {
            HealthyPantryTheme {
                RecipeFormContent(
                    formState = RecipeFormState(),
                    availableFoodItems = emptyList(),
                    onNameChanged = {},
                    onServingsChanged = {},
                    onNotesChanged = {},
                    onAddIngredientRow = {},
                    onIngredientRowChanged = { _, _ -> },
                    onRemoveIngredientRow = {},
                    onSaveClick = { saved = true },
                    onCancelClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Save recipe").performClick()

        assertTrue(saved)
    }
}
