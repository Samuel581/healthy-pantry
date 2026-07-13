package com.healthypantry.feature.recipes.ui.screen

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
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.ui.vm.RecipeFormState
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
    fun listRendersEachRecipeNameAndServings() {
        val state = RecipeUiState(recipes = listOf(recipe(id = 1L, name = "Chicken stir-fry", servings = 2)), isLoading = false)

        composeTestRule.setContent {
            HealthyPantryTheme {
                RecipeListContent(uiState = state, onAddClick = {}, onEditClick = {}, onDeleteClick = {})
            }
        }

        composeTestRule.onNodeWithText("Chicken stir-fry").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 servings").assertIsDisplayed()
    }

    @Test
    fun emptyStateMessageShownWhenNoRecipesAndNotLoading() {
        composeTestRule.setContent {
            HealthyPantryTheme {
                RecipeListContent(
                    uiState = RecipeUiState(recipes = emptyList(), isLoading = false),
                    onAddClick = {},
                    onEditClick = {},
                    onDeleteClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("No recipes yet. Tap + to add one.").assertIsDisplayed()
    }

    @Test
    fun tappingDeleteInvokesCallbackWithTheTappedRecipe() {
        var deleted: Recipe? = null
        val target = recipe(id = 42L, name = "Chicken stir-fry")
        val state = RecipeUiState(recipes = listOf(target), isLoading = false)

        composeTestRule.setContent {
            HealthyPantryTheme {
                RecipeListContent(uiState = state, onAddClick = {}, onEditClick = {}, onDeleteClick = { deleted = it })
            }
        }

        composeTestRule.onNodeWithContentDescription("Delete Chicken stir-fry").performClick()

        assertEquals(target, deleted)
    }

    @Test
    fun addModeShowsAddTitleAndCoreFormFields() {
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

        composeTestRule.onNodeWithText("Add recipe").assertIsDisplayed()
        composeTestRule.onNodeWithText("Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Servings").assertIsDisplayed()
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

        composeTestRule.onNodeWithText("Name").performTextInput("Chicken stir-fry")

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

        composeTestRule.onNodeWithText("Save").performClick()

        assertTrue(saved)
    }
}
