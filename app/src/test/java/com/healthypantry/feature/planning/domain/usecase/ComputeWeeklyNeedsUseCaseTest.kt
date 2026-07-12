package com.healthypantry.feature.planning.domain.usecase

import com.healthypantry.core.common.Result
import com.healthypantry.core.unit.ConversionFactor
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.core.unit.UnitConverter
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.planning.domain.model.MealSlot
import com.healthypantry.feature.planning.domain.model.PlanEntry
import com.healthypantry.feature.planning.domain.model.PlanEntryType
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.domain.model.RecipeIngredient
import com.healthypantry.feature.recipes.domain.model.RecipeIngredientDetail
import com.healthypantry.feature.recipes.domain.model.RecipeWithIngredients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Spec: "Weekly Plan Assignment and Quick-Add", "Projected vs Actual Stock" (sdd/pantry-tracker/spec).
 *
 * Produces the `FoodItemId -> committed quantity` map that
 * `ComputeProjectedStockUseCase.observeProjected` accepts as `committedQuantities`.
 */
class ComputeWeeklyNeedsUseCaseTest {

    private val useCase = ComputeWeeklyNeedsUseCase(UnitConverter())

    private val riceId = 1L
    private val chickenId = 2L
    private val bananaId = 3L
    private val bowlRecipeId = 10L

    private fun rice() = FoodItem(
        id = riceId,
        name = "Rice",
        canonicalUnit = MeasurementUnit.GRAM,
        source = FoodItemSource.MANUAL,
        caloriesPerUnit = 1.3,
        proteinGramsPerUnit = 0.027,
        carbsGramsPerUnit = 0.28,
        fatGramsPerUnit = 0.003,
    )

    private fun chicken() = FoodItem(
        id = chickenId,
        name = "Chicken breast",
        canonicalUnit = MeasurementUnit.GRAM,
        source = FoodItemSource.MANUAL,
        caloriesPerUnit = 1.65,
        proteinGramsPerUnit = 0.31,
        carbsGramsPerUnit = 0.0,
        fatGramsPerUnit = 0.036,
    )

    private fun bowl(servings: Int = 2) = RecipeWithIngredients(
        recipe = Recipe(id = bowlRecipeId, name = "Bowl", servings = servings, createdAt = Instant.parse("2026-07-12T00:00:00Z")),
        ingredients = listOf(
            RecipeIngredientDetail(
                ingredient = RecipeIngredient(recipeId = bowlRecipeId, foodItemId = riceId, quantity = 200.0, unit = MeasurementUnit.GRAM, sortOrder = 0),
                foodItem = rice(),
            ),
            RecipeIngredientDetail(
                ingredient = RecipeIngredient(recipeId = bowlRecipeId, foodItemId = chickenId, quantity = 150.0, unit = MeasurementUnit.GRAM, sortOrder = 1),
                foodItem = chicken(),
            ),
        ),
    )

    @Test
    fun `aggregates quick-add ITEM entries by food item across the week`() {
        val entries = listOf(
            PlanEntry(dateEpochDay = 1, mealSlot = MealSlot.BREAKFAST, type = PlanEntryType.ITEM, foodItemId = bananaId, quantity = 1.0),
            PlanEntry(dateEpochDay = 2, mealSlot = MealSlot.SNACK, type = PlanEntryType.ITEM, foodItemId = bananaId, quantity = 2.0),
        )

        val result = useCase.compute(entries, recipesById = emptyMap(), conversionFactorsByFoodItemId = emptyMap())

        assertTrue(result is Result.Success)
        assertEquals(3.0, (result as Result.Success).value[bananaId] ?: 0.0, 0.0001)
    }

