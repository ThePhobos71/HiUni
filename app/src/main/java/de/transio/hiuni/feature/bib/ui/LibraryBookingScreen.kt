package de.transio.hiuni.feature.bib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.common.DateTimeFormats
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.bib.BookingScreenState
import de.transio.hiuni.feature.bib.data.BibConfig
import de.transio.hiuni.feature.bib.data.BibSnapshot
import de.transio.hiuni.feature.bib.data.MyBooking
import de.transio.hiuni.feature.bib.data.SlotStatus
import java.time.LocalDate
import java.time.LocalTime

private const val OPEN_HOUR = 8
private const val CLOSE_HOUR = 20
private val SLOT_COUNT = (CLOSE_HOUR - OPEN_HOUR) * 2 // 24 Slots à 30 min

/** Index 0 = 8:00, 1 = 8:30, … 24 = 20:00. */
internal fun slotIdxToStartHHMM(idx: Int): Pair<Int, Int> {
    val hour = OPEN_HOUR + idx / 2
    val minute = (idx % 2) * 30
    return hour to minute
}

private fun slotIdxToTime(idx: Int): LocalTime {
    val (h, m) = slotIdxToStartHHMM(idx)
    return LocalTime.of(h, m)
}

@Composable
fun LibraryBookingScreen(
    booking: BookingScreenState,
    snapshot: BibSnapshot?,
    loading: Boolean,
    onBack: () -> Unit,
    onToggleSlot: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDone: () -> Unit
) {
    val day = snapshot?.forRoomDay(booking.date, booking.roomId)
    val meta = BibConfig.ROOM_META[booking.roomId]
    val now = LocalTime.now()
    val isToday = booking.date == LocalDate.now()
    val myBookingsForRoom = snapshot?.myBookings
        ?.filter { it.roomId == booking.roomId }
        .orEmpty()

    // Slot-Status pro Index aus den Backend-Daten.
    val slotsByTime = day?.slots?.associateBy { it.startTime }.orEmpty()
    fun statusAt(idx: Int): SlotStatus {
        val t = slotIdxToTime(idx)
        return slotsByTime[t]?.status ?: SlotStatus.CLOSED
    }
    fun isPast(idx: Int): Boolean = isToday && slotIdxToTime(idx) <= now
    fun isBlocked(idx: Int): Boolean {
        val s = statusAt(idx)
        return isPast(idx) || s == SlotStatus.BOOKED || s == SlotStatus.OWN_BOOKING || s == SlotStatus.CLOSED
    }

    if (booking.confirmed) {
        ConfirmationView(
            booking = booking,
            meta = meta,
            onDone = onDone
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BookingHeader(
            title = "Gruppenraum ${meta?.label ?: "F${booking.roomId}"}",
            subtitle = buildString {
                append(booking.date.format(DateTimeFormats.dayShort))
                meta?.let { append(" · ${it.capacityMin}–${it.capacityMax} Plätze") }
            },
            onBack = onBack
        )

        Box(modifier = Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 18.dp, end = 18.dp, top = 18.dp,
                    bottom = 130.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (loading && snapshot == null) {
                    item(span = { GridItemSpan(3) }, key = "loading") {
                        LoadingBanner()
                    }
                }
                if (myBookingsForRoom.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }, key = "my-bookings") {
                        MyRoomBookings(bookings = myBookingsForRoom)
                    }
                }
                item(span = { GridItemSpan(3) }, key = "floorplan") {
                    BibFloorplan(
                        modifier = Modifier.padding(bottom = 14.dp),
                        highlightRoomId = booking.roomId
                    )
                }
                item(span = { GridItemSpan(3) }, key = "hint") {
                    BookingHint()
                }
                item(span = { GridItemSpan(3) }, key = "legend") {
                    Legend()
                }
                items(
                    count = SLOT_COUNT,
                    key = { it },
                    span = { GridItemSpan(1) }
                ) { idx ->
                    SlotCell(
                        idx = idx,
                        status = statusAt(idx),
                        past = isPast(idx),
                        selected = idx in booking.selected,
                        onClick = { onToggleSlot(idx) }
                    )
                }
                item(span = { GridItemSpan(3) }, key = "footer") {
                    val hours = snapshot?.openHoursFor(booking.date)
                    val label = if (hours != null) {
                        "Bibliothek geöffnet %d:%02d – %d:%02d · 30-Min-Slots".format(
                            hours.first.hour, hours.first.minute,
                            hours.second.hour, hours.second.minute
                        )
                    } else {
                        "Bibliothek an diesem Tag geschlossen"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = HiUniColors.semantics.onSurfaceMuted,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                booking.error?.let { msg ->
                    item(span = { GridItemSpan(3) }, key = "err") {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = HiUniColors.semantics.red,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            StickyCta(
                booking = booking,
                onConfirm = onConfirm,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun BookingHeader(title: String, subtitle: String, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
            .padding(start = 6.dp, end = 22.dp, top = 12.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun BookingHint() {
    val semantics = HiUniColors.semantics
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(
            text = "ZEITRAUM WÄHLEN",
            style = MaterialTheme.typography.labelSmall,
            color = semantics.onSurfaceMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = "Tippe einen freien Slot an. Angrenzende Slots verlängern die Buchung (max. 2 h).",
            style = MaterialTheme.typography.bodySmall,
            color = semantics.onSurfaceMuted
        )
    }
}

@Composable
private fun LoadingBanner() {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = semantics.surfaceAlt,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.primary
            )
            Column {
                Text(
                    text = "Lade Belegung …",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Buchungen für diesen Raum werden geprüft",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun MyRoomBookings(bookings: List<MyBooking>) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            text = "MEINE BUCHUNGEN FÜR DIESEN RAUM",
            style = MaterialTheme.typography.labelSmall,
            color = semantics.onSurfaceMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        bookings.forEach { b ->
            Surface(
                color = colors.primaryContainer,
                shape = RoundedCornerShape(HiUniRadii.tile),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = b.date.format(DateTimeFormats.dayShort) + " · %02d:%02d – %02d:%02d".format(
                                b.startTime.hour, b.startTime.minute,
                                b.endTime.hour, b.endTime.minute
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Im Kalender als „Bibliothek · ${BibConfig.ROOM_META[b.roomId]?.label ?: "F${b.roomId}"}“",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Row(
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LegendItem(label = "Frei", color = semantics.greenSurface)
        LegendItem(label = "Gewählt", color = colors.primary)
        LegendItem(label = "Deine Buchung", color = colors.primaryContainer)
        LegendItem(label = "Belegt", color = semantics.surfaceAlt, withBorder = true)
    }
}

@Composable
private fun LegendItem(label: String, color: Color, withBorder: Boolean = false) {
    val semantics = HiUniColors.semantics
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val box = Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
        Box(
            modifier = if (withBorder)
                box.border(width = 1.dp, color = semantics.surfaceAlt, shape = RoundedCornerShape(4.dp))
            else box
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = semantics.onSurfaceMuted,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SlotCell(
    idx: Int,
    status: SlotStatus,
    past: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val start = slotIdxToTime(idx)
    val end = slotIdxToTime(idx + 1)

    val ownBooking = status == SlotStatus.OWN_BOOKING
    val taken = status == SlotStatus.BOOKED
    val closed = status == SlotStatus.CLOSED
    val free = status == SlotStatus.FREE
    val disabled = past || taken || ownBooking || closed

    val bg: Color = when {
        selected -> colors.primary
        ownBooking -> colors.primaryContainer
        taken -> semantics.surfaceAlt
        closed -> semantics.surfaceAlt.copy(alpha = 0.6f)
        past -> Color.Transparent
        free -> semantics.greenSurface
        else -> semantics.surfaceAlt
    }
    val fg: Color = when {
        selected -> colors.onPrimary
        ownBooking -> colors.primary
        taken -> semantics.onSurfaceMuted
        closed -> semantics.onSurfaceMuted
        past -> semantics.onSurfaceMuted
        free -> semantics.green
        else -> semantics.onSurfaceMuted
    }
    val subLabel = when {
        ownBooking -> "gebucht"
        taken -> "belegt"
        closed -> "geschl."
        past -> "vorbei"
        else -> "bis %02d:%02d".format(end.hour, end.minute)
    }

    val baseModifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .clip(RoundedCornerShape(HiUniRadii.tile - 4.dp))
        .background(bg)
    val boxModifier = when {
        past && !taken -> baseModifier.border(
            width = 1.dp,
            color = semantics.surfaceAlt,
            shape = RoundedCornerShape(HiUniRadii.tile - 4.dp)
        )
        selected -> baseModifier.border(
            width = 1.dp,
            color = colors.primary,
            shape = RoundedCornerShape(HiUniRadii.tile - 4.dp)
        )
        else -> baseModifier
    }

    val slotLabel = "%02d:%02d bis %02d:%02d Uhr, %s".format(
        start.hour, start.minute, end.hour, end.minute,
        if (selected) "ausgewählt" else "frei"
    )
    Box(
        modifier = if (disabled) {
            boxModifier
        } else {
            boxModifier
                .clickable(onClickLabel = slotLabel, onClick = onClick)
                .semantics { role = Role.Checkbox }
        },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%02d:%02d".format(start.hour, start.minute),
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) colors.onPrimary.copy(alpha = 0.7f) else fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun StickyCta(
    booking: BookingScreenState,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val hasSelection = booking.selected.isNotEmpty()
    val sorted = booking.selected.sorted()
    val rangeLabel: String
    val subLabel: String
    if (hasSelection) {
        val startTime = slotIdxToTime(sorted.first())
        val endTime = slotIdxToTime(sorted.last() + 1)
        val durMin = sorted.size * 30
        val durLbl = formatDurationMin(durMin)
        rangeLabel = "%02d:%02d – %02d:%02d".format(
            startTime.hour, startTime.minute, endTime.hour, endTime.minute
        )
        subLabel = "$durLbl · " + booking.date.format(DateTimeFormats.dayShort)
    } else {
        rangeLabel = "Slot wählen"
        subLabel = "Tippe oben einen freien Slot an"
    }
    val canConfirm = hasSelection && !booking.submitting
    Surface(
        color = colors.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(semantics.surfaceAlt)
            )
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rangeLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (hasSelection) colors.onSurface else semantics.onSurfaceMuted,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = subLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                val haptics = LocalHapticFeedback.current
                Surface(
                    color = if (canConfirm) colors.primary else semantics.surfaceAlt,
                    shape = RoundedCornerShape(HiUniRadii.tile),
                    modifier = Modifier
                        .clickable(enabled = canConfirm, onClickLabel = "Buchen") {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onConfirm()
                        }
                        .semantics { role = Role.Button }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (booking.submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.onPrimary
                            )
                        } else {
                            Text(
                                text = "Buchen",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (canConfirm) colors.onPrimary else semantics.onSurfaceMuted,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmationView(
    booking: BookingScreenState,
    meta: BibConfig.RoomMeta?,
    onDone: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val sorted = booking.selected.sorted()
    val startTime = slotIdxToTime(sorted.first())
    val endTime = slotIdxToTime(sorted.last() + 1)
    val durMin = sorted.size * 30
    val durLbl = formatDurationMin(durMin)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 22.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(semantics.greenSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = semantics.green,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Raum gebucht",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Deine Reservierung steht jetzt in der Bibliotheks-Übersicht und im Kalender.",
            style = MaterialTheme.typography.bodySmall,
            color = semantics.onSurfaceMuted,
            modifier = Modifier.padding(horizontal = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(HiUniRadii.card),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "RESERVIERUNG",
                    style = MaterialTheme.typography.labelSmall,
                    color = semantics.onSurfaceMuted,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Gruppenraum ${meta?.label ?: "F${booking.roomId}"}",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    fontWeight = FontWeight.ExtraBold
                )
                meta?.let {
                    Text(
                        text = "F-Gebäude · ${it.capacityMin}–${it.capacityMax} Personen",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(semantics.surfaceAlt)
                )
                Spacer(Modifier.height(14.dp))
                SummaryRow(
                    label = "Tag",
                    value = booking.date.format(DateTimeFormats.dayShort)
                )
                Spacer(Modifier.height(8.dp))
                SummaryRow(
                    label = "Uhrzeit",
                    value = "%02d:%02d – %02d:%02d".format(
                        startTime.hour, startTime.minute, endTime.hour, endTime.minute
                    )
                )
                Spacer(Modifier.height(8.dp))
                SummaryRow(label = "Dauer", value = durLbl)
            }
        }

        Spacer(Modifier.height(18.dp))
        Surface(
            color = colors.primary,
            shape = RoundedCornerShape(HiUniRadii.tile),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Fertig", onClick = onDone)
                .semantics { role = Role.Button }
        ) {
            Box(
                modifier = Modifier.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Fertig",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = semantics.onSurfaceMuted,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

private fun formatDurationMin(mins: Int): String {
    val h = mins / 60
    val m = mins % 60
    return buildString {
        if (h > 0) append("${h}h")
        if (m > 0) {
            if (h > 0) append(' ')
            append("$m Min")
        }
        if (isEmpty()) append("0 Min")
    }
}

/** Compose lazy-grid item span helper. */
private fun GridItemSpan(currentLineSpan: Int) =
    androidx.compose.foundation.lazy.grid.GridItemSpan(currentLineSpan)
