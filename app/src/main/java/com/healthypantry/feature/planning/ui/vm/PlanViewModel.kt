package com.healthypantry.feature.planning.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.common.Result
import com.healthypantry.core.unit.ConversionFactor
import com.healthypantry.core.unit.UnitConversionError
import com.healthypantry.feature.pantry.data.repo.FoodItemRepository
import com.healthypantry.feature.pantry.data.repo.UnitConversionRepository
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.planning.data.repo.PlanEntryRepository
import com.healthypantry.feature.planning.domain.model.MealSlot
import com.healthypantry.feature.planning.domain.model.PlanEntry
import com.healthypantry.feature.planning.domain.model.PlanEntryType
import com.healthypantry.feature.planning.domain.usecase.ComputeWeeklyNeedsUseCase
import com.healthypantry.feature.planning.domain.usecase.MarkPlanEntryEatenUseCase
import com.healthypantry.feature.recipes.data.repo.RecipeRepository
import com.healthypantry.feature.recipes.domain.model.Recipe
import com.healthypantry.feature.recipes.domain.model.RecipeWithIngredients
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One [PlanEntry] joined with the display name of the [Recipe]/[FoodItem] it references. */
data class PlanEntryUi(
    val entry: PlanEntry,
    val displayName: String,
)

data class PlanUiState(
    val weekRange: WeekRange = currentWeekRange(),
    val entries: List<PlanEntryUi> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val foodItems: List<FoodItem> = emptyList(),
    /** `FoodItemId -> committed quantity` for the week, from [ComputeWeeklyNeedsUseCase]. */
    val weeklyNeeds: Map<Long, Double> = emptyMap(),
    val weeklyNeedsError: String? = null,
    val isLoading: Boolean = true,
)