    @Test
    fun `RECIPE entries scale ingredients by servings ratio and merge into the same food item's need`() {
        // Given "Bowl" (default 2 servings, 200g Rice + 150g Chicken) is assigned twice this
        // week, once at 1 serving and once at the full 2 servings
        val entries = listOf(
            PlanEntry(dateEpochDay = 1, mealSlot = MealSlot.DINNER, type = PlanEntryType.RECIPE, recipeId = bowlRecipeId, servings = 1.0),
            PlanEntry(dateEpochDay = 3, mealSlot = MealSlot.DINNER, type = PlanEntryType.RECIPE, recipeId = bowlRecipeId, servings = 2.0),
        )

        val result = useCase.compute(entries, recipesById = mapOf(bowlRecipeId to bowl()), conversionFactorsByFoodItemId = emptyMap())

        // Then Rice need = 100g (1 serving) + 200g (2 servings) = 300g
        assertTrue(result is Result.Success)
        assertEquals(300.0, (result as Result.Success).value[riceId] ?: 0.0, 0.0001)
        assertEquals(225.0, (result as Result.Success).value[chickenId] ?: 0.0, 0.0001)
    }

    @Test
    fun `eaten entries are excluded from weekly needs`() {
        // Given a 300g Rice quick-add for today was already marked eaten
        val entries = listOf(
            PlanEntry(dateEpochDay = 1, mealSlot = MealSlot.LUNCH, type = PlanEntryType.ITEM, foodItemId = riceId, quantity = 300.0, eaten = true),
        )

        val result = useCase.compute(entries, recipesById = emptyMap(), conversionFactorsByFoodItemId = emptyMap())

        // Then it no longer commits any quantity against Rice (spec "Mark-eaten decrements actual")
        assertTrue(result is Result.Success)
        assertEquals(null, (result as Result.Success).value[riceId])
    }

    @Test
    fun `a RECIPE entry whose recipe is missing from the resolved map contributes nothing`() {
        val entries = listOf(
            PlanEntry(dateEpochDay = 1, mealSlot = MealSlot.DINNER, type = PlanEntryType.RECIPE, recipeId = bowlRecipeId, servings = 1.0),
        )

        val result = useCase.compute(entries, recipesById = emptyMap(), conversionFactorsByFoodItemId = emptyMap())

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).value.isEmpty())
    }

    @Test
    fun `surfaces an unresolved-conversion error from a RECIPE entry's ingredient`() {
        val riceWithCupIngredient = bowl(servings = 1).let {
            it.copy(ingredients = listOf(it.ingredients.first().copy(ingredient = it.ingredients.first().ingredient.copy(unit = MeasurementUnit.CUP))))
        }
        val entries = listOf(
            PlanEntry(dateEpochDay = 1, mealSlot = MealSlot.DINNER, type = PlanEntryType.RECIPE, recipeId = bowlRecipeId, servings = 1.0),
        )

        val result = useCase.compute(
            entries,
            recipesById = mapOf(bowlRecipeId to riceWithCupIngredient),
            conversionFactorsByFoodItemId = emptyMap(),
        )

        assertTrue(result is Result.Failure)
    }

    @Test
    fun `ConversionFactor-registered ingredient unit converts before aggregating`() {
        val cupBowl = bowl(servings = 1).let {
            it.copy(ingredients = listOf(it.ingredients.first().copy(ingredient = it.ingredients.first().ingredient.copy(quantity = 2.0, unit = MeasurementUnit.CUP))))
        }
        val entries = listOf(
            PlanEntry(dateEpochDay = 1, mealSlot = MealSlot.DINNER, type = PlanEntryType.RECIPE, recipeId = bowlRecipeId, servings = 1.0),
        )
        val factors = mapOf(riceId to listOf(ConversionFactor(fromUnit = MeasurementUnit.CUP, toUnit = MeasurementUnit.GRAM, factor = 185.0)))

        val result = useCase.compute(entries, recipesById = mapOf(bowlRecipeId to cupBowl), conversionFactorsByFoodItemId = factors)

        assertTrue(result is Result.Success)
        assertEquals(370.0, (result as Result.Success).value[riceId] ?: 0.0, 0.0001)
    }
}
