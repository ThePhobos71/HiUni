package de.transio.hiuni.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii

/**
 * Einheitlicher Error-State für „Laden fehlgeschlagen und kein Cache da"-Fälle.
 * Formales Gegenstück zu [EmptyState]: gleiche drei Layout-Modi, gleiche
 * Parameter-API, plus ein prominenter, hand-gestylter „Erneut versuchen"-Button.
 *
 * Layout-Modi (analog [EmptyState]):
 *  - **Hero** (`iconSurface != null`): zentrierte Column mit 72dp-Icon-Circle.
 *    Für Top-Level-Fehler die den ganzen Screen einnehmen (Mensa/Movies leerer
 *    Cache + Netzfehler).
 *  - **Card** (`containerColor != null`): in eine Surface-Card eingebettet.
 *    Für sekundäre Fehler innerhalb einer sonst gefüllten Liste.
 *  - **Minimal** (weder `iconSurface` noch `containerColor`): zentrierte Column
 *    ohne Wrapper (z.B. Detail-Panes).
 *
 * Default-Icon ist [Icons.Outlined.CloudOff] (Netz weg), default-Accent die
 * semantische Rot-Farbe — beide über Parameter überschreibbar.
 *
 * ── VERDRAHTUNGS-MUSTER (Welle-2-Agents: so auf weitere Screens ausrollen) ──
 *
 * Voraussetzung im UiState: ein `isLoading`-Flag (erster Load, noch kein Cache)
 * getrennt von `isRefreshing` (Pull-to-Refresh über vorhandenem Cache) sowie ein
 * `errorMessage: String?`. Im ViewModel beim ersten `refresh(force=false)`
 * `isLoading=true` setzen und nach der ersten Content-Emission bzw. am Ende des
 * Refreshs zurücksetzen; `errorMessage` NUR bei einem Fehler füllen.
 *
 * Im Screen dann drei Fälle nach Priorität unterscheiden:
 *
 * ```
 * val hasContent = state.items.isNotEmpty()
 * when {
 *     // 1. Fehler UND kein Cache → ErrorState mit Retry (kein Blank, keine Snackbar)
 *     state.errorMessage != null && !hasContent -> ErrorState(
 *         iconSurface = semantics.redSurface,
 *         iconAccent = semantics.red,
 *         title = "Verbindung fehlgeschlagen",
 *         body = state.errorMessage,
 *         onRetry = viewModel::refresh
 *     )
 *     // 2. Erster Load UND kein Cache → Skeleton statt leerem Screen
 *     state.isLoading && !hasContent -> HiUniSkeletonList()
 *     // 3. sonst normaler Content
 *     else -> Content(...)
 * }
 * ```
 *
 * Fall „Fehler ABER Cache vorhanden" (`errorMessage != null && hasContent`)
 * gehört NICHT hierher: dort bleiben die Stale-Daten sichtbar und der Fehler
 * kommt als dezente Snackbar (bestehende `LaunchedEffect(errorMessage)`-Logik).
 * Damit die Snackbar nicht doppelt zum ErrorState feuert, in dem Effect auf
 * `hasContent` prüfen (siehe MensaScreen/MoviesScreen).
 */
@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Erneut versuchen",
    icon: ImageVector? = Icons.Outlined.CloudOff,
    iconAccent: Color? = null,
    iconSurface: Color? = null,
    title: String? = "Verbindung fehlgeschlagen",
    body: String? = null,
    secondaryBody: String? = null,
    containerColor: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val resolvedAccent = iconAccent ?: semantics.red

    val retryButton: (@Composable () -> Unit)? = onRetry?.let {
        { RetryButton(label = retryLabel, onClick = it) }
    }

    if (containerColor != null) {
        // Card-Layout
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(HiUniRadii.card),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = resolvedAccent
                        )
                    }
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface
                        )
                    }
                    if (body != null) {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = semantics.onSurfaceMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (secondaryBody != null) {
                        Text(
                            text = secondaryBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = semantics.onSurfaceMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                    retryButton?.invoke()
                }
            }
        }
    } else {
        // Hero / Minimal Layout
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                if (iconSurface != null) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(iconSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = resolvedAccent,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = resolvedAccent
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                if (body != null || secondaryBody != null) Spacer(Modifier.height(6.dp))
            }
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted,
                    textAlign = TextAlign.Center
                )
            }
            if (secondaryBody != null) {
                if (body != null) Spacer(Modifier.height(10.dp))
                Text(
                    text = secondaryBody,
                    style = MaterialTheme.typography.labelMedium,
                    color = semantics.onSurfaceMuted,
                    textAlign = TextAlign.Center
                )
            }
            retryButton?.let {
                Spacer(Modifier.height(18.dp))
                it()
            }
        }
    }
}

/**
 * Hand-gestylter Primär-Button (Surface + clickable statt Stock-M3-Button),
 * damit er zum Design-Kit passt. Icon + Label in der Primärfarbe.
 */
@Composable
private fun RetryButton(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.primary,
        shape = RoundedCornerShape(HiUniRadii.pill),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
