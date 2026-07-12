package com.healthypantry.feature.recipes.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.data.entity.FoodItemEntity

/**
 * Room persistence model for one ingredient line of a [RecipeEntity] (spec "Recipe CRUD with
 * Ingredients"): a quantity+unit of a pantry [FoodItemEntity], placed at [sortOrder] within the
 * recipe's entered ingredient order.
 *
 * `onDelete = CASCADE` for [recipeId]: an ingredient line has no meaning once its recipe is gone.
 * `onDelete = RESTRICT` for [foodItemId]: a pantry item referenced by a recipe MUST NOT be
 * silently deleted out from under that recipe (per design.md "Room Schema").
 */
@Entity(
    tableName = "recipe_ingredient",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodItemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("recipeId"), Index("foodItemId")],
)
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val foodItemId: Long,
    val quantity: Double,
    val unit: MeasurementUnit,
    val sortOrder: Int,
)
