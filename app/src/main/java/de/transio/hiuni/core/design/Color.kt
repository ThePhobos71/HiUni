package de.transio.hiuni.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Approximated sRGB conversions of the OKLCH design tokens from the design handoff.
 * Hue 265 = indigo-violet primary, hue 72 = amber accent, plus semantic status colors.
 */

// === Indigo Primary (hue 265) ===
internal val IndigoPrimaryLight = Color(0xFF3D3FBF)
internal val IndigoPrimaryDark = Color(0xFF9595FF)
internal val IndigoSurfaceTintLight = Color(0xFFE6E5F8)
internal val IndigoSurfaceTintDark = Color(0xFF26264F)

// === Amber Accent (hue 72) ===
internal val AmberAccentLight = Color(0xFFB47817)
internal val AmberAccentDark = Color(0xFFE4B056)
internal val AmberSurfaceTintLight = Color(0xFFF8EAD0)
internal val AmberSurfaceTintDark = Color(0xFF2F2818)

// === Green Status (hue 145) ===
internal val GreenStatusLight = Color(0xFF188A3B)
internal val GreenStatusDark = Color(0xFF4FCE7D)
internal val GreenSurfaceTintLight = Color(0xFFD8F2DD)
internal val GreenSurfaceTintDark = Color(0xFF112B19)

// === Red Status (hue 25) ===
internal val RedStatusLight = Color(0xFFC2342C)
internal val RedStatusDark = Color(0xFFF7766B)
internal val RedSurfaceTintLight = Color(0xFFFAD9D4)
internal val RedSurfaceTintDark = Color(0xFF2E1715)

// === Purple Status (hue 300) ===
internal val PurpleStatusLight = Color(0xFF8C2EBA)
internal val PurpleStatusDark = Color(0xFFD78BF1)
internal val PurpleSurfaceTintLight = Color(0xFFF3DCFA)
internal val PurpleSurfaceTintDark = Color(0xFF291532)

// === Neutrals ===
internal val BackgroundLight = Color(0xFFF1F4F8)
internal val BackgroundDark = Color(0xFF13141B)
internal val SurfaceLight = Color(0xFFFFFFFF)
internal val SurfaceDark = Color(0xFF1E1F28)
internal val SurfaceAltLight = Color(0xFFF6F8FB)
internal val SurfaceAltDark = Color(0xFF272832)
internal val OnSurfaceLight = Color(0xFF16161B)
internal val OnSurfaceDark = Color(0xFFEAEAEE)
internal val OnSurfaceMutedLight = Color(0xFF74757B)
internal val OnSurfaceMutedDark = Color(0xFF9899A2)
internal val OutlineLight = Color(0xFFDFE1E7)
internal val OutlineDark = Color(0xFF383A45)

/**
 * Semantic extension palette so screens can express "amber pill background" etc.
 * without bypassing the theme.
 */
data class HiUniSemanticColors(
    val amber: Color,
    val amberSurface: Color,
    val onAmber: Color,
    val green: Color,
    val greenSurface: Color,
    val onGreen: Color,
    val red: Color,
    val redSurface: Color,
    val onRed: Color,
    val purple: Color,
    val purpleSurface: Color,
    val onPurple: Color,
    val surfaceAlt: Color,
    val onSurfaceMuted: Color
)

internal val LightSemantics = HiUniSemanticColors(
    amber = AmberAccentLight,
    amberSurface = AmberSurfaceTintLight,
    onAmber = SurfaceLight,
    green = GreenStatusLight,
    greenSurface = GreenSurfaceTintLight,
    onGreen = SurfaceLight,
    red = RedStatusLight,
    redSurface = RedSurfaceTintLight,
    onRed = SurfaceLight,
    purple = PurpleStatusLight,
    purpleSurface = PurpleSurfaceTintLight,
    onPurple = SurfaceLight,
    surfaceAlt = SurfaceAltLight,
    onSurfaceMuted = OnSurfaceMutedLight
)

internal val DarkSemantics = HiUniSemanticColors(
    amber = AmberAccentDark,
    amberSurface = AmberSurfaceTintDark,
    onAmber = BackgroundDark,
    green = GreenStatusDark,
    greenSurface = GreenSurfaceTintDark,
    onGreen = BackgroundDark,
    red = RedStatusDark,
    redSurface = RedSurfaceTintDark,
    onRed = BackgroundDark,
    purple = PurpleStatusDark,
    purpleSurface = PurpleSurfaceTintDark,
    onPurple = BackgroundDark,
    surfaceAlt = SurfaceAltDark,
    onSurfaceMuted = OnSurfaceMutedDark
)

val LocalHiUniSemantics = compositionLocalOf<HiUniSemanticColors> {
    error("No HiUniSemanticColors provided")
}

object HiUniColors {
    val semantics: HiUniSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalHiUniSemantics.current
}
