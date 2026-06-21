package de.transio.hiuni.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColors = lightColorScheme(
    primary = IndigoPrimaryLight,
    onPrimary = SurfaceLight,
    primaryContainer = IndigoSurfaceTintLight,
    onPrimaryContainer = IndigoPrimaryLight,
    secondary = AmberAccentLight,
    onSecondary = SurfaceLight,
    secondaryContainer = AmberSurfaceTintLight,
    onSecondaryContainer = AmberAccentLight,
    tertiary = PurpleStatusLight,
    onTertiary = SurfaceLight,
    tertiaryContainer = PurpleSurfaceTintLight,
    onTertiaryContainer = PurpleStatusLight,
    error = RedStatusLight,
    onError = SurfaceLight,
    errorContainer = RedSurfaceTintLight,
    onErrorContainer = RedStatusLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceAltLight,
    onSurfaceVariant = OnSurfaceMutedLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight
)

private val DarkColors = darkColorScheme(
    primary = IndigoPrimaryDark,
    onPrimary = BackgroundDark,
    primaryContainer = IndigoSurfaceTintDark,
    onPrimaryContainer = IndigoPrimaryDark,
    secondary = AmberAccentDark,
    onSecondary = BackgroundDark,
    secondaryContainer = AmberSurfaceTintDark,
    onSecondaryContainer = AmberAccentDark,
    tertiary = PurpleStatusDark,
    onTertiary = BackgroundDark,
    tertiaryContainer = PurpleSurfaceTintDark,
    onTertiaryContainer = PurpleStatusDark,
    error = RedStatusDark,
    onError = BackgroundDark,
    errorContainer = RedSurfaceTintDark,
    onErrorContainer = RedStatusDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceAltDark,
    onSurfaceVariant = OnSurfaceMutedDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark
)

@Composable
fun HiUniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val semantics = if (darkTheme) DarkSemantics else LightSemantics

    CompositionLocalProvider(LocalHiUniSemantics provides semantics) {
        MaterialTheme(
            colorScheme = colors,
            typography = HiUniTypography,
            shapes = HiUniShapes,
            content = content
        )
    }
}
