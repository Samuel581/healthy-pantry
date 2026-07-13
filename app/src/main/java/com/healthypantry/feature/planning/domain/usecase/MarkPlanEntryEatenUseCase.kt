package com.healthypantry.feature.planning.domain.usecase

import androidx.room.withTransaction
import com.healthypantry.core.common.Result
import com.healthypantry.core.database.AppDatabase
import com.healthypantry.core.unit.UnitConversionError
import com.healthypantry.core.unit.UnitConverter
import com.healthypantry.feature.pantry.data.repo.StockBatchRepository
import com.healthypantry.feature.pantry.data.repo.UnitConversionRepository
import com.healthypantry.feature.planning.data.repo.PlanEntryRepository
import com.healthypantry.feature.planning.domain.model.PlanEntry
import com.healthypantry.feature.planning.domain.model.PlanEntryType
import com.healthypantry.feature.recipes.data.repo.RecipeRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

/**
 * Spec: "Mark-eaten decrements actual" (sdd/pantry-tracker/spec, "Projected vs Actual Stock").
 *
 * Takes an already-resolved [PlanEntry] — the caller (e.g. the future weekly-plan UI) already has
 * it from a list it is displaying — rather than looking it up by id, avoiding the need for a new
 * `PlanEntryRepository.getById`.
 *
 * For [PlanEntryType.ITEM] the decrement amount is [PlanEntry.quantity] directly: already
 * expressed in that FoodItem's own canonical unit (see [PlanEntry] KDoc — a deliberate v1
 * simplification, there is no separate unit field on the quick-add schema). For
 * [PlanEntryType.RECIPE], each ingredient is scaled by `entry.servings / recipe.servings` and
 * converted to its FoodItem's canonical unit before decrementing — the same scale+convert step
 * [ComputeMacroTotalsUseCase]/[ComputeWeeklyNeedsUseCase] use, shared via [convertScaledIngredient].
 *
 * Every recipe-ingredient conversion is resolved *before* any write happens: if any ingredient's
 * unit is unresolvable, the whole call returns [Result.Failure] with zero side effects (no
 * partial decrement, no eaten flag flipped). The resolved decrement(s) and the
 * [PlanEntryRepository.markEaten] flag-flip then run inside one [AppDatabase.withTransaction] —
 * `StockBatchRepository` and `PlanEntryRepository` are separate repositories backed by the same
 * [AppDatabase], so this use-case injects [AppDatabase] directly (mirroring
 * `RecipeRepositoryImpl.upsertRecipeWithIngredients`'s pattern) to guarantee the stock decrement
 * and the eaten flag can never diverge if either half fails.
 *
 * [execute] is idempotent, and the guarantee is enforced at the DB layer, not just in-memory: the
 * `entry.eaten` check below is a fast-path optimization only (skips re-resolving conversions when
 * the caller's own snapshot already knows the entry is eaten), NOT the correctness guard. Two
 * concurrent/rapid calls (UI double-tap, or a retried call after an ambiguous timeout) can both be
 * built from an equally-stale [PlanEntry] snapshot with `eaten == false` — the fast-path check
 * alone cannot see this. The actual guard is [PlanEntryRepository.markEaten]'s conditional
 * `UPDATE ... WHERE eaten = 0`, run *first* inside the transaction below: SQLite/Room serializes
 * writers, so only one of two concurrent calls for the same entry can ever affect a row. Stock is
 * only decremented when that guarded update reports it actually flipped the flag; a call that sees
 * 0 affected rows treats the entry as already eaten (by this call or a concurrent one) and returns
 * success with zero further side effects.
 */
class MarkPlanEntryEatenUseCase @Inject constructor(
    private val database: AppDatabase,
    private val stockBatchRepository: StockBatchRepository,
    private val recipeRepository: RecipeRepository,
    private val unitConversionRepository: UnitConversionRepository,
    private val planEntryRepository: PlanEntryRepository,
    private val unitConverter: UnitConverter,
) {

    suspend fun execute(entry: PlanEntry, eatenAt: Instant = Instant.now()): Result<Unit, UnitConversionError> {
        if (entry.eaten) {
            // Fast-path optimization only (see class KDoc) — NOT the correctness guard. Skips
            // re-resolving conversions when the caller's own snapshot already knows this entry is
            // eaten; the real guard is the guarded UPDATE inside the transaction below.
            return Result.success(Unit)
        }

        val decrements: List<Pair<Long, Double>> = when (entry.type) {
            PlanEntryType.ITEM -> {
                val foodItemId = requireNotNull(entry.foodItemId) { "ITEM plan entry must have a foodItemId" }
                val quantity = requireNotNull(entry.quantity) { "ITEM plan entry must have a quantity" }
                listOf(foodItemId to quantity)
            }

            PlanEntryType.RECIPE -> {
                val recipeId = requireNotNull(entry.recipeId) { "RECIPE plan entry must have a recipeId" }
                val requestedServings = requireNotNull(entry.servings) { "RECIPE plan entry must have servings" }
                val recipeWithIngredients = recipeRepository.observeRecipeWithIngredients(recipeId).first()

                if (recipeWithIngredients == null) {
                    // Recipe was deleted after being planned (should not happen in practice: its
                    // FK is RESTRICT while referenced by any plan entry) — nothing left to
                    // decrement; the entry is still marked eaten below.
                    emptyList()
                } else {
                    val resolved = mutableListOf<Pair<Long, Double>>()
                    for (detail in recipeWithIngredients.ingredients) {
                        val factors = unitConversionRepository.observeForFoodItem(detail.foodItem.id).first()
                        val converted = unitConverter.convertScaledIngredient(
                            detail,
                            requestedServings,
                            recipeWithIngredients.recipe.servings,
                            factors,
                        )
                        when (converted) {
                            is Result.Failure -> return converted
                            is Result.Success -> resolved += detail.foodItem.id to converted.value
                        }
                    }
                    resolved
                }
            }
        }

        database.withTransaction {
            val affectedRows = planEntryRepository.markEaten(entry.id, eatenAt)
            if (affectedRows == 0) {
                // The guarded UPDATE didn't transition anything: this entry was already marked
                // eaten by this call or a concurrent one racing ahead of us. Do NOT decrement stock
                // again — the DB, not our possibly-stale `entry` snapshot, is the source of truth.
                return@withTransaction
            }
            decrements.forEach { (foodItemId, amount) -> stockBatchRepository.decrementForFoodItem(foodItemId, amount) }
        }

        return Result.success(Unit)
    }
}
