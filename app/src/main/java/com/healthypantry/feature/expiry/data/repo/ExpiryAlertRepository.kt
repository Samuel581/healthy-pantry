package com.healthypantry.feature.expiry.data.repo

import com.healthypantry.feature.expiry.domain.model.ExpiringBatch
import com.healthypantry.feature.expiry.domain.model.ExpiryStatus
import com.healthypantry.feature.pantry.data.dao.FoodItemDao
import com.healthypantry.feature.pantry.data.dao.StockBatchDao
import com.healthypantry.feature.pantry.data.repo.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Domain-facing read of which [com.healthypantry.feature.pantry.domain.model.StockBatch]es are
 * expiring soon or already expired (spec "Expiry Notification Scheduling"). Backs both
 * [com.healthypantry.feature.expiry.worker.ExpiryCheckWorker] (system notification) and
 * [com.healthypantry.feature.expiry.ui.vm.ExpiryAlertViewModel] (in-app banner/badge fallback,
 * spec "Notification-Denied Fallback") off the same reactive source, so both surfaces always
 * agree on which items qualify.
 */
interface ExpiryAlertRepository {

    /**
     * Reactively emits every batch with a non-null expiry date that is due within
     * [lookaheadDays] days from now, or already past due, joined with its owning food item.
     * Re-emits whenever the underlying `stock_batch`/`food_item` tables change.
     */
    fun observeExpiringSoon(lookaheadDays: Long = DEFAULT_LOOKAHEAD_DAYS): Flow<List<ExpiringBatch>>

    companion object {
        /** Default lookahead window from the spec's example scenario. */
        const val DEFAULT_LOOKAHEAD_DAYS = 3L
    }
}

class ExpiryAlertRepositoryImpl @Inject constructor(
    private val stockBatchDao: StockBatchDao,
    private val foodItemDao: FoodItemDao,
    private val clock: Clock,
) : ExpiryAlertRepository {

    override fun observeExpiringSoon(lookaheadDays: Long): Flow<List<ExpiringBatch>> =
        combine(
            stockBatchDao.observeBatchesWithExpiry(),
            foodItemDao.observeAll(),
        ) { batchEntities, foodItemEntities ->
            val foodItemsById = foodItemEntities.associateBy { it.id }
            val today = LocalDate.now(clock)
            val threshold = today.plusDays(lookaheadDays)

            batchEntities.mapNotNull { batchEntity ->
                val expiryDate = batchEntity.expiryDate ?: return@mapNotNull null
                // Outside the lookahead window (spec scenario "Item outside the lookahead
                // window is not flagged") — an expiry date in the past is always <= threshold,
                // so already-expired batches fall through to the EXPIRED branch below instead
                // of being excluded here.
                if (expiryDate.isAfter(threshold)) return@mapNotNull null

                val foodItemEntity = foodItemsById[batchEntity.foodItemId] ?: return@mapNotNull null
                val status = if (expiryDate.isBefore(today)) ExpiryStatus.EXPIRED else ExpiryStatus.EXPIRING_SOON

                ExpiringBatch(
                    foodItem = foodItemEntity.toDomain(),
                    stockBatch = batchEntity.toDomain(),
                    status = status,
                )
            }
        }
}
