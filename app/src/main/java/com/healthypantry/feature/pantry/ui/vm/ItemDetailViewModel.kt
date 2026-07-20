package com.healthypantry.feature.pantry.ui.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.unit.ConversionFactor
import com.healthypantry.feature.pantry.data.repo.FoodItemRepository
import com.healthypantry.feature.pantry.data.repo.StockBatchRepository
import com.healthypantry.feature.pantry.data.repo.UnitConversionRepository
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.StockBatch
import com.healthypantry.feature.pantry.domain.usecase.ComputeProjectedStockUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * State for `ItemDetailScreen`: a single [FoodItem] joined with its [StockBatch]es and its
 * registered [ConversionFactor]s (PR3a - item detail screen).
 *
 * [actualStock] mirrors [StockBatchRepository.observeTotalOnHand] for [FoodItem.id]. [projectedStock]
 * reuses [ComputeProjectedStockUseCase] the same way `PantryViewModel` does for the list, but with
 * a `committedQuantity` of `0.0`: resolving the real committed quantity requires the same
 * cross-feature `PlanEntry`/`RecipeWithIngredients`/`ConversionFactor` resolution
 * `PantryViewModel.resolveCommittedQuantities` already owns, which is deliberately out of scope
 * for this UI-only slice (PR3a) - wiring it here would either duplicate that resolution or require
 * this screen to inject `PantryViewModel` itself (see `ItemDetailViewModel`'s own KDoc). Until a
 * later PR wires that in, [projectedStock] equals [actualStock].
 */
data class ItemDetailUiState(
    val foodItem: FoodItem? = null,
    val batches: List<StockBatch> = emptyList(),
    val conversions: List<ConversionFactor> = emptyList(),
    val actualStock: Double = 0.0,
    val projectedStock: Double = 0.0,
    val isLoading: Boolean = true,
)

/**
 * Spec: Item and Stock Batch CRUD, Unit Conversion Correctness, Projected vs Actual Stock
 * (openspec/changes/pantry-tracker/specs/pantry-stock/spec.md,
 * openspec/changes/pantry-tracker/specs/unit-conversion/spec.md).
 *
 * Backs `ItemDetailScreen` (PR3a): the item's own name/macros, its batches (add/delete), its
 * registered unit conversions (read-only display here), and item deletion.
 *
 * Takes the item id via [SavedStateHandle] (`"itemId"`, matching `Destinations.ITEM_DETAIL`'s nav
 * arg in `HealthyPantryNavHost`) rather than a constructor param, since Hilt's nav-arg injection
 * only supports [SavedStateHandle] - there's no existing nav-arg-driven ViewModel in this codebase
 * to mirror (`ItemFormScreen`'s `itemId` is instead resolved by the NavHost itself and passed down
 * as a Composable param), so this is the first.
 *
 * Injects the same repositories `PantryViewModel` injects (not `PantryViewModel` itself) for
 * persistence: this codebase has no existing ViewModel-injects-ViewModel pattern, and
 * [androidx.lifecycle.ViewModel]s are scoped to their own `NavBackStackEntry`/Activity by
 * [hiltViewModel][androidx.hilt.navigation.compose.hiltViewModel] - injecting `PantryViewModel`
 * here would tie this screen's lifecycle to whichever scope `PantryViewModel` happens to resolve
 * against (its own default `hiltViewModel()` call site) instead of this screen's, and would pull
 * in `PantryViewModel`'s much larger dependency graph (planning/recipes) for features this screen
 * doesn't need. Repository-level injection keeps this ViewModel's dependencies scoped to what it
 * actually uses, exactly like `PantryViewModel` and `ItemFormViewModel` already do.
 */
@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val foodItemRepository: FoodItemRepository,
    private val stockBatchRepository: StockBatchRepository,
    private val unitConversionRepository: UnitConversionRepository,
    private val computeProjectedStockUseCase: ComputeProjectedStockUseCase,
    private val clock: Clock,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val itemId: Long = checkNotNull(savedStateHandle.get<Long>(ITEM_ID_ARG)) {
        "ItemDetailViewModel requires a non-null \"$ITEM_ID_ARG\" nav arg"
    }

    val uiState: StateFlow<ItemDetailUiState> = combine(
        foodItemRepository.observeById(itemId),
        stockBatchRepository.observeForFoodItem(itemId),
        unitConversionRepository.observeForFoodItem(itemId),
        stockBatchRepository.observeTotalOnHand(itemId),
    ) { foodItem, batches, conversions, actualStock ->
        ItemDetailUiState(
            foodItem = foodItem,
            batches = batches,
            conversions = conversions,
            actualStock = actualStock,
            projectedStock = computeProjectedStockUseCase.compute(actualStock, committedQuantity = 0.0),
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ItemDetailUiState(isLoading = true),
    )

    private val _errorEvent = MutableSharedFlow<String>()

    /** One-off failures from [addBatch]/[deleteBatch]/[deleteItem], surfaced as a Snackbar - same
     * convention as [PantryViewModel.errorEvent]. */
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    fun addBatch(quantity: Double, expiryDate: LocalDate?) = launchOnIo {
        stockBatchRepository.upsert(
            StockBatch(
                foodItemId = itemId,
                quantity = quantity,
                expiryDate = expiryDate,
                addedAt = Instant.now(clock),
            ),
        )
    }

    fun deleteBatch(batch: StockBatch) = launchOnIo { stockBatchRepository.delete(batch) }

    /**
     * Fire-and-forget, like [PantryViewModel.deleteItem] - the caller (`ItemDetailScreen`) is
     * expected to invoke its `onItemDeleted` callback immediately after calling this rather than
     * waiting for persistence to confirm, matching `ItemFormScreen`'s save flow. A failure (e.g. an
     * unrelated concurrent delete) still surfaces via [errorEvent] even though navigation has
     * already moved on.
     */
    fun deleteItem() = launchOnIo {
        uiState.value.foodItem?.let { foodItemRepository.delete(it) }
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

    companion object {
        /** Must match `Destinations.ITEM_DETAIL`'s `{itemId}` nav arg name in `HealthyPantryNavHost`. */
        const val ITEM_ID_ARG = "itemId"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
