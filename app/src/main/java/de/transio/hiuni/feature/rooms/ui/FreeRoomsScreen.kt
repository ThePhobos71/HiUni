package de.transio.hiuni.feature.rooms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.common.DateTimeFormats
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.design.components.ErrorState
import de.transio.hiuni.feature.rooms.FreeRoomsUiState
import de.transio.hiuni.feature.rooms.FreeRoomsViewModel
import de.transio.hiuni.feature.rooms.data.BusyRoom
import de.transio.hiuni.feature.rooms.data.Campus
import de.transio.hiuni.feature.rooms.data.FreeRoom
import de.transio.hiuni.feature.rooms.data.FreeRoomCalculator
import de.transio.hiuni.feature.rooms.data.LsfRoom
import de.transio.hiuni.ui.responsive.FullWidthContent
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Raumfinder: Campus wählen, sofort die jetzt freien Seminarräume und Hörsäle sehen.
 * Aufbau wie der Bibliothek-Screen (Kopf mit Titel, Status-Pill, Untertitel und
 * Pill-Reihen; darunter Karten), alles handgestylt aus Surface und Text.
 */
@Composable
fun FreeRoomsScreen(viewModel: FreeRoomsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    FullWidthContent {
        Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
            FreeRoomsHeader(
                state = state,
                onSelectCampus = viewModel::selectCampus,
                onSelectTime = viewModel::selectTime,
                onReload = viewModel::reload
            )
            FreeRoomsBody(
                state = state,
                onReload = viewModel::reload,
                onRetryMissing = viewModel::retryMissing
            )
        }
    }
}

/* ───────────────────────────────────────────────────────────
 * Kopf: Titel · Frei-Pill · Datum und Stand · Campus · Uhrzeit
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun FreeRoomsHeader(
    state: FreeRoomsUiState,
    onSelectCampus: (Campus) -> Unit,
    onSelectTime: (Int?) -> Unit,
    onReload: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val snapshot = state.snapshot

    val subtitle = buildString {
        if (state.today != java.time.LocalDate.MIN) append(state.today.format(DateTimeFormats.dayFull))
        if (snapshot != null) {
            if (isNotEmpty()) append(" · ")
            append("Stand ").append(clockOf(snapshot.fetchedAt))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Freie Räume",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            if (snapshot != null) {
                val freeCount = state.result.free.size
                StatusPill(
                    text = "$freeCount FREI",
                    color = if (freeCount > 0) semantics.green else semantics.onSurfaceMuted,
                    background = if (freeCount > 0) semantics.greenSurface else semantics.surfaceAlt
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (state.campus != null && !state.isLoading) {
                ActionPill(text = "Neu laden", clickLabel = "Raumpläne neu laden", onClick = onReload)
            }
        }

        Spacer(Modifier.height(14.dp))
        PillRow(
            options = Campus.entries,
            isSelected = { it == state.campus },
            label = { it.label },
            onSelect = onSelectCampus
        )
        Spacer(Modifier.height(8.dp))
        PillRow(
            options = listOf<Int?>(null) + state.timeOptions,
            isSelected = { it == state.chosenMinute },
            label = { minute -> minute?.let(FreeRoomCalculator::clock) ?: "Jetzt" },
            onSelect = onSelectTime
        )
    }
}

/* ───────────────────────────────────────────────────────────
 * Inhalt: Hinweis · Laden · Fehlende · Frei · Belegt
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun FreeRoomsBody(
    state: FreeRoomsUiState,
    onReload: () -> Unit,
    onRetryMissing: () -> Unit
) {
    val semantics = HiUniColors.semantics
    val snapshot = state.snapshot
    val timeLabel = if (state.chosenMinute == null) "Jetzt" else "Um ${FreeRoomCalculator.clock(state.atMinute)}"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.campus == null) {
            item(key = "choose") {
                InfoCard(
                    title = "Campus wählen",
                    body = "Wähle oben einen Campus. Die Raumpläne werden dann einmal für heute " +
                        "aus dem LSF geladen und bis heute Abend gemerkt."
                )
            }
            return@LazyColumn
        }

        state.progress?.let { progress ->
            item(key = "progress") { ProgressCard(done = progress.done, total = progress.total) }
        }

        if (snapshot == null) {
            if (state.error != null && !state.isLoading) {
                item(key = "error") {
                    ErrorState(
                        modifier = Modifier.fillParentMaxHeight(0.7f),
                        iconSurface = semantics.redSurface,
                        iconAccent = semantics.red,
                        title = "Raumpläne nicht geladen",
                        body = state.error,
                        onRetry = onReload
                    )
                }
            }
            return@LazyColumn
        }

        state.error?.let { error ->
            item(key = "error-inline") {
                InfoCard(title = "Neu laden fehlgeschlagen", body = "$error Angezeigt wird der Stand von vorhin.", accent = semantics.red)
            }
        }

        item(key = "hint") { LockHint() }

        if (state.missing.isNotEmpty() && !state.isLoading) {
            item(key = "missing") { MissingCard(missing = state.missing, onRetry = onRetryMissing) }
        }

        item(key = "free-header") { SectionLabel("$timeLabel frei") }
        if (state.result.free.isEmpty()) {
            item(key = "free-empty") {
                Text(
                    text = "Gerade ist hier laut LSF kein Raum frei.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
        items(state.result.free, key = { "free-${it.room.rgid}" }) { FreeRoomCard(it) }

        if (state.result.busy.isNotEmpty()) {
            item(key = "busy-header") {
                Spacer(Modifier.height(6.dp))
                SectionLabel("Belegt")
            }
            items(state.result.busy, key = { "busy-${it.room.rgid}" }) { BusyRoomCard(it) }
        }
    }
}

@Composable
private fun FreeRoomCard(entry: FreeRoom) {
    val semantics = HiUniColors.semantics
    RoomCard(
        room = entry.room,
        status = FreeRoomCalculator.statusText(entry),
        statusColor = semantics.green,
        statusBackground = semantics.greenSurface,
        muted = false
    )
}

@Composable
private fun BusyRoomCard(entry: BusyRoom) {
    val semantics = HiUniColors.semantics
    RoomCard(
        room = entry.room,
        status = FreeRoomCalculator.statusText(entry),
        statusColor = semantics.onSurfaceMuted,
        statusBackground = semantics.surfaceAlt,
        muted = true
    )
}

@Composable
private fun RoomCard(
    room: LsfRoom,
    status: String,
    statusColor: Color,
    statusBackground: Color,
    muted: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val typeLabel = room.type.ifBlank { "Raum" }
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${room.code}, $typeLabel, $status"
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.code,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (muted) semantics.onSurfaceMuted else colors.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Surface(
                color = statusBackground,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.widthIn(max = 190.dp)
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** Dezenter Hinweis: LSF kennt Buchungen, nicht Schlösser. */
@Composable
private fun LockHint() {
    val semantics = HiUniColors.semantics
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HiUniRadii.tile))
            .background(semantics.surfaceAlt)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = semantics.onSurfaceMuted,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "Frei laut LSF heißt nicht, dass der Raum aufgeschlossen ist.",
            style = MaterialTheme.typography.bodySmall,
            color = semantics.onSurfaceMuted
        )
    }
}

