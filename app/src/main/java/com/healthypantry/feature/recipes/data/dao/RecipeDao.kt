package com.healthypantry.feature.recipes.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.healthypantry.feature.recipes.data.entity.RecipeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Insert
    suspend fun insert(recipe: RecipeEntity): Long

    @Update
    suspend fun update(recipe: RecipeEntity)

    @Delete
    suspend fun delete(recipe: RecipeEntity)

    @Query("SELECT * FROM recipe WHERE id = :id")
    fun observeById(id: Long): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipe ORDER BY name ASC")
    fun observeAll(): Flow<List<RecipeEntity>>

    /**
     * [RecipeEntity] joined with its ordered ingredient rows, each paired with the pantry item
     * it references (spec "Recipe CRUD with Ingredients"). `null` if no recipe with [id] exists.
     */
    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id")
    fun observeRecipeWithIngredients(id: Long): Flow<RecipeWithIngredientsEntity?>
}
