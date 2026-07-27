package com.healthypantry.app.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// --- Organic design system — base tokens -----------------------------------------------------

val AccentColor = Color(0xFFC67139)
val Accent2Color = Color(0xFF7A8A5E)
val BackgroundColor = Color(0xFFF5EAD8)
val SurfaceColor = Color(0xFFEBDDC5)
val OnBackgroundColor = Color(0xFF201E1D)
val OnSurfaceColor = OnBackgroundColor
val DividerColor = Color(0x29201E1D) // #201e1d @ 16% opacity

// --- Neutral ramp 100 -> 900 --------------------------------------------------------------

val Neutral100 = Color(0xFFF9F4ED)
val Neutral200 = Color(0xFFEEE7DB)
val Neutral300 = Color(0xFFDCD3C4)
val Neutral400 = Color(0xFFC0B6A5)
val Neutral500 = Color(0xFFA19786)
val Neutral600 = Color(0xFF82796A)
val Neutral700 = Color(0xFF645C50)
val Neutral800 = Color(0xFF474238)
val Neutral900 = Color(0xFF2E2B25)

// --- Accent ramp 100 -> 900 ---------------------------------------------------------------

val Accent100 = Color(0xFFFFF2EB)
val Accent200 = Color(0xFFFFE1D0)
val Accent300 = Color(0xFFFFC6A5)
val Accent400 = Color(0xFFF6A06B)
val Accent500 = Color(0xFFD67F48)
val Accent600 = Color(0xFFB2622D)
val Accent700 = Color(0xFF8C491A)
val Accent800 = Color(0xFF643312)
val Accent900 = Color(0xFF402310)

// --- Accent-2 ramp 100 -> 900 -------------------------------------------------------------

val Accent2_100 = Color(0xFFF0FAE1)
val Accent2_200 = Color(0xFFE1EECC)
val Accent2_300 = Color(0xFFCCDBB2)
val Accent2_400 = Color(0xFFAEBF92)
val Accent2_500 = Color(0xFF8FA073)
val Accent2_600 = Color(0xFF728157)
val Accent2_700 = Color(0xFF56633F)
val Accent2_800 = Color(0xFF3D472B)
val Accent2_900 = Color(0xFF272E1B)

/**
 * Extra brand colors that don't map onto Material3's [androidx.compose.material3.ColorScheme]
 * slots (e.g. tag/pill backgrounds, category chips). Exposed via [LocalHealthyPantryExtraColors]
 * so screens can reach the full Organic ramps without re-deriving them.
 *
 * Only a single (light) palette instance exists — the Organic design has no dark-theme tokens,
 * see [HealthyPantryTheme].
 */
data class HealthyPantryExtraColors(
    val accent100: Color,
    val accent200: Color,
    val accent300: Color,
    val accent400: Color,
    val accent500: Color,
    val accent600: Color,
    val accent700: Color,
    val accent800: Color,
    val accent900: Color,
    val accent2_100: Color,
    val accent2_200: Color,
    val accent2_300: Color,
    val accent2_400: Color,
    val accent2_500: Color,
    val accent2_600: Color,
    val accent2_700: Color,
    val accent2_800: Color,
    val accent2_900: Color,
    val neutral100: Color,
    val neutral200: Color,
    val neutral300: Color,
    val neutral400: Color,
    val neutral500: Color,
    val neutral600: Color,
    val neutral700: Color,
    val neutral800: Color,
    val neutral900: Color,
    val divider: Color,
)

val LightHealthyPantryExtraColors = HealthyPantryExtraColors(
    accent100 = Accent100,
    accent200 = Accent200,
    accent300 = Accent300,
    accent400 = Accent400,
    accent500 = Accent500,
    accent600 = Accent600,
    accent700 = Accent700,
    accent800 = Accent800,
    accent900 = Accent900,
    accent2_100 = Accent2_100,
    accent2_200 = Accent2_200,
    accent2_300 = Accent2_300,
    accent2_400 = Accent2_400,
    accent2_500 = Accent2_500,
    accent2_600 = Accent2_600,
    accent2_700 = Accent2_700,
    accent2_800 = Accent2_800,
    accent2_900 = Accent2_900,
    neutral100 = Neutral100,
    neutral200 = Neutral200,
    neutral300 = Neutral300,
    neutral400 = Neutral400,
    neutral500 = Neutral500,
    neutral600 = Neutral600,
    neutral700 = Neutral700,
    neutral800 = Neutral800,
    neutral900 = Neutral900,
    divider = DividerColor,
)

val LocalHealthyPantryExtraColors = staticCompositionLocalOf<HealthyPantryExtraColors> {
    error("No HealthyPantryExtraColors provided — wrap this content in HealthyPantryTheme.")
}
