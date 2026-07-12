package com.healthypantry.feature.planning.domain.model

/**
 * Time-of-day bucket a [com.healthypantry.feature.planning.data.entity.PlanEntryEntity] /
 * [PlanEntry] is assigned to (spec "Weekly Plan Assignment and Quick-Add").
 */
enum class MealSlot {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK,
}
