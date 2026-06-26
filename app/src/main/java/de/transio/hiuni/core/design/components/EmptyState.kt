package de.transio.hiuni.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Einheitlicher Empty-State für „nichts zu zeigen"-Listen und Detail-Screens.
 *
 * Zwei Layout-Modi werden über die Parameter unterschieden:
 *
 * - **Hero** (`iconSurface != null`): zentrierte Column mit 72dp-Icon-Circle,
 *   Title, Body und optionaler Action. Wird für Top-Level-Empties genutzt
 *   (Klausuren leer, Mitteilungen leer, Aufgaben leer).
 * - **Card** (`containerColor != null`): in eine Surface-Card eingebettet, mit
 *   kleinem Icon ohne Circle. Für sekundäre Empties (Movies, Email, Kurse).
 *
 * Wenn weder `iconSurface` noch `containerColor` gesetzt sind, fällt das
 * Layout auf eine minimale, zentrierte Column ohne Wrapper zurück (z.B.
 * EmptyDetail in MovieDetailScreen).
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconAccent: Color? = null,
    iconSurface: Color? = null,
    title: String? = null,
    body: String? = null,
    secondaryBody: String? = null,
    containerColor: Color? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val resolvedAccent = iconAccent ?: colors.primary

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
                            color = semantics.onSurfaceMuted
                        )
                    }
                    if (secondaryBody != null) {
                        Text(
                            text = secondaryBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = semantics.onSurfaceMuted
                        )
                    }
                    action?.invoke()
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
            action?.let {
                Spacer(Modifier.height(14.dp))
                it()
            }
        }
    }
}
