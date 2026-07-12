package com.healthypantry.feature.planning.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.healthypantry.feature.pantry.data.entity.FoodItemEntity
import com.healthypantry.feature.planning.domain.model.MealSlot
import com.healthypantry.feature.planning.domain.model.PlanEntryType
import com.healthypantry.feature.recipes.data.entity.RecipeEntity

/**
 * Room persistence model for one day/meal-slot planning entry (spec "Weekly Plan Assignment and
 * Quick-Add"): either a [RecipeEntity] assignment ([type] == [PlanEntryType.RECIPE], [recipeId]
 * + [servings] set) or a single-item quick-add ([type] == [PlanEntryType.ITEM], [foodItemId] +
 * [quantity] set) — exactly one of the two pairs is populated per [type]. Mapped to/from
 * [com.healthypantry.feature.planning.domain.model.PlanEntry] by `feature/planning/data/repo`
 * mappers — never returned directly from a repository.
 *
 * `onDelete = RESTRICT` for both FKs: a [PlanEntryEntity] row with [eaten] == true is the user's
 * actual historical consumption record, not disposable plan state — it must not be silently
 * hard-deleted just because the recipe or item it refers to is later removed from the pantry/
 * recipe book. This mirrors [com.healthypantry.feature.recipes.data.entity.RecipeIngredientEntity
 * .foodItemId], which is explicitly `RESTRICT` for the same reason (a referenced row must not be
 * silently deleted out from under it). Deleting a [RecipeEntity]/[FoodItemEntity] that still has
 * any [PlanEntryEntity] referencing it — eaten or not — now throws a FK constraint violation
 * instead of cascading; callers must explicitly clear/reassign plan entries first.
 */
@Entity(
    tableName = "plan_entry",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = FoodItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodItemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("dateEpochDay"), Index("recipeId"), Index("foodItemId")],
)
data class PlanEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val mealSlot: MealSlot,
    val type: PlanEntryType,
    val recipeId: Long?,
    val foodItemId: Long?,
    val quantity: Double?,
    val servings: Double?,
    val eaten: Boolean,
    val eatenAt: Long?,
)
