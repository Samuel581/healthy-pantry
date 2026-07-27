package com.healthypantry.feature.recipes.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.common.Result
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.data.repo.FoodItemRepository
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.planning.domain.model.MacroTotals
import com.healthypantry.feature.planning.domain.usecase.ComputeMacroTotalsUseCase
import com.healthypantry.feature.recipes.data.repo.RecipeRepository
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.domain.model.RecipeIngredient
import com.healthypantry.feature.recipes.domain.model.RecipeWithIngredients
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One editable ingredient line inside [RecipeFormState.ingredients]. */
data class RecipeIngredientFormRow(
    val foodItemId: Long? = null,
    val quantity: String = "",
    val unit: MeasurementUnit = MeasurementUnit.GRAM,
)

/**
 * Editable state for the recipe create/edit form. Kept as raw [String] for [servings]/ingredient
 * [RecipeIngredientFormRow.quantity] (same rationale as `ItemFormUiState`'s macro fields: an
 * `OutlinedTextField` can bind directly while the user is mid-edit); only parsed at save time by
 * [RecipeViewModel.saveRecipe].
 */
data class RecipeFormState(
    val id: Long = 0L,
    val name: String = "",
    val servings: String = "1",
    val notes: String = "",
    val createdAt: Instant = Instant.now(),
    val ingredients: List<RecipeIngredientFormRow> = emptyList(),
)

/** Precomputed per-recipe macro rollup for one recipe card in `RecipeListContent`'s list (spec
 * "Recipe total from ingredients"). [macroTotals] is best-effort: an
 * unresolved ingredient unit conversion (no registered [com.healthypantry.core.unit.ConversionFactor]
 * and the ingredient's unit differs from its FoodItem's canonical unit) falls back to
 * [MacroTotals.ZERO] with `isComplete = false` rather than crashing the list — per-item
 * conversion-factor registration for the recipes list is out of scope for this restyle (the same
 * "PR8" macro-rollup wiring `RecipeIngredient`/`RecipeWithIngredients` KDoc already defers). */
data class RecipeMacroSummary(
    val ingredientCount: Int,
    val macroTotals: MacroTotals,
) {
    companion object {
        val EMPTY = RecipeMacroSummary(ingredientCount = 0, macroTotals = MacroTotals.ZERO.copy(isComplete = false))
    }
}

data class RecipeUiState(
    val recipes: List<Recipe> = emptyList(),
    val foodItems: List<FoodItem> = emptyList(),
    val macroSummariesByRecipeId: Map<Long, RecipeMacroSummary> = emptyMap(),
    val isLoading: Boolean = true,
)

