package de.transio.hiuni.core.design.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniMotion
import de.transio.hiuni.core.design.HiUniRadii

/**
 * Dezente Pulse-/Shimmer-Skeleton-Bausteine fürs erste Laden ohne Cache.
 *
 * Die Platzhalter pulsieren zwischen zwei theme-abgeleiteten Alpha-Stufen der
 * `onSurfaceMuted`-Farbe — dadurch funktioniert das im Light- wie Dark-Mode ohne
 * hartkodierte Farbwerte. Die Puls-Dauer stammt aus einem Motion-Token
 * ([HiUniMotion.skeletonPulseMs]).
 *
 * Bausteine:
 *  - [SkeletonLine]   — einzelne Textzeile (Breite/Höhe frei).
 *  - [SkeletonCircle] — runder Platzhalter (Avatar/Icon).
 *  - [SkeletonCard]   — Surface-Card mit Zeilen, als Listen-Item-Platzhalter.
 *  - [HiUniSkeletonList] — fertige Liste aus [SkeletonCard]s, drop-in für den
 *    „isLoading && kein Cache"-Fall (siehe Muster-KDoc auf [ErrorState]).
 *
 * Mehrere Platzhalter können sich EINE Puls-Phase über [rememberSkeletonColor]
 * teilen (Farbe einmal holen und durchreichen), damit sie synchron atmen statt
 * gegeneinander zu flackern — [HiUniSkeletonList] macht genau das.
 */

/** Gemeinsame, theme-abgeleitete Puls-Farbe. Ein Aufruf pro Skeleton-Baum. */
@Composable
fun rememberSkeletonColor(): Color {
    val base = HiUniColors.semantics.onSurfaceMuted
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = HiUniMotion.skeletonPulseMs,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton-alpha"
    )
    return base.copy(alpha = alpha)
}

@Composable
fun SkeletonLine(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    fraction: Float = 1f,
    height: Dp = 14.dp,
    color: Color = rememberSkeletonColor(),
    shape: Shape = RoundedCornerShape(HiUniRadii.smallPill)
) {
    val widthMod = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth(fraction)
    Box(
        modifier = modifier
            .then(widthMod)
            .height(height)
            .clip(shape)
            .background(color)
    )
}

@Composable
fun SkeletonCircle(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    color: Color = rememberSkeletonColor()
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * Ein Listen-Item-Platzhalter im Card-Look des Design-Kits.
 *
 * `color` kann von aussen (z.B. aus [HiUniSkeletonList]) durchgereicht werden,
 * damit alle Cards einer Liste dieselbe Puls-Phase teilen und synchron atmen.
 * Ohne Angabe holt sich jede Card ihre eigene (dann läuft sie eigenständig).
 */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    lines: Int = 2,
    showCircle: Boolean = false,
    color: Color = rememberSkeletonColor()
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (showCircle) {
                SkeletonCircle(size = 44.dp, color = color)
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                SkeletonLine(fraction = 0.4f, height = 11.dp, color = color)
                SkeletonLine(fraction = 0.85f, height = 17.dp, color = color)
                repeat((lines - 1).coerceAtLeast(0)) { idx ->
                    SkeletonLine(
                        fraction = if (idx == (lines - 2)) 0.6f else 0.95f,
                        height = 13.dp,
                        color = color
                    )
                }
            }
        }
    }
}

/**
 * Drop-in-Platzhalter-Liste für den „erster Load, noch kein Cache"-Fall.
 * Rendert [count] [SkeletonCard]s im selben Padding/Spacing wie die späteren
 * echten Listen (18dp horizontal / 16dp vertical / 12dp Gap).
 */
@Composable
fun HiUniSkeletonList(
    modifier: Modifier = Modifier,
    count: Int = 5,
    showCircle: Boolean = false
) {
    // Puls-Farbe hier einmal ziehen, damit ALLE Cards derselben Liste synchron atmen.
    val pulse = rememberSkeletonColor()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false
    ) {
        items(count) {
            SkeletonCard(showCircle = showCircle, color = pulse)
        }
    }
}
