package com.healthypantry.feature.planning.data.repo

import com.healthypantry.core.common.DispatcherProvider
import com.healthypantry.feature.planning.data.dao.PlanEntryDao
import com.healthypantry.feature.planning.domain.model.PlanEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

/**
 * Domain-facing CRUD access to [PlanEntry]s (spec "Weekly Plan Assignment and Quick-Add"). Wraps
 * [PlanEntryDao] and never exposes Room entity types to callers.
 */
interface PlanEntryRepository {
    fun observeWeek(start: Long, end: Long): Flow<List<PlanEntry>>

    /** Inserts a new entry ([PlanEntry.id] == 0) or updates an existing one; returns its id. */
    suspend fun upsert(entry: PlanEntry): Long
    suspend fun delete(entry: PlanEntry)

    /**
     * Marks the entry [id] eaten at [eatenAt] (defaults to now), **only if it is not already
     * eaten** (atomic guard, see [PlanEntryDao.markEaten]). Moves its quantity out of projected
     * deficit and into an actual-stock decrement (spec "Projected vs Actual Stock", scenario
     * "Mark-eaten decrements actual").
     *
     * @return the number of rows affected: 1 if this call flipped the entry to eaten, 0 if it was
     * already eaten. Callers (see
     * [com.healthypantry.feature.planning.domain.usecase.MarkPlanEntryEatenUseCase]) must only
     * decrement stock when this returns 1.
     */
    suspend fun markEaten(id: Long, eatenAt: Instant = Instant.now()): Int
}

class PlanEntryRepositoryImpl @Inject constructor(
    private val planEntryDao: PlanEntryDao,
    private val dispatcherProvider: DispatcherProvider,
) : PlanEntryRepository {

    override fun observeWeek(start: Long, end: Long): Flow<List<PlanEntry>> =
        planEntryDao.observeWeek(start, end).map { entities -> entities.map { it.toDomain() } }

    override suspend fun upsert(entry: PlanEntry): Long = withContext(dispatcherProvider.io) {
        if (entry.id == 0L) {
            planEntryDao.insert(entry.toEntity())
        } else {
            planEntryDao.update(entry.toEntity())
            entry.id
        }
    }

    override suspend fun delete(entry: PlanEntry) = withContext(dispatcherProvider.io) {
        planEntryDao.delete(entry.toEntity())
    }

    override suspend fun markEaten(id: Long, eatenAt: Instant): Int = withContext(dispatcherProvider.io) {
        planEntryDao.markEaten(id, eatenAt.toEpochMilli())
    }
}
