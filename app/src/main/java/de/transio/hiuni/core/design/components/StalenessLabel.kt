package de.transio.hiuni.core.design.components

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniMotion

/**
 * Schwellwert (6h in Millisekunden), ab dem gecachte Daten als „alt" gelten und
 * das [StalenessLabel] auch online sichtbar wird. Öffentlich, damit Tests und
 * Aufrufer denselben Wert referenzieren statt eine Magic Number zu duplizieren.
 */
const val STALENESS_THRESHOLD_MS: Long = 6L * 60 * 60 * 1000

/**
 * Reine Sichtbarkeitslogik der Stale-Kennzeichnung — Android-frei und damit
 * unit-testbar. Das Label erscheint dezent unter der TopBar, aber NUR wenn:
 *
 *  - offline ist (dann sind die Daten per Definition „gespeichert/alt"), ODER
 *  - der letzte Refresh länger als [STALENESS_THRESHOLD_MS] her ist.
 *
 * Ein noch nie erfolgter Refresh ([lastRefreshEpoch] <= 0) liefert `false`:
 * Ohne Timestamp gibt es keine sinnvolle „Stand:"-Angabe, und der Erst-Load
 * zeigt ohnehin Skeleton/ErrorState.
 */
fun shouldShowStaleness(
    lastRefreshEpoch: Long,
    isOnline: Boolean,
    nowEpoch: Long,
    thresholdMs: Long = STALENESS_THRESHOLD_MS,
): Boolean {
    if (lastRefreshEpoch <= 0L) return false
    if (!isOnline) return true
    return (nowEpoch - lastRefreshEpoch) >= thresholdMs
}

/**
 * Dezentes „Stand: vor 2 Std."-Label für gecachte Daten. Der Aufrufer entscheidet
 * via [shouldShowStaleness], ob es überhaupt gerendert wird — dieser Composable
 * rendert unbedingt, sobald er im Baum steht (kein internes if, damit die
 * Ein-/Ausblend-Verantwortung beim Aufrufer bzw. dessen AnimatedVisibility liegt).
 *
 * Die relative Zeit kommt aus [DateUtils.getRelativeTimeSpanString] (lokalisiert,
 * „vor 2 Std." / „vor 5 Min."). Bei [lastRefreshEpoch] <= 0 fällt der Text auf
 * eine neutrale Variante zurück statt „vor 56 Jahren" (Epoch 0) anzuzeigen.
 */
@Composable
fun StalenessLabel(
    lastRefreshEpoch: Long,
    modifier: Modifier = Modifier,
    nowEpoch: Long = System.currentTimeMillis(),
) {
    val semantics = HiUniColors.semantics
    val relative = if (lastRefreshEpoch > 0L) {
        DateUtils.getRelativeTimeSpanString(
            lastRefreshEpoch,
            nowEpoch,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    } else {
        "unbekannt"
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = semantics.onSurfaceMuted,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = "Stand: $relative",
            style = MaterialTheme.typography.labelSmall,
            color = semantics.onSurfaceMuted,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Aufruf-fertiger Wrapper: kombiniert [shouldShowStaleness] mit einer sanften
 * Fade-Animation (Motion-Token [HiUniMotion.contentSwitchMs]) um das
 * [StalenessLabel]. Screens hängen genau diese eine Zeile dezent unter ihren
 * Header, statt Sichtbarkeits- und Formatlogik selbst zu verdrahten.
 */
@Composable
fun StalenessRow(
    lastRefreshEpoch: Long,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    nowEpoch: Long = System.currentTimeMillis(),
) {
    AnimatedVisibility(
        visible = shouldShowStaleness(lastRefreshEpoch, isOnline, nowEpoch),
        enter = fadeIn(animationSpec = tween(HiUniMotion.contentSwitchMs)),
        exit = fadeOut(animationSpec = tween(HiUniMotion.contentSwitchMs)),
    ) {
        StalenessLabel(
            lastRefreshEpoch = lastRefreshEpoch,
            modifier = modifier,
            nowEpoch = nowEpoch,
        )
    }
}
