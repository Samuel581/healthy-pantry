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
}
