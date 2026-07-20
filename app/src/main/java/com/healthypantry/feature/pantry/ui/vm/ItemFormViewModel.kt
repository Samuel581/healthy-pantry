package com.healthypantry.feature.pantry.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.core.common.Result
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.nutrition.data.NutritionLookupRepository
import com.healthypantry.feature.nutrition.domain.model.NutritionLookupError
import com.healthypantry.feature.nutrition.domain.model.NutritionResult
import com.healthypantry.feature.pantry.domain.model.FoodItem
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns [ItemFormUiState] for `ItemFormScreen` (spec "Barcode Scan via Open Food Facts", "Manual
 * Entry with USDA FDC Fallback", nutrition-lookup domain). Persistence is
 * [PantryViewModel.addItem]/[PantryViewModel.updateItem]'s job - this ViewModel only resolves
 * lookups into form state and never touches `FoodItemRepository` directly, so `upsert` logic
 * stays in one place.
 */
@HiltViewModel
class ItemFormViewModel @Inject constructor(
    private val nutritionLookupRepository: NutritionLookupRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemFormUiState())
    val uiState: StateFlow<ItemFormUiState> = _uiState.asStateFlow()

    /**
     * Seeds the form from an already-persisted [item] for edit mode. Every field starts
     * "touched" so a stray barcode scan or USDA search while editing can't silently overwrite
     * already-saved values without an explicit further edit.
     *
     * A `null` macro on [item] (unknown, see [FoodItem] KDoc) seeds the field as an empty string
     * rather than the literal text `"null"`, so [ItemFormUiState.toFoodItem] round-trips it back
     * to `null` on the next save unless the user actually types a value.
     */
    fun loadExisting(item: FoodItem) {
        _uiState.update {
            it.copy(
                name = item.name,
                canonicalUnit = item.canonicalUnit,
                caloriesPerUnit = item.caloriesPerUnit?.toString().orEmpty(),
                proteinGramsPerUnit = item.proteinGramsPerUnit?.toString().orEmpty(),
                carbsGramsPerUnit = item.carbsGramsPerUnit?.toString().orEmpty(),
                fatGramsPerUnit = item.fatGramsPerUnit?.toString().orEmpty(),
                source = item.source,
                barcode = item.barcode,
                externalSourceId = item.externalSourceId,
                touchedFields = ItemFormField.entries.toSet(),
            )
        }
    }

    fun onNameChanged(value: String) = touch(ItemFormField.NAME) { it.copy(name = value) }

    fun onCanonicalUnitChanged(unit: MeasurementUnit) =
        touch(ItemFormField.UNIT) { it.copy(canonicalUnit = unit) }

    fun onCaloriesChanged(value: String) = touch(ItemFormField.CALORIES) { it.copy(caloriesPerUnit = value) }

    fun onProteinChanged(value: String) = touch(ItemFormField.PROTEIN) { it.copy(proteinGramsPerUnit = value) }

    fun onCarbsChanged(value: String) = touch(ItemFormField.CARBS) { it.copy(carbsGramsPerUnit = value) }

    fun onFatChanged(value: String) = touch(ItemFormField.FAT) { it.copy(fatGramsPerUnit = value) }

    fun onUsdaQueryChanged(value: String) = _uiState.update { it.copy(usdaQuery = value) }

    private fun touch(field: ItemFormField, transform: (ItemFormUiState) -> ItemFormUiState) {
        _uiState.update { transform(it).copy(touchedFields = it.touchedFields + field) }
    }

    /**
     * Spec scenario "Successful OFF match" / "OFF data gap" - a miss or incomplete data falls
     * back to the (already rendered) manual-entry fields instead of blocking the form.
     */
    fun onBarcodeScanned(barcode: String) {
        _uiState.update { it.copy(isLookingUp = true, lookupError = null) }
        launchLookup {
            when (val result = nutritionLookupRepository.lookupByBarcode(barcode)) {
                is Result.Success -> _uiState.update {
                    it.prefillFrom(result.value, source = FoodItemSource.BARCODE, barcode = barcode)
                        .copy(isLookingUp = false)
                }

                is Result.Failure -> _uiState.update {
                    it.copy(isLookingUp = false, barcode = barcode, lookupError = result.error.toUserMessage())
                }
            }
        }
    }

    /**
     * Spec scenario "USDA search success" / "Missing USDA API key" - a configuration/rate-limit
     * error is surfaced but manual entry always remains available.
     */
    fun onUsdaSearch(query: String) {
        _uiState.update { it.copy(isLookingUp = true, lookupError = null, searchResults = emptyList()) }
        launchLookup {
            when (val result = nutritionLookupRepository.searchByName(query)) {
                is Result.Success -> _uiState.update {
                    it.copy(isLookingUp = false, searchResults = result.value)
                }

                is Result.Failure -> _uiState.update {
                    it.copy(isLookingUp = false, lookupError = result.error.toUserMessage())
                }
            }
        }
    }

    fun onResultSelected(result: NutritionResult) {
        _uiState.update {
            it.prefillFrom(result, source = FoodItemSource.MANUAL)
                .copy(searchResults = emptyList())
        }
    }

    /** Builds the [FoodItem] to persist from the current form state (see [ItemFormUiState.toFoodItem]). */
    fun buildFoodItem(existingId: Long = 0L): FoodItem = uiState.value.toFoodItem(existingId)

    /**
     * Runs a lookup [block] on [DispatcherProvider.io], guarding against any exception the OFF/USDA
     * sources don't already convert into a typed [Result] (e.g. a malformed community-sourced OFF
     * payload) - mirrors `PantryViewModel.launchOnIo`'s catch-all so a lookup failure degrades to
     * manual entry instead of crashing the form.
     */
    private fun launchLookup(block: suspend () -> Unit) {
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLookingUp = false, lookupError = e.message ?: "Lookup failed. You can enter macros manually.")
                }
            }
        }
    }
}

private fun NutritionLookupError.toUserMessage(): String = when (this) {
    is NutritionLookupError.NotFound -> "No match found. You can enter macros manually."
    is NutritionLookupError.RateLimited ->
        "USDA lookup is rate-limited (configure USDA_FDC_API_KEY in local.properties). " +
            "You can enter macros manually."

    is NutritionLookupError.ApiError -> "Lookup failed (code $code). You can enter macros manually."
    is NutritionLookupError.NetworkError -> "Network error during lookup. You can enter macros manually."
}
