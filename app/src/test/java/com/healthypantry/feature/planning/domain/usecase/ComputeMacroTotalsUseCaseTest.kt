package com.healthypantry.feature.planning.domain.usecase

import com.healthypantry.core.common.Result
import com.healthypantry.core.unit.ConversionFactor
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.core.unit.UnitConversionError
import com.healthypantry.core.unit.UnitConverter
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.planning.domain.model.MacroTotals
import com.healthypantry.feature.planning.domain.model.ResolvedDayEntry
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.domain.model.RecipeIngredient
import com.healthypantry.feature.recipes.domain.model.RecipeIngredientDetail
import com.healthypantry.feature.recipes.domain.model.RecipeWithIngredients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Spec: "Item-to-Day Macro Rollup", "Recipe total from ingredients", "Day total across recipe and
 * quick-add" (sdd/pantry-tracker/spec).
 */
class ComputeMacroTotalsUseCaseTest {

    private val useCase = ComputeMacroTotalsUseCase(UnitConverter())

    private fun rice() = FoodItem(
        id = 1L,
        name = "Rice",
        canonicalUnit = MeasurementUnit.GRAM,
        source = FoodItemSource.MANUAL,
        caloriesPerUnit = 1.3,
        proteinGramsPerUnit = 0.027,
        carbsGramsPerUnit = 0.28,
        fatGramsPerUnit = 0.003,
    )

    private fun chicken() = FoodItem(
        id = 2L,
        name = "Chicken breast",
        canonicalUnit = MeasurementUnit.GRAM,
        source = FoodItemSource.MANUAL,
        caloriesPerUnit = 1.65,
        proteinGramsPerUnit = 0.31,
        carbsGramsPerUnit = 0.0,
        fatGramsPerUnit = 0.036,
    )

    private fun bowl(servings: Int = 2) = Recipe(
        id = 10L,
        name = "Bowl",
        servings = servings,
        createdAt = Instant.parse("2026-07-12T00:00:00Z"),
    )

    @Test
    fun `computeForQuickAdd multiplies per-unit macros by quantity`() {
        // Given "Rice" (per-gram macros) is quick-added at 200g
        val totals = useCase.computeForQuickAdd(rice(), quantity = 200.0)

        // Then macros scale directly by quantity (no conversion needed)
        assertEquals(260.0, totals.calories, 0.0001)
        assertEquals(5.4, totals.proteinGrams, 0.0001)
        assertEquals(56.0, totals.carbsGrams, 0.0001)
        assertEquals(0.6, totals.fatGrams, 0.0001)
    }

    @Test
    fun `computeForRecipe sums each ingredient's macros when units already match canonical`() {
        // Given "Bowl" has 200g Rice + 150g Chicken (spec "Recipe total from ingredients")
        val recipe = RecipeWithIngredients(
            recipe = bowl(servings = 2),
            ingredients = listOf(
                RecipeIngredientDetail(
                    ingredient = RecipeIngredient(recipeId = 10L, foodItemId = 1L, quantity = 200.0, unit = MeasurementUnit.GRAM, sortOrder = 0),
                    foodItem = rice(),
                ),
                RecipeIngredientDetail(
                    ingredient = RecipeIngredient(recipeId = 10L, foodItemId = 2L, quantity = 150.0, unit = MeasurementUnit.GRAM, sortOrder = 1),
                    foodItem = chicken(),
                ),
            ),
        )

        // When computed for the recipe's own default 2 servings (ratio 1.0)
        val result = useCase.computeForRecipe(recipe, requestedServings = 2.0, conversionFactorsByFoodItemId = emptyMap())

        // Then kcal = sum of each ingredient's macros: 260 + 247.5 = 507.5
        assertTrue(result is Result.Success)
        val totals = (result as Result.Success).value
        assertEquals(507.5, totals.calories, 0.0001)
        assertEquals(51.9, totals.proteinGrams, 0.0001)
        assertEquals(56.0, totals.carbsGrams, 0.0001)
        assertEquals(6.0, totals.fatGrams, 0.0001)
    }

    @Test
    fun `computeForRecipe scales ingredient quantities by requestedServings over recipe servings`() {
        // Given "Bowl" (default 2 servings) has 200g Rice + 150g Chicken
        val recipe = RecipeWithIngredients(
            recipe = bowl(servings = 2),
            ingredients = listOf(
                RecipeIngredientDetail(
                    ingredient = RecipeIngredient(recipeId = 10L, foodItemId = 1L, quantity = 200.0, unit = MeasurementUnit.GRAM, sortOrder = 0),
                    foodItem = rice(),
                ),
                RecipeIngredientDetail(
                    ingredient = RecipeIngredient(recipeId = 10L, foodItemId = 2L, quantity = 150.0, unit = MeasurementUnit.GRAM, sortOrder = 1),
                    foodItem = chicken(),
                ),
            ),
        )

        // When only 1 serving is planned for this slot (half the recipe's default batch)
        val result = useCase.computeForRecipe(recipe, requestedServings = 1.0, conversionFactorsByFoodItemId = emptyMap())

        // Then every ingredient's contribution is halved: 130 (100g rice) + 123.75 (75g chicken)
        assertTrue(result is Result.Success)
        val totals = (result as Result.Success).value
        assertEquals(253.75, totals.calories, 0.0001)
    }

