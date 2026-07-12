package com.healthypantry.core.unit

/**
 * Bounded set of units the app understands, grouped by [UnitCategory].
 *
 * Deliberately closed (not user-extensible): conversion correctness depends on every unit
 * having a well-known category, and new units must be added here explicitly.
 */
enum class MeasurementUnit(val category: UnitCategory) {
    GRAM(UnitCategory.MASS),
    KILOGRAM(UnitCategory.MASS),
    MILLILITER(UnitCategory.VOLUME),
    LITER(UnitCategory.VOLUME),
    CUP(UnitCategory.VOLUME),
    TABLESPOON(UnitCategory.VOLUME),
    TEASPOON(UnitCategory.VOLUME),
    PIECE(UnitCategory.COUNT),
}

enum class UnitCategory {
    MASS,
    VOLUME,
    COUNT,
}
