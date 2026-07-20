package com.healthypantry.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Organic design system corner radii: sm=8dp, md=16dp, lg=28dp. Cards use an even larger radius
 * (lg * 1.15 ≈ 32dp), exposed here as [Shapes.extraLarge] for screens that need the "card" look.
 */
val HealthyPantryShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
