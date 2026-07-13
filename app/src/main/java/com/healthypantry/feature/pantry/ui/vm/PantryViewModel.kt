package com.healthypantry.feature.pantry.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthypantry.core.common.DispatcherProvider
import kotlinx.coroutines.CancellationException
import com.healthypantry.feature.pantry.data.repo.FoodItemRepository
import com.healthypantry.feature.pantry.data.repo.StockBatchRepository
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.StockBatch
import com.healthypantry.feature.pantry.domain.usecase.ComputeProjectedStockUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
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
 * TODO(PR9 follow-up, not closed in this PR): `committedQuantity` below is still hardcoded to
 * `0.0` — projected stock still equals actual stock. A real source now exists:
 * `com.healthypantry.feature.planning.ui.vm.PlanViewModel.uiState.weeklyNeeds` already computes
 * the `FoodItemId -> committed quantity` map for the current week via
 * [ComputeWeeklyNeedsUseCase][com.healthypantry.feature.planning.domain.usecase.ComputeWeeklyNeedsUseCase].
 * Wiring it in here was deliberately left out of PR9 rather than forced in: it would make this
 * ViewModel depend on `PlanEntryRepository`/`RecipeRepository`/`UnitConversionRepository` (a
 * pantry-feature ViewModel reaching into planning/recipes), plus reactively re-resolve every
 * referenced recipe's ingredients and every referenced item's conversion factors per pantry row
 * (the same one-shot-`.first()`-per-recipe resolution `PlanViewModel.resolveWeeklyNeeds` already
 * does) — a meaningful scope/complexity increase for a Phase 9 UI task, and a cross-feature
 * dependency this ViewModel doesn't otherwise have. Once nav/integration (Phase 11) exists and a
 * shared "current week needs" source is decided (e.g. hoisted above both ViewModels, or read
 * from a shared repository-level cache instead of two independent per-screen computations), this
 * should be revisited instead of adding a second independent computation of the same map here.
 *
 * `PlanEntry` (meal-planning) does not exist yet (PR7/PR8), so this ViewModel always passes a
 * committed quantity of `0.0` into [ComputeProjectedStockUseCase.compute] — projected stock
 * equals actual stock until this TODO above is picked up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PantryViewModel @Inject constructor(
    private val foodItemRepository: FoodItemRepository,
    private val stockBatchRepository: StockBatchRepository,
    private val computeProjectedStockUseCase: ComputeProjectedStockUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    val uiState: StateFlow<PantryUiState> =
        foodItemRepository.observeAll()
            .flatMapLatest { items -> observeRowsFor(items) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = PantryUiState(isLoading = true),
            )

    private val _errorEvent = MutableSharedFlow<String>()

    /**
     * One-off failures from [addItem]/[updateItem]/[deleteItem]/[addStockBatch] (e.g. a Room
     * constraint violation from an unrelated concurrent delete). A [SharedFlow], not part of
     * [uiState], since these are transient events (surface a snackbar) rather than persistent
     * UI state.
     */
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    private fun observeRowsFor(items: List<FoodItem>): Flow<PantryUiState> {
        if (items.isEmpty()) {
            return flowOf(PantryUiState(items = emptyList(), isLoading = false))
        }
        val rowFlows = items.map { item -> observeRow(item) }
        return combine(rowFlows) { rows -> PantryUiState(items = rows.toList(), isLoading = false) }
    }

    private fun observeRow(item: FoodItem): Flow<PantryItemUi> =
        stockBatchRepository.observeTotalOnHand(item.id).map { actualStock ->
            PantryItemUi(
                foodItem = item,
                actualStock = actualStock,
                projectedStock = computeProjectedStockUseCase.compute(actualStock, committedQuantity = 0.0),
            )
        }

    fun addItem(item: FoodItem) = launchOnIo { foodItemRepository.upsert(item) }

    fun updateItem(item: FoodItem) = launchOnIo { foodItemRepository.upsert(item) }

    fun deleteItem(item: FoodItem) = launchOnIo { foodItemRepository.delete(item) }

    fun addStockBatch(batch: StockBatch) = launchOnIo { stockBatchRepository.upsert(batch) }

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
