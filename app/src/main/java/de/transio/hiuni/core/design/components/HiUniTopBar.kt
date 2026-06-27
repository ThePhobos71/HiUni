package de.transio.hiuni.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii

/**
 * Standardisierte Top-Bar — Back-Button + Titel (+ optional Subtitle + Trailing-Action),
 * konsistent über Profile/Exams/Notifications/Settings-Sub-Screens.
 *
 * - [roundedBottom] = true (Default): Surface mit unten abgerundeten Ecken
 *   ([HiUniRadii.big]), wie bei Profile/Exams/Notifications.
 * - [roundedBottom] = false: ohne Surface, nur Row mit colors.surface-Background,
 *   wie bei den Settings-Sub-Screens (Home/Nav/QuickAccess).
 *
 * [MaterialTheme.typography.titleLarge] bringt bereits FontWeight.Bold mit —
 * deshalb kein zusätzlicher Bold-Override.
 */
@Composable
fun HiUniTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    roundedBottom: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    val rowModifier = Modifier
        .fillMaxWidth()
        .let { if (roundedBottom) it else it.background(colors.surface) }
        .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 12.dp)

    val barContent: @Composable () -> Unit = {
        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (trailing != null) Arrangement.SpaceBetween
                                    else Arrangement.Start
        ) {
            // Linker Cluster: Back-Button + (Titel / Titel+Subtitle).
            // Eigene Row, damit Trailing-Action visuell als rechter Block
            // mit SpaceBetween-Layout vom Titel getrennt liegt.
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Zurück",
                        tint = colors.onSurface
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = semantics.onSurfaceMuted
                        )
                    }
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
    }

    if (roundedBottom) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(
                bottomStart = HiUniRadii.big,
                bottomEnd = HiUniRadii.big
            ),
            modifier = modifier.fillMaxWidth()
        ) {
            barContent()
        }
    } else {
        // Kein Surface — der Row-eigene background(colors.surface) reicht.
        // [modifier] wird hier nicht durchgereicht, weil rowModifier bereits
        // alle Layout-Pflichten übernimmt; falls Aufrufer eigene Modifier
        // anhängen wollen, ist roundedBottom=true der primäre Pfad.
        barContent()
    }
}
