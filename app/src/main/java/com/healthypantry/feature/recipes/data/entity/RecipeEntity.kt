package com.healthypantry.feature.recipes.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Room persistence model for a recipe (spec "Recipe CRUD with Ingredients"). Mapped to/from
 * [com.healthypantry.feature.recipes.domain.model.Recipe] by `feature/recipes/data/repo`
 * mappers — never returned directly from a repository. The ordered ingredient list itself lives
 * in [RecipeIngredientEntity.sortOrder], not on this entity.
 */
@Entity(tableName = "recipe")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val servings: Int,
    val notes: String?,
    val createdAt: Instant,
)
