package de.transio.hiuni.feature.sport.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.common.DateTimeFormats
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.sport.SportDetailViewModel
import de.transio.hiuni.feature.sport.data.SportEventEntity
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private val ZONE = ZoneId.of("Europe/Berlin")
private const val BOOKING_URL = "https://www.supersaas.de/schedule/HSP_Uni_Hildesheim/Hochschulsport"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportDetailScreen(
    onBack: () -> Unit,
    viewModel: SportDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.pinMessage) {
        val msg = state.pinMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeMessage()
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            DetailTopBar(
                title = state.event?.title?.let(::titleCase) ?: "Hochschulsport",
                isCancelled = state.event?.isCancelled == true,
                onBack = onBack
            )
            val event = state.event
            if (event == null) {
                EmptyDetail(onBack = onBack)
                return@Column
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Hero(event = event)
                Spacer(Modifier.height(14.dp))
                CapacityCard(event = event)
                Spacer(Modifier.height(14.dp))
                DescriptionCard(event = event)
                Spacer(Modifier.height(18.dp))
                ActionRow(
                    event = event,
                    isPinned = state.isCalendarPinned,
                    onTogglePin = {
                        if (state.isCalendarPinned) viewModel.unpinFromCalendar()
                        else viewModel.pinToCalendar()
                    }
                )
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

/* ───────────────────────────────────────────────────────────
 * Top-Bar
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun DetailTopBar(title: String, isCancelled: Boolean, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
            .padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Zurück",
                tint = colors.onSurface
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = colors.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (isCancelled) {
            StatusPill(text = "ABGESAGT", color = semantics.red, background = semantics.redSurface)
        }
    }
}

/* ───────────────────────────────────────────────────────────
 * Hero — Datum, Zeit, Ort
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun Hero(event: SportEventEntity) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    val zoned = event.startTime.atZone(ZONE)
    val dayLabel = zoned.format(DateTimeFormats.dayFull).replaceFirstChar { it.uppercase(Locale.GERMAN) }
    val timeLabel = "${zoned.toLocalTime().format(DateTimeFormats.time24)} – " +
        "${event.endTime.atZone(ZONE).toLocalTime().format(DateTimeFormats.time24)} Uhr"

    Surface(
        color = colors.primaryContainer,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = titleCase(event.title.ifBlank { "Hochschulsport" }),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onPrimaryContainer
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onPrimaryContainer
            )
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onPrimaryContainer.copy(alpha = 0.85f)
            )
            if (!event.location.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                LocationPill(text = "Ort: ${event.location}", tint = semantics.green, background = semantics.greenSurface)
            }
        }
    }
}

/* ───────────────────────────────────────────────────────────
 * Kapazitäts-Karte
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun CapacityCard(event: SportEventEntity) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    if (event.isCancelled) {
        Surface(
            color = semantics.redSurface,
            shape = RoundedCornerShape(HiUniRadii.card),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.EventBusy,
                    contentDescription = null,
                    tint = semantics.red
                )
                Column {
                    Text(
                        text = "FÄLLT AUS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = semantics.red
                    )
                    Text(
                        text = "Dieser Termin wurde abgesagt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.red.copy(alpha = 0.85f)
                    )
                }
            }
        }
        return
    }

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = "KAPAZITÄT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = semantics.onSurfaceMuted
            )
            Spacer(Modifier.height(10.dp))
            if (event.capacity <= 0) {
                Text(
                    text = "Frei buchbar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = semantics.green
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Kein Platz-Limit hinterlegt — einfach vorbeikommen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
                return@Column
            }
            val occupied = event.currentBookings.coerceIn(0, event.capacity)
            val ratio = occupied.toFloat() / event.capacity.toFloat()
            val (barColor, bgColor) = when {
                ratio >= 1f -> semantics.red to semantics.redSurface
                ratio >= 0.7f -> semantics.amber to semantics.amberSurface
                else -> semantics.green to semantics.greenSurface
            }
            LinearProgressIndicator(
                progress = { ratio.coerceIn(0f, 1f) },
                color = barColor,
                trackColor = bgColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
            Spacer(Modifier.height(10.dp))
            val free = event.freeSpots ?: 0
            val label = when {
                free <= 0 && event.waitlistCount > 0 ->
                    "Voll — ${event.waitlistCount} auf Warteliste"
                free <= 0 -> "Ausgebucht — ${event.capacity} Plätze belegt"
                else -> "$free von ${event.capacity} Plätzen frei"
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            if (event.isPaidOnly) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Nur mit Hochschulsport-Ticket buchbar",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

/* ───────────────────────────────────────────────────────────
 * Beschreibung
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun DescriptionCard(event: SportEventEntity) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = "BESCHREIBUNG",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = semantics.onSurfaceMuted
            )
            Spacer(Modifier.height(10.dp))
            val text = cleanedDescription(event)
            if (text.isNullOrBlank()) {
                Text(
                    text = "Keine weitere Beschreibung im Plan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
            }
        }
    }
}

/* ───────────────────────────────────────────────────────────
 * Action-Row
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun ActionRow(
    event: SportEventEntity,
    isPinned: Boolean,
    onTogglePin: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val isPast = event.endTime.isBefore(Instant.now())
    val isDisabled = isPast || event.isCancelled

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilledTonalButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onTogglePin()
            },
            enabled = !isDisabled,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = if (isPinned) "Aus Kalender entfernen" else "In Kalender",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
        OutlinedButton(
            onClick = {
                // Day-Deep-Link: supersaas respektiert `?d=YYYY-MM-DD` für die
                // initiale Kalender-Position. Datum wird in lokaler Zeitzone
                // (Europe/Berlin) gebildet, weil die supersaas-Sicht
                // dieselbe Zone benutzt.
                val day = event.startTime.atZone(ZONE).toLocalDate()
                val url = "$BOOKING_URL?d=$day"
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            },
            enabled = !isDisabled,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Im Browser buchen",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}

/* ───────────────────────────────────────────────────────────
 * Helpers
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun LocationPill(text: String, tint: Color, background: Color) {
    Surface(
        color = background,
        shape = RoundedCornerShape(HiUniRadii.smallPill)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun StatusPill(text: String, color: Color, background: Color) {
    Surface(color = background, shape = RoundedCornerShape(HiUniRadii.smallPill)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyDetail(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.SportsBasketball,
            contentDescription = null,
            tint = semantics.onSurfaceMuted
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Termin nicht (mehr) im aktuellen Plan-Fenster.",
            style = MaterialTheme.typography.bodyMedium,
            color = semantics.onSurfaceMuted
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text("Zurück", color = colors.primary) }
    }
}

/**
 * Beschreibung normalisieren: `\r\n` → `\n`, führende "Ort: …"-Zeile rauswerfen
 * weil sie im Hero schon steht, Whitespace trimmen.
 *
 * Wenn nach dem Stripping nichts mehr übrig bleibt (Event hat als Description
 * NUR den Ort), fallen wir auf den unbeschnittenen Original-Text zurück —
 * besser doppelter Ort als leerer Beschreibungs-Card. Wirklich leer → null.
 */
private fun cleanedDescription(event: SportEventEntity): String? {
    val raw = event.description
        ?.replace("\r\n", "\n")
        ?.replace("\r", "\n")
        ?.trim()
        ?: return null
    if (raw.isBlank()) return null

    val lines = raw.split('\n').toMutableList()
    if (lines.isNotEmpty() && lines[0].trim().startsWith("Ort:", ignoreCase = true)) {
        lines.removeAt(0)
    }
    val stripped = lines.joinToString("\n").trim()
    return stripped.ifBlank { raw }
}

/**
 * Schreiende Titel ("YOGA FÜR ANFÄNGER") in Title-Case wandeln — außer wenn ein
 * "FÄLLT AUS"-Marker drin steht. Dann lassen wir das Geschrei stehen, weil das
 * Signal-Charakter hat.
 */
private fun titleCase(input: String): String {
    if (input.contains("FÄLLT AUS", ignoreCase = false)) return input
    val upperRatio = input.count { it.isUpperCase() }.toDouble() /
        input.count { it.isLetter() }.coerceAtLeast(1).toDouble()
    if (upperRatio < 0.7) return input
    return input.lowercase(Locale.GERMAN).split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { c -> c.uppercase(Locale.GERMAN) }
    }
}
