package com.healthypantry.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Organic design system [androidx.compose.material3.ColorScheme]. The source design has no
 * dark-theme tokens at all, so this is the only palette — see [HealthyPantryTheme].
 */
private val OrganicLightColors = lightColorScheme(
    primary = AccentColor,
    onPrimary = BackgroundColor,
    primaryContainer = Accent200,
    onPrimaryContainer = Accent800,
    secondary = Accent2Color,
    onSecondary = BackgroundColor,
    secondaryContainer = Accent2_200,
    onSecondaryContainer = Accent2_800,
    tertiary = Accent2_700,
    onTertiary = BackgroundColor,
    tertiaryContainer = Accent2_300,
    onTertiaryContainer = Accent2_900,
    background = BackgroundColor,
    onBackground = OnBackgroundColor,
    surface = SurfaceColor,
    onSurface = OnSurfaceColor,
    surfaceVariant = Neutral200,
    onSurfaceVariant = Neutral700,
    outline = Neutral400,
    outlineVariant = Neutral300,
    // No error token in the source design — a standard, sane Material red family.
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Neutral900,
    inverseOnSurface = Neutral100,
    inversePrimary = Accent300,
    surfaceTint = AccentColor,
    scrim = Color(0xFF000000),
)

/**
 * App-wide Material3 theme wrapper for the Organic design system.
 *
 * The source design has no dark-theme tokens at all (colors above are the single, light-only
 * palette). [darkTheme] is kept as a parameter for API stability / future work, but is
 * intentionally NOT branched on yet — both [darkTheme] values currently render the same
 * light-derived [androidx.compose.material3.ColorScheme].
 */
@Composable
fun HealthyPantryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalHealthyPantryExtraColors provides LightHealthyPantryExtraColors) {
        MaterialTheme(
            colorScheme = OrganicLightColors,
            typography = HealthyPantryTypography,
            shapes = HealthyPantryShapes,
            content = content,
        )
    }
}
