package com.healthypantry.feature.nutrition.data

import com.healthypantry.core.common.Result
import com.healthypantry.feature.nutrition.data.off.OpenFoodFactsNutritionSource
import com.healthypantry.feature.nutrition.data.usda.UsdaNutritionSource
import com.healthypantry.feature.nutrition.domain.model.NutritionLookupError
import com.healthypantry.feature.nutrition.domain.model.NutritionResult
import javax.inject.Inject

/**
 * Single entry point for nutrition lookups (spec "Barcode Scan, Manual Entry with USDA
 * Fallback") so callers (PR6 `ItemFormScreen`/`PantryViewModel`) don't need to know which
 * source answered.
 */
interface NutritionLookupRepository {
    /** Tries [OpenFoodFactsNutritionSource] only — see [NutritionLookupRepositoryImpl] for why. */
    suspend fun lookupByBarcode(barcode: String): Result<NutritionResult, NutritionLookupError>

    /** Delegates to [UsdaNutritionSource]. */
    suspend fun searchByName(name: String): Result<List<NutritionResult>, NutritionLookupError>
}

/**
 * Composes [OpenFoodFactsNutritionSource] (barcode) and [UsdaNutritionSource] (manual
 * name-search) behind [NutritionLookupRepository], per design.md "Nutrition lookup
 * composition". This is intentionally a thin pass-through/delegation layer — mapping and
 * error-handling logic already live in each source ([OpenFoodFactsNutritionSourceTest]/
 * [UsdaNutritionSourceTest] cover those), so this class must not duplicate any of it.
 *
 * Composition rule:
 * - [lookupByBarcode] tries [openFoodFactsSource] only and returns its result unchanged. USDA
 *   FoodData Central has no barcode search, so a miss here is **not** auto-followed by a USDA
 *   query — per spec scenario "Barcode not found in Open Food Facts", the miss surfaces to the
 *   caller (PR6 UI) so *it* can fall back to the manual-entry name-search flow, which calls
 *   [searchByName] itself.
 * - [searchByName] delegates to [usdaSource] (spec scenario "Manual name search against USDA
 *   FoodData Central").
 *
 * Manual macro override precedence (spec scenario "Manual macro override always wins") is
 * intentionally **not** implemented here: this repository is a stateless lookup, with no
 * knowledge of a persisted `FoodItem`'s user-edited macros. That precedence rule belongs to the
 * `FoodItem` persistence/form layer in PR6 (`ItemFormScreen`/`PantryViewModel`), which decides
 * whether to apply a fresh lookup result over already-persisted manual values.
 */
class NutritionLookupRepositoryImpl @Inject constructor(
    private val openFoodFactsSource: OpenFoodFactsNutritionSource,
    private val usdaSource: UsdaNutritionSource,
) : NutritionLookupRepository {

    override suspend fun lookupByBarcode(barcode: String): Result<NutritionResult, NutritionLookupError> =
        openFoodFactsSource.lookupByBarcode(barcode)

    override suspend fun searchByName(name: String): Result<List<NutritionResult>, NutritionLookupError> =
        usdaSource.searchByName(name)
}
