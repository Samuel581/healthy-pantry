package com.healthypantry.feature.pantry.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.unit.ConversionFactor
import kotlinx.coroutines.CancellationException
import com.healthypantry.feature.pantry.data.repo.FoodItemRepository
import com.healthypantry.feature.pantry.data.repo.StockBatchRepository
import com.healthypantry.feature.pantry.data.repo.UnitConversionRepository
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.StockBatch
import com.healthypantry.feature.pantry.domain.usecase.ComputeProjectedStockUseCase
import com.healthypantry.feature.planning.data.repo.PlanEntryRepository
import com.healthypantry.feature.planning.domain.model.PlanEntry
import com.healthypantry.feature.planning.domain.model.PlanEntryType
import com.healthypantry.feature.planning.domain.usecase.ComputeWeeklyNeedsUseCase
import com.healthypantry.feature.planning.ui.vm.currentWeekRange
import com.healthypantry.feature.recipes.data.repo.RecipeRepository
import com.healthypantry.feature.recipes.domain.model.RecipeWithIngredients
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A single pantry row: a [FoodItem] joined with its actual stock
 * ([StockBatchRepository.observeTotalOnHand]) and projected stock
 * ([ComputeProjectedStockUseCase]).
 */
data class PantryItemUi(
    val foodItem: FoodItem,
    val actualStock: Double,
    val projectedStock: Double,
)

