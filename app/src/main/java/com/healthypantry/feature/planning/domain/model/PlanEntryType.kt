package com.healthypantry.feature.planning.domain.model

/**
 * Whether a [com.healthypantry.feature.planning.data.entity.PlanEntryEntity] / [PlanEntry]
 * references a full [com.healthypantry.feature.recipes.domain.model.Recipe] assignment or a
 * single pantry item quick-add (spec "Weekly Plan Assignment and Quick-Add").
 */
enum class PlanEntryType {
    RECIPE,
    ITEM,
}
