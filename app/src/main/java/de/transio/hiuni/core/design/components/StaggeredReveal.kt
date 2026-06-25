package de.transio.hiuni.core.design.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Sanfter Stagger-Reveal für Cards/Surfaces in einer Liste: jede Position
 * startet leicht nach unten verschoben + transparent und faded mit kleinem
 * Versatz pro `index` rein. Die Box reserviert ihren finalen Platz sofort,
 * sodass keine Layout-Sprünge entstehen — nur Alpha + translationY werden
 * über `graphicsLayer` animiert.
 *
 * Nutzung:
 * ```
 * LazyColumn {
 *     item { StaggeredReveal(index = 0) { CasLoginCard() } }
 *     item { StaggeredReveal(index = 1) { LsfStundenplanCard() } }
 *     ...
 * }
 * ```
 *
 * `delayPerItem = 35ms` ist auf 8–12 sichtbare Items abgestimmt — Total-
 * Reveal-Dauer bleibt unter ~700ms. Bei größeren Listen ggf. kürzer wählen
 * oder den Index ab einer Schwelle deckeln.
 */
@Composable
fun StaggeredReveal(
    index: Int,
    delayPerItem: Long = 35L,
    durationMs: Int = 280,
    content: @Composable () -> Unit
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (index > 0) delay(index * delayPerItem)
        revealed = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
        label = "stagger-alpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (revealed) 0.dp else 14.dp,
        animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
        label = "stagger-offset"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = offsetY.toPx()
        }
    ) {
        content()
    }
}
