package de.transio.hiuni.ui.responsive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Limitiert den Inhalt auf eine lesbare Maximalbreite und zentriert ihn
 * horizontal. Auf Phones (~360–480dp) ist der Cap nie wirksam — der Wrapper
 * ist ein No-Op. Erst auf Tablet/Foldable in Landscape (≥ 900dp) greift er
 * und verhindert dass Listen-Items und Karten über die ganze Breite ziehen.
 *
 * Hintergrund bleibt absichtlich voll-breit (Box füllt MaxSize), nur der
 * Content-Slot ist begrenzt — das ist konsistent mit Material-3-Patterns
 * (NavRail + zentrierter Content über voller Surface).
 */
@Composable
fun AdaptiveContentBox(content: @Composable () -> Unit) {
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
 * 840dp ist Material-3-Standard für „one-pane content readability". Bei
 * mehr Breite würden Zeilen unangenehm lang werden.
 */
private val MaxContentWidth = 840.dp
