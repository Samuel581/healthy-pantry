package com.healthypantry.feature.recipes.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.healthypantry.feature.recipes.data.entity.RecipeIngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeIngredientDao {

    @Insert
    suspend fun insert(ingredient: RecipeIngredientEntity): Long

    /** Inserts a full ordered ingredient list in one transaction-free batch call. */
    @Insert
    suspend fun insertAll(ingredients: List<RecipeIngredientEntity>): List<Long>

    @Update
    suspend fun update(ingredient: RecipeIngredientEntity)

    @Delete
    suspend fun delete(ingredient: RecipeIngredientEntity)

    /** Clears every ingredient line for a recipe — used by the repository's replace-on-edit flow. */
    @Query("DELETE FROM recipe_ingredient WHERE recipeId = :recipeId")
    suspend fun deleteAllForRecipe(recipeId: Long)

    @Query("SELECT * FROM recipe_ingredient WHERE recipeId = :recipeId ORDER BY sortOrder ASC")
    fun observeForRecipe(recipeId: Long): Flow<List<RecipeIngredientEntity>>
}