@Composable
private fun ProgressCard(done: Int, total: Int) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val fraction = if (total > 0) done.toFloat() / total else 0f
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = if (total > 0) "Lade Raumpläne: $done von $total" else "Lade Raumliste",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            // Handgestylter Balken wie die Auslastung im Design-Mock (Bibliothek).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(semantics.surfaceAlt)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.primary)
                )
            }
            Text(
                text = "Einmal pro Tag, danach geht die Suche sofort und auch offline.",
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun MissingCard(missing: List<LsfRoom>, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val count = missing.size
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (count == 1) "1 Raum nicht geladen" else "$count Räume nicht geladen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.amber,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = missing.joinToString(", ") { it.code },
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            ActionPill(text = "Nachladen", clickLabel = "Fehlende Räume nachladen", onClick = onRetry)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, accent: Color? = null) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = accent ?: colors.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/* ───────────────────────────────────────────────────────────
 * Bausteine
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(Locale.GERMAN),
        style = MaterialTheme.typography.labelSmall,
        color = HiUniColors.semantics.onSurfaceMuted,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun StatusPill(text: String, color: Color, background: Color) {
    Surface(color = background, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Kleine Aktions-Pill im Stil der "Buchen"-Pill der Bibliothek. */
@Composable
private fun ActionPill(text: String, clickLabel: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.primaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = clickLabel, onClick = onClick)
            .semantics { role = Role.Button }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * Einzeilige, horizontal scrollbare Pill-Reihe wie die Filter im Design-Mock
 * (handgestylte Surface und Text statt M3-FilterChip).
 */
@Composable
private fun <T> PillRow(
    options: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        options.forEach { option ->
            val isActive = isSelected(option)
            Surface(
                color = if (isActive) colors.primary else semantics.surfaceAlt,
                shape = RoundedCornerShape(HiUniRadii.pill),
                modifier = Modifier
                    .clip(RoundedCornerShape(HiUniRadii.pill))
                    .clickable(onClick = { onSelect(option) })
                    .semantics { role = Role.Button }
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isActive) colors.onPrimary else semantics.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun clockOf(epochMillis: Long): String {
    val t = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(t.hour, t.minute)
}