    @Test
    fun `computeForRecipe converts an ingredient unit to canonical using its registered factor`() {
        // Given "Rice" registers 1 cup = 185g, and the recipe specifies 2 cups
        val recipe = RecipeWithIngredients(
            recipe = bowl(servings = 1),
            ingredients = listOf(
                RecipeIngredientDetail(
                    ingredient = RecipeIngredient(recipeId = 10L, foodItemId = 1L, quantity = 2.0, unit = MeasurementUnit.CUP, sortOrder = 0),
                    foodItem = rice(),
                ),
            ),
        )
        val factors = mapOf(1L to listOf(ConversionFactor(fromUnit = MeasurementUnit.CUP, toUnit = MeasurementUnit.GRAM, factor = 185.0)))

        // When computed for the recipe's own 1 serving (ratio 1.0)
        val result = useCase.computeForRecipe(recipe, requestedServings = 1.0, conversionFactorsByFoodItemId = factors)

        // Then 2 cups converts to 370g before macros are computed: 370 * 1.3 = 481
        assertTrue(result is Result.Success)
        assertEquals(481.0, (result as Result.Success).value.calories, 0.0001)
    }

    @Test
    fun `computeForRecipe surfaces the unresolved-conversion error instead of treating it as zero`() {
        // Given "Rice" has no registered conversion from tablespoon
        val recipe = RecipeWithIngredients(
            recipe = bowl(servings = 1),
            ingredients = listOf(
                RecipeIngredientDetail(
                    ingredient = RecipeIngredient(recipeId = 10L, foodItemId = 1L, quantity = 3.0, unit = MeasurementUnit.TABLESPOON, sortOrder = 0),
                    foodItem = rice(),
                ),
            ),
        )

        // When computed with no registered factors
        val result = useCase.computeForRecipe(recipe, requestedServings = 1.0, conversionFactorsByFoodItemId = emptyMap())

        // Then the error is surfaced, not silently zeroed
        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertEquals(
            UnitConversionError.UnresolvedConversion(foodItemLabel = "Rice", fromUnit = MeasurementUnit.TABLESPOON, toUnit = MeasurementUnit.GRAM),
            error,
        )
    }

    @Test
    fun `computeForRecipe fails with a typed error instead of dividing by zero when recipe servings is zero`() {
        // Given "Bowl" somehow has servings = 0 (no domain/DB validation prevents this)
        val recipe = RecipeWithIngredients(
            recipe = bowl(servings = 0),
            ingredients = listOf(
                RecipeIngredientDetail(
                    ingredient = RecipeIngredient(recipeId = 10L, foodItemId = 1L, quantity = 200.0, unit = MeasurementUnit.GRAM, sortOrder = 0),
                    foodItem = rice(),
                ),
            ),
        )

        // When computed for any requested servings
        val result = useCase.computeForRecipe(recipe, requestedServings = 1.0, conversionFactorsByFoodItemId = emptyMap())

        // Then it fails with a typed error rather than producing Infinity/NaN
        assertTrue(result is Result.Failure)
        assertEquals(UnitConversionError.InvalidRecipeServings(0), (result as Result.Failure).error)
    }

    @Test
    fun `computeDayTotal sums a recipe entry and a quick-add entry for the same day`() {
        // Given a day has "Bowl" (200g Rice + 150g Chicken, full recipe servings) plus a banana quick-add
        val recipe = RecipeWithIngredients(
            recipe = bowl(servings = 2),
            ingredients = listOf(
                RecipeIngredientDetail(
                    ingredient = RecipeIngredient(recipeId = 10L, foodItemId = 1L, quantity = 200.0, unit = MeasurementUnit.GRAM, sortOrder = 0),
                    foodItem = rice(),
                ),
                RecipeIngredientDetail(
                    ingredient = RecipeIngredient(recipeId = 10L, foodItemId = 2L, quantity = 150.0, unit = MeasurementUnit.GRAM, sortOrder = 1),
                    foodItem = chicken(),
                ),
            ),
        )
        val banana = FoodItem(
            id = 3L,
            name = "Banana",
            canonicalUnit = MeasurementUnit.PIECE,
            source = FoodItemSource.MANUAL,
            caloriesPerUnit = 89.0,
            proteinGramsPerUnit = 1.1,
            carbsGramsPerUnit = 22.8,
            fatGramsPerUnit = 0.3,
        )
        val entries = listOf(
            ResolvedDayEntry.RecipeEntry(recipe, requestedServings = 2.0),
            ResolvedDayEntry.QuickAddEntry(banana, quantity = 1.0),
        )

        // When the day's total is computed
        val result = useCase.computeDayTotal(entries, conversionFactorsByFoodItemId = emptyMap())

        // Then it's the recipe total (507.5 kcal) plus the quick-add (89 kcal) = 596.5
        assertTrue(result is Result.Success)
        assertEquals(596.5, (result as Result.Success).value.calories, 0.0001)
    }

    @Test
    fun `computeDayTotal propagates a recipe entry's unresolved-conversion failure`() {
        val recipe = RecipeWithIngredients(
            recipe = bowl(servings = 1),
            ingredients = listOf(
                RecipeIngredientDetail(
                    ingredient = RecipeIngredient(recipeId = 10L, foodItemId = 1L, quantity = 3.0, unit = MeasurementUnit.TABLESPOON, sortOrder = 0),
                    foodItem = rice(),
                ),
            ),
        )
        val entries = listOf(ResolvedDayEntry.RecipeEntry(recipe, requestedServings = 1.0))

        val result = useCase.computeDayTotal(entries, conversionFactorsByFoodItemId = emptyMap())

        assertTrue(result is Result.Failure)
    }

    @Test
    fun `MacroTotals ZERO is the additive identity`() {
        val totals = MacroTotals(1.0, 2.0, 3.0, 4.0)
        assertEquals(totals, MacroTotals.ZERO + totals)
    }
}