data class PantryUiState(
    val items: List<PantryItemUi> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Spec: Item and Stock Batch CRUD, Projected vs Actual Stock
 * (openspec/changes/pantry-tracker/specs/pantry-stock/spec.md).
 *
 * Joins [FoodItemRepository.observeAll] with each item's actual stock and projected stock into
 * a single [uiState] for `feature/pantry/ui/screen` Compose screens (PR6).
 *
 * Projected stock subtracts this week's not-yet-eaten committed quantity (spec "Projected vs
 * Actual Stock"): [resolveCommittedQuantities] mirrors
 * `PlanViewModel.resolveWeeklyNeeds`'s one-shot recipe/conversion-factor resolution feeding
 * [ComputeWeeklyNeedsUseCase] — the "FoodItemId -> committed quantity" map that use-case computes
 * is exactly what [ComputeProjectedStockUseCase.compute] accepts as `committedQuantity`. This is
 * a deliberate cross-feature dependency (this ViewModel now reaches into
 * `PlanEntryRepository`/`RecipeRepository`/`UnitConversionRepository`, and reuses
 * `com.healthypantry.feature.planning.ui.vm.currentWeekRange`, a UI-layer helper from the
 * planning feature) rather than a shared repository-level cache — the KDoc this replaced flagged
 * exactly this tradeoff as a possible follow-up; picking it up here still duplicates
 * `PlanViewModel.resolveWeeklyNeeds`'s resolution rather than sharing it, which remains a real
 * candidate for a later refactor once both screens are wired into navigation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PantryViewModel @Inject constructor(
    private val foodItemRepository: FoodItemRepository,
    private val stockBatchRepository: StockBatchRepository,
    private val computeProjectedStockUseCase: ComputeProjectedStockUseCase,
    private val planEntryRepository: PlanEntryRepository,
    private val recipeRepository: RecipeRepository,
    private val unitConversionRepository: UnitConversionRepository,
    private val computeWeeklyNeedsUseCase: ComputeWeeklyNeedsUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val weekRange = currentWeekRange()

    val uiState: StateFlow<PantryUiState> =
        combine(foodItemRepository.observeAll(), observeCommittedQuantities()) { items, committedQuantities ->
            items to committedQuantities
        }
            .flatMapLatest { (items, committedQuantities) -> observeRowsFor(items, committedQuantities) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = PantryUiState(isLoading = true),
            )

    private val _errorEvent = MutableSharedFlow<String>()

    /**
     * One-off failures from [addItem]/[updateItem]/[deleteItem]/[addStockBatch]/[deleteStockBatch] (e.g. a Room
     * constraint violation from an unrelated concurrent delete). A [SharedFlow], not part of
     * [uiState], since these are transient events (surface a snackbar) rather than persistent
     * UI state.
     */
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    private fun observeRowsFor(items: List<FoodItem>, committedQuantities: Map<Long, Double>): Flow<PantryUiState> {
        if (items.isEmpty()) {
            return flowOf(PantryUiState(items = emptyList(), isLoading = false))
        }
        val rowFlows = items.map { item -> observeRow(item, committedQuantities) }
        return combine(rowFlows) { rows -> PantryUiState(items = rows.toList(), isLoading = false) }
    }

    private fun observeRow(item: FoodItem, committedQuantities: Map<Long, Double>): Flow<PantryItemUi> =
        stockBatchRepository.observeTotalOnHand(item.id).map { actualStock ->
            PantryItemUi(
                foodItem = item,
                actualStock = actualStock,
                projectedStock = computeProjectedStockUseCase.compute(
                    actualStock,
                    committedQuantity = committedQuantities[item.id] ?: 0.0,
                ),
            )
        }

    /**
     * This week's `FoodItemId -> committed quantity` map, re-resolved every time the week's plan
     * entries change (spec "Projected vs Actual Stock").
     */
    private fun observeCommittedQuantities(): Flow<Map<Long, Double>> =
        planEntryRepository.observeWeek(weekRange.startEpochDay, weekRange.endEpochDay)
            .map { entries -> resolveCommittedQuantities(entries) }

    /**
     * One-shot resolution of every RECIPE-referenced [RecipeWithIngredients] and every referenced
     * FoodItem's [ConversionFactor]s for [entries], then feeds them into
     * [ComputeWeeklyNeedsUseCase.compute] — identical resolution to
     * `PlanViewModel.resolveWeeklyNeeds`.
     *
     * A [com.healthypantry.core.common.Result.Failure] (e.g. an unresolvable unit conversion)
     * falls back to an empty map (every item's committed quantity defaults to zero, so its
     * projected stock falls back to actual stock) rather than surfacing an error here: a
     * planning-side data problem shouldn't block the whole pantry list from rendering, and
     * `PlanViewModel`'s own `weeklyNeedsError` is still the place that surfaces it to the user.
     */
    private suspend fun resolveCommittedQuantities(entries: List<PlanEntry>): Map<Long, Double> {
        val recipeIds = entries.filter { it.type == PlanEntryType.RECIPE }.mapNotNull { it.recipeId }.toSet()
        val recipesWithIngredientsById: Map<Long, RecipeWithIngredients> = recipeIds
            .mapNotNull { id -> recipeRepository.observeRecipeWithIngredients(id).first()?.let { id to it } }
            .toMap()

        val foodItemIds = recipesWithIngredientsById.values
            .flatMap { withIngredients -> withIngredients.ingredients.map { it.foodItem.id } }
            .toSet() + entries.filter { it.type == PlanEntryType.ITEM }.mapNotNull { it.foodItemId }.toSet()
        val factorsByFoodItemId: Map<Long, List<ConversionFactor>> =
            foodItemIds.associateWith { id -> unitConversionRepository.observeForFoodItem(id).first() }

        return computeWeeklyNeedsUseCase.compute(entries, recipesWithIngredientsById, factorsByFoodItemId)
            .getOrNull()
            .orEmpty()
    }

    /**
     * Inserts a new item or updates an existing one, returning its id. A plain suspend function
     * (not fire-and-forget like [updateItem]/[deleteItem]) so the caller can await the new id for
     * an id-dependent follow-up (e.g. attaching a starting [StockBatch]) — the caller (a later
     * PR's screen) is expected to launch this itself via `viewModelScope.launch` and handle a
     * thrown exception, rather than this ViewModel catching it internally.
     */
    suspend fun addItem(item: FoodItem): Long = foodItemRepository.upsert(item)

    fun updateItem(item: FoodItem) = launchOnIo { foodItemRepository.upsert(item) }

    fun deleteItem(item: FoodItem) = launchOnIo { foodItemRepository.delete(item) }

    fun getItem(id: Long): Flow<FoodItem?> = foodItemRepository.observeById(id)

    fun addStockBatch(batch: StockBatch) = launchOnIo { stockBatchRepository.upsert(batch) }

    /** Per-item batch list for an item-detail screen (spec "Item and Stock Batch CRUD"). */
    fun observeBatchesForItem(foodItemId: Long): Flow<List<StockBatch>> =
        stockBatchRepository.observeForFoodItem(foodItemId)

    fun deleteStockBatch(batch: StockBatch) = launchOnIo { stockBatchRepository.delete(batch) }

    private fun launchOnIo(block: suspend () -> Unit) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                block()
            } catch (e: Exception) {
                // Repository calls are plain suspend functions that can throw (e.g. a Room
                // FOREIGN KEY violation on addStockBatch for a since-deleted item) - catch here
                // so a single failed action surfaces as a recoverable event instead of crashing
                // the whole screen. CancellationException is rethrown so cancelling a launch
                // (e.g. ViewModel cleared) isn't mistaken for a real failure.
                if (e is CancellationException) throw e
                _errorEvent.emit(e.message ?: "Something went wrong")
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
