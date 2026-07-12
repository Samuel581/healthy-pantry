package com.healthypantry.feature.pantry.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.healthypantry.feature.pantry.data.entity.FoodItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {

    @Insert
    suspend fun insert(item: FoodItemEntity): Long

    @Update
    suspend fun update(item: FoodItemEntity)

    @Delete
    suspend fun delete(item: FoodItemEntity)

    @Query("SELECT * FROM food_item WHERE id = :id")
    fun observeById(id: Long): Flow<FoodItemEntity?>

    @Query("SELECT * FROM food_item ORDER BY name ASC")
    fun observeAll(): Flow<List<FoodItemEntity>>
}
