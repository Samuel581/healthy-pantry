package com.healthypantry.feature.pantry.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.healthypantry.feature.pantry.data.entity.UnitConversionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnitConversionDao {

    @Insert
    suspend fun insert(conversion: UnitConversionEntity): Long

    @Delete
    suspend fun delete(conversion: UnitConversionEntity)

    @Query("SELECT * FROM unit_conversion WHERE foodItemId = :foodItemId")
    fun observeForFoodItem(foodItemId: Long): Flow<List<UnitConversionEntity>>

    /**
     * One-shot (non-[Flow]) read of every conversion row for [foodItemId], used by
     * `UnitConversionRepository.delete` to find the persisted row matching a caller-supplied
     * [com.healthypantry.core.unit.ConversionFactor] (which carries no id of its own) without
     * collecting a [Flow] inside a suspend function — same pattern as [StockBatchDao.getForFoodItem].
     */
    @Query("SELECT * FROM unit_conversion WHERE foodItemId = :foodItemId")
    suspend fun getForFoodItem(foodItemId: Long): List<UnitConversionEntity>
}