/**
 * Spec: Recipe CRUD with Ingredients
 * (openspec/changes/pantry-tracker/specs/meal-planning/spec.md).
 *
 * Joins [RecipeRepository.observeAll] with [FoodItemRepository.observeAll] (needed so
 * `RecipeScreen`'s ingredient picker can list available pantry items) into a single [uiState],
 * plus a separate [formState] for the create/edit form — following the same errorEvent/
 * launchOnIo convention as `PantryViewModel`.
 *
 * Unlike the pantry slice (which splits list vs form across `PantryViewModel`/
 * `ItemFormViewModel`), this single ViewModel owns both — [RecipeIngredient] has no independent
 * lifecycle apart from its owning [Recipe] (see [RecipeRepository] KDoc), so there's no benefit
 * to a second ViewModel here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecipeViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val foodItemRepository: FoodItemRepository,
    private val computeMacroTotalsUseCase: ComputeMacroTotalsUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    /**
     * [recipes]/[foodItems] combine reactively (unchanged), but each recipe's
     * [RecipeMacroSummary] is resolved once per emission via a plain `flow { ... first() }`
     * builder — same "resolve once, not a fully reactive nested-Flow join" convention
     * `PlanViewModel.resolveWeeklyNeeds` already established, since the summaries only need to
     * re-run when the recipe/food-item lists themselves change, not on every unrelated
     * ingredient-table write elsewhere.
     */
    val uiState: StateFlow<RecipeUiState> =
        combine(recipeRepository.observeAll(), foodItemRepository.observeAll()) { recipes, foodItems ->
            recipes to foodItems
        }.flatMapLatest { (recipes, foodItems) ->
            flow {
                val summaries = recipes.associate { recipe ->
                    val withIngredients = recipeRepository.observeRecipeWithIngredients(recipe.id).first()
                    recipe.id to macroSummaryFor(withIngredients)
                }
                emit(
                    RecipeUiState(
                        recipes = recipes,
                        foodItems = foodItems,
                        macroSummariesByRecipeId = summaries,
                        isLoading = false,
                    ),
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = RecipeUiState(isLoading = true),
        )

    /** No registered [com.healthypantry.core.unit.ConversionFactor]s are resolved here (see
     * [RecipeMacroSummary] KDoc) — every ingredient's own [ComputeMacroTotalsUseCase.computeForRecipe]
     * conversion still succeeds whenever its unit already matches its FoodItem's canonical unit
     * ([com.healthypantry.core.unit.UnitConverter.convert] short-circuits on equal units), which
     * covers the common case; a genuine cross-unit mismatch degrades to an incomplete zero total
     * instead of surfacing a [com.healthypantry.core.unit.UnitConversionError] to this list. */
    private fun macroSummaryFor(withIngredients: RecipeWithIngredients?): RecipeMacroSummary {
        if (withIngredients == null) return RecipeMacroSummary.EMPTY
        val result = computeMacroTotalsUseCase.computeForRecipe(
            recipeWithIngredients = withIngredients,
            requestedServings = withIngredients.recipe.servings.toDouble(),
            conversionFactorsByFoodItemId = emptyMap(),
        )
        val macroTotals = when (result) {
            is Result.Success -> result.value
            is Result.Failure -> MacroTotals.ZERO.copy(isComplete = false)
        }
        return RecipeMacroSummary(ingredientCount = withIngredients.ingredients.size, macroTotals = macroTotals)
    }

    private val _formState = MutableStateFlow(RecipeFormState())
    val formState: StateFlow<RecipeFormState> = _formState.asStateFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    /** Resets [formState] to a blank recipe, ready for "create" mode. */
    fun startNewRecipe() {
        _formState.value = RecipeFormState()
    }

    /** Loads [id]'s persisted recipe + ingredients into [formState] for "edit" mode. */
    fun loadRecipeForEdit(id: Long) = launchOnIo {
        val withIngredients = recipeRepository.observeRecipeWithIngredients(id).first() ?: return@launchOnIo
        _formState.value = RecipeFormState(
            id = withIngredients.recipe.id,
            name = withIngredients.recipe.name,
            servings = withIngredients.recipe.servings.toString(),
            notes = withIngredients.recipe.notes.orEmpty(),
            createdAt = withIngredients.recipe.createdAt,
            ingredients = withIngredients.ingredients.map { detail ->
                RecipeIngredientFormRow(
                    foodItemId = detail.foodItem.id,
                    quantity = detail.ingredient.quantity.toString(),
                    unit = detail.ingredient.unit,
                )
            },
        )
    }

    fun onNameChanged(value: String) = _formState.update { it.copy(name = value) }
    fun onServingsChanged(value: String) = _formState.update { it.copy(servings = value) }
    fun onNotesChanged(value: String) = _formState.update { it.copy(notes = value) }

    fun addIngredientRow() =
        _formState.update { it.copy(ingredients = it.ingredients + RecipeIngredientFormRow()) }

    fun updateIngredientRow(index: Int, row: RecipeIngredientFormRow) = _formState.update { state ->
        state.copy(ingredients = state.ingredients.mapIndexed { i, existing -> if (i == index) row else existing })
    }

    fun removeIngredientRow(index: Int) = _formState.update { state ->
        state.copy(ingredients = state.ingredients.filterIndexed { i, _ -> i != index })
    }

    /**
     * Persists [formState] via [RecipeRepository.upsertRecipeWithIngredients]. Ingredient rows
     * missing a picked [RecipeIngredientFormRow.foodItemId] or an unparseable quantity are
     * dropped rather than blocking the whole save — same "best-effort parse, save what's valid"
     * approach as `ItemFormUiState.toFoodItem`.
     */
    fun saveRecipe() {
        val form = _formState.value
        val recipe = Recipe(
            id = form.id,
            name = form.name,
            servings = form.servings.toIntOrNull() ?: 1,
            notes = form.notes.ifBlank { null },
            createdAt = form.createdAt,
        )
        val ingredients = form.ingredients.mapIndexedNotNull { index, row ->
            val foodItemId = row.foodItemId ?: return@mapIndexedNotNull null
            val quantity = row.quantity.toDoubleOrNull() ?: return@mapIndexedNotNull null
            RecipeIngredient(
                recipeId = recipe.id,
                foodItemId = foodItemId,
                quantity = quantity,
                unit = row.unit,
                sortOrder = index,
            )
        }
        launchOnIo { recipeRepository.upsertRecipeWithIngredients(recipe, ingredients) }
    }

    fun deleteRecipe(recipe: Recipe) = launchOnIo { recipeRepository.delete(recipe) }

    private fun launchOnIo(block: suspend () -> Unit) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _errorEvent.emit(e.message ?: "Something went wrong")
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