/**
 * Spec: Weekly Plan Assignment, Weekly Ingredient Needs, Mark Plan Entry Eaten
 * (openspec/changes/pantry-tracker/specs/meal-planning/spec.md).
 *
 * Joins this week's [PlanEntryRepository.observeWeek] rows with [RecipeRepository.observeAll]/
 * [FoodItemRepository.observeAll] (for display names + assign/quick-add pickers) into [uiState],
 * plus resolves [ComputeWeeklyNeedsUseCase]'s `FoodItemId -> committed quantity` map so a later
 * pantry integration (or this screen itself) can show it. [markEaten] is the first production
 * caller of [MarkPlanEntryEatenUseCase].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanViewModel @Inject constructor(
    private val planEntryRepository: PlanEntryRepository,
    private val recipeRepository: RecipeRepository,
    private val foodItemRepository: FoodItemRepository,
    private val unitConversionRepository: UnitConversionRepository,
    private val computeWeeklyNeedsUseCase: ComputeWeeklyNeedsUseCase,
    private val markPlanEntryEatenUseCase: MarkPlanEntryEatenUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    val weekRange: WeekRange = currentWeekRange()

    val uiState: StateFlow<PlanUiState> = combine(
        planEntryRepository.observeWeek(weekRange.startEpochDay, weekRange.endEpochDay),
        recipeRepository.observeAll(),
        foodItemRepository.observeAll(),
    ) { entries, recipes, foodItems -> Triple(entries, recipes, foodItems) }
        .flatMapLatest { (entries, recipes, foodItems) ->
            resolveWeeklyNeeds(entries).map { needsResult -> buildUiState(entries, recipes, foodItems, needsResult) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PlanUiState(weekRange = weekRange, isLoading = true),
        )

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    private fun buildUiState(
        entries: List<PlanEntry>,
        recipes: List<Recipe>,
        foodItems: List<FoodItem>,
        needsResult: Result<Map<Long, Double>, UnitConversionError>,
    ): PlanUiState {
        val recipesById = recipes.associateBy { it.id }
        val foodItemsById = foodItems.associateBy { it.id }
        val entryUis = entries.map { entry ->
            val displayName = when (entry.type) {
                PlanEntryType.RECIPE -> recipesById[entry.recipeId]?.name ?: "Unknown recipe"
                PlanEntryType.ITEM -> foodItemsById[entry.foodItemId]?.name ?: "Unknown item"
            }
            PlanEntryUi(entry = entry, displayName = displayName)
        }
        return PlanUiState(
            weekRange = weekRange,
            entries = entryUis,
            recipes = recipes,
            foodItems = foodItems,
            weeklyNeeds = needsResult.getOrNull().orEmpty(),
            weeklyNeedsError = needsResult.errorOrNull()?.toUserMessage(),
            isLoading = false,
        )
    }

    /**
     * One-shot resolution of every RECIPE-referenced [RecipeWithIngredients] and every
     * referenced FoodItem's [ConversionFactor]s for [entries], then feeds them into
     * [ComputeWeeklyNeedsUseCase.compute]. Uses `.first()` one-shot repository reads (same
     * pattern as `MarkPlanEntryEatenUseCase.execute`) rather than a fully reactive nested-Flow
     * join, since this only needs to re-run when the week's own entry list changes.
     */
    private fun resolveWeeklyNeeds(entries: List<PlanEntry>): Flow<Result<Map<Long, Double>, UnitConversionError>> = flow {
        val recipeIds = entries.filter { it.type == PlanEntryType.RECIPE }.mapNotNull { it.recipeId }.toSet()
        val recipesWithIngredientsById: Map<Long, RecipeWithIngredients> = recipeIds
            .mapNotNull { id -> recipeRepository.observeRecipeWithIngredients(id).first()?.let { id to it } }
            .toMap()

        val foodItemIds = recipesWithIngredientsById.values
            .flatMap { withIngredients -> withIngredients.ingredients.map { it.foodItem.id } }
            .toSet() + entries.filter { it.type == PlanEntryType.ITEM }.mapNotNull { it.foodItemId }.toSet()
        val factorsByFoodItemId: Map<Long, List<ConversionFactor>> =
            foodItemIds.associateWith { id -> unitConversionRepository.observeForFoodItem(id).first() }

        emit(computeWeeklyNeedsUseCase.compute(entries, recipesWithIngredientsById, factorsByFoodItemId))
    }

    /** Assigns [recipeId] to [day]/[mealSlot] for [servings] (spec "Assign a recipe to a day"). */
    fun assignRecipe(day: Long, mealSlot: MealSlot, recipeId: Long, servings: Double) = launchOnIo {
        planEntryRepository.upsert(
            PlanEntry(
                dateEpochDay = day,
                mealSlot = mealSlot,
                type = PlanEntryType.RECIPE,
                recipeId = recipeId,
                servings = servings,
            ),
        )
    }

    /** Quick-adds [foodItemId] + [quantity] to [day]/[mealSlot], no recipe (spec "Quick-add a raw item"). */
    fun quickAddItem(day: Long, mealSlot: MealSlot, foodItemId: Long, quantity: Double) = launchOnIo {
        planEntryRepository.upsert(
            PlanEntry(
                dateEpochDay = day,
                mealSlot = mealSlot,
                type = PlanEntryType.ITEM,
                foodItemId = foodItemId,
                quantity = quantity,
            ),
        )
    }

    fun deleteEntry(entry: PlanEntry) = launchOnIo { planEntryRepository.delete(entry) }

    /**
     * Marks [entry] eaten via [MarkPlanEntryEatenUseCase] (spec "Marking eaten removes the entry
     * from projected deficit"). A typed [Result.Failure] (e.g. an unresolvable unit conversion)
     * surfaces through [errorEvent] the same way an unexpected repository exception does.
     */
    fun markEaten(entry: PlanEntry) = launchOnIo {
        when (val result = markPlanEntryEatenUseCase.execute(entry)) {
            is Result.Failure -> _errorEvent.emit(result.error.toUserMessage())
            is Result.Success -> Unit
        }
    }

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

private fun UnitConversionError.toUserMessage(): String = when (this) {
    is UnitConversionError.UnresolvedConversion ->
        "No conversion registered for $foodItemLabel from $fromUnit to $toUnit."
    is UnitConversionError.InvalidRecipeServings ->
        "Recipe has invalid servings ($recipeServings) - can't compute quantities."
}
