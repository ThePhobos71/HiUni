package de.transio.hiuni.core.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniMotion

/**
 * Schmale, dezente Offline-Leiste im Design-Kit-Stil. Wird EINMAL global über der
 * NavHost-Ebene eingehängt (siehe AdaptiveScaffold), sodass alle Screens sie erben
 * statt sie pro Screen zu duplizieren.
 *
 * Sichtbarkeit steuert [visible] von außen (typischerweise `!isOnline`). Der
 * Übergang läuft über die zentralen Motion-Tokens ([HiUniMotion.contentSwitchMs]):
 * Ein sanftes Fade + Expand/Shrink der Höhe, damit die Leiste den darunterliegenden
 * Content nicht schlagartig verschiebt, sondern „hereinschiebt".
 */
@Composable
fun OfflineBanner(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(
            animationSpec = tween(HiUniMotion.contentSwitchMs),
        ) + fadeIn(animationSpec = tween(HiUniMotion.contentSwitchMs)),
        exit = shrinkVertically(
            animationSpec = tween(HiUniMotion.contentSwitchMs),
        ) + fadeOut(animationSpec = tween(HiUniMotion.contentSwitchMs)),
    ) {
        val semantics = HiUniColors.semantics
        Surface(
            color = semantics.amberSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = semantics.amber,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "Offline – gespeicherte Daten",
                    style = MaterialTheme.typography.labelMedium,
                    color = semantics.amber,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
