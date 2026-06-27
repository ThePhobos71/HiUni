package de.transio.hiuni.ui.responsive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Liefert die aktuelle WindowSizeClass an alle Screens via Composition Local —
 * Screens können damit eigene Layout-Verzweigungen machen (z.B. List-Detail-
 * Multi-Pane auf Expanded).
 */
val LocalWindowSizeClass = compositionLocalOf<WindowSizeClass?> { null }

/**
 * Steuert ob [AdaptiveContentBox] den Content auf [MaxContentWidth] cappt.
 * Default: `CAPPED` — typische Read-Screens werden auf 1100dp begrenzt.
 * Screens die echte Tablet-Optimierung brauchen (Calendar-Grid, Multi-Pane)
 * wrappen sich in [FullWidthContent], um den Cap aufzuheben.
 */
enum class ContentWidthMode { CAPPED, FULL }

val LocalContentWidthMode = compositionLocalOf { ContentWidthMode.CAPPED }

/**
 * Aktiviert für die umschlossenen Composables den `FULL`-Modus — der globale
 * AdaptiveContentBox lässt den Content dann über die volle Breite gehen.
 * Sinnvoll bei Stundenraster-Grids, Multi-Pane-Layouts, Karten-Composites.
 */
@Composable
fun FullWidthContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentWidthMode provides ContentWidthMode.FULL) {
        content()
    }
}

/**
 * Limitiert den Inhalt auf eine lesbare Maximalbreite und zentriert ihn
 * horizontal — solange der umliegende Scope nicht via [FullWidthContent]
 * opt-out gewählt hat. Auf Phones (~360–480dp) ist der Cap nie wirksam,
 * erst auf Tablet/Foldable greift er.
 *
 * Hintergrund bleibt absichtlich voll-breit (Box füllt MaxSize), nur der
 * Content-Slot ist begrenzt — konsistent mit Material-3-Patterns.
 */
@Composable
fun AdaptiveContentBox(content: @Composable () -> Unit) {
    val mode = LocalContentWidthMode.current
    if (mode == ContentWidthMode.FULL) {
        content()
        return
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(modifier = Modifier.widthIn(max = MaxContentWidth)) {
            content()
        }
    }
}

/**
 * 1100dp gibt Image-reichen Listen (Movies, Mensa) und Card-Grids genug
 * Platz, hält aber Read-Heavy-Text (Settings, Mail-Body) komfortabel lesbar.
 * Material-3-Spec sieht 840–1200dp je nach Content-Typ vor — wir nehmen
 * einen guten Allgemeinwert.
 */
private val MaxContentWidth = 1100.dp
