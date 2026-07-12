package com.healthypantry.feature.pantry.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.feature.pantry.data.repo.FoodItemRepository
import com.healthypantry.feature.pantry.data.repo.StockBatchRepository
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.StockBatch
import com.healthypantry.feature.pantry.domain.usecase.ComputeProjectedStockUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
 * `PlanEntry` (meal-planning) does not exist yet (PR7/PR8), so this ViewModel always passes a
 * committed quantity of `0.0` into [ComputeProjectedStockUseCase.compute] — projected stock
 * equals actual stock until PR8 wires a real commitment source in here.
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
        viewModelScope.launch(dispatcherProvider.io) { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
