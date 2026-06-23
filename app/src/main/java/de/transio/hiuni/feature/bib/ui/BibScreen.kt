package de.transio.hiuni.feature.bib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.bib.BibUiState
import de.transio.hiuni.feature.bib.BibViewModel
import de.transio.hiuni.feature.bib.data.BibConfig
import de.transio.hiuni.feature.bib.data.MyBooking
import de.transio.hiuni.feature.bib.data.RoomDayAvailability
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibScreen(viewModel: BibViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.booking != null) {
                BackHandler(enabled = !state.booking!!.confirmed) {
                    viewModel.closeBookingScreen()
                }
                BackHandler(enabled = state.booking!!.confirmed) {
                    viewModel.acknowledgeBookingDone()
                }
                LibraryBookingScreen(
                    booking = state.booking!!,
                    snapshot = state.data.snapshot,
                    loading = state.data.loading,
                    onBack = viewModel::closeBookingScreen,
                    onToggleSlot = { idx ->
                        val booking = state.booking ?: return@LibraryBookingScreen
                        val day = state.data.snapshot?.forRoomDay(booking.date, booking.roomId)
                        val slotsByTime = day?.slots?.associateBy { it.startTime }.orEmpty()
                        val now = LocalTime.now()
                        val isToday = booking.date == java.time.LocalDate.now()
                        viewModel.toggleSlot(idx) { i ->
                            val t = LocalTime.of(8 + i / 2, (i % 2) * 30)
                            val s = slotsByTime[t]?.status
                            (isToday && t <= now) ||
                                s == de.transio.hiuni.feature.bib.data.SlotStatus.BOOKED ||
                                s == de.transio.hiuni.feature.bib.data.SlotStatus.OWN_BOOKING ||
                                s == de.transio.hiuni.feature.bib.data.SlotStatus.CLOSED ||
                                s == null
                        }
                    },
                    onConfirm = { viewModel.confirmBookingScreen(::slotIdxToStartHHMM) },
                    onDone = viewModel::acknowledgeBookingDone
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        BibHeader(
                            state = state,
                            onSelectDate = viewModel::selectDate
                        )
                        BibBody(
                            state = state,
                            onCancel = viewModel::cancel,
                            onBook = { roomId -> viewModel.openBookingScreen(roomId, state.selectedDate) }
                        )
                    }
                }
            }
        }
    }
}

/* ───────────────────────────────────────────────────────────
 * Header — Titel · GEÖFFNET-Pille · Tagestitel · Day-Picker
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun BibHeader(
    state: BibUiState,
    onSelectDate: (LocalDate) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val snapshot = state.data.snapshot
    val today = snapshot?.today
    val selectedDate = state.selectedDate ?: today
    val rooms = snapshot?.roomsToday.orEmpty()
    val closedToday = rooms.isNotEmpty() && rooms.all { it.openCount == 0 }
    val openNow = !closedToday && isOpenNow()

    val selectableDates = snapshot?.availableDates()?.take(14).orEmpty()

    val subtitle = buildString {
        if (selectedDate != null) {
            append(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)))
            append(" · ")
        }
        append("8:00 – 20:00 Uhr")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(start = 22.dp, end = 22.dp, top = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Bibliothek",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            StatusPill(
                text = if (openNow) "GEÖFFNET" else "GESCHLOSSEN",
                color = if (openNow) semantics.green else semantics.red,
                background = if (openNow) semantics.greenSurface else semantics.redSurface
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = semantics.onSurfaceMuted,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 2.dp)
        )

        if (selectableDates.isNotEmpty() && today != null) {
            Spacer(Modifier.height(14.dp))
            DayPickerRow(
                dates = selectableDates,
                selected = selectedDate,
                today = today,
                bookedDates = snapshot.myBookings.map { it.date }.toSet(),
                onSelect = onSelectDate
            )
            Spacer(Modifier.height(14.dp))
        } else {
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun DayPickerRow(
    dates: List<LocalDate>,
    selected: LocalDate?,
    today: LocalDate,
    bookedDates: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(dates, key = { it.toEpochDay() }) { date ->
            DayChip(
                date = date,
                isToday = date == today,
                isTomorrow = date == today.plusDays(1),
                active = date == selected,
                hasBooking = date in bookedDates,
                onClick = { onSelect(date) }
            )
        }
    }
}

@Composable
private fun DayChip(
    date: LocalDate,
    isToday: Boolean,
    isTomorrow: Boolean,
    active: Boolean,
    hasBooking: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val label = when {
        isToday -> "HEUTE"
        isTomorrow -> "MORGEN"
        else -> date.format(DateTimeFormatter.ofPattern("EEE", Locale.GERMAN))
            .replace(".", "")
            .uppercase(Locale.GERMAN)
    }
    val labelColor = when {
        active -> Color.White.copy(alpha = 0.72f)
        isToday -> colors.primary
        else -> semantics.onSurfaceMuted
    }
    val dayColor = when {
        active -> Color.White
        isToday -> colors.primary
        else -> colors.onSurface
    }
    // Fixe Breite pro Chip, sodass MORGEN-Chip (lange Beschriftung) nicht
    // breiter wird als HEUTE/SA. Typografie an MensaScreen.WeekDayCell
    // angelehnt (labelMedium + titleLarge) damit's nicht squashed wirkt.
    Box(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(HiUniRadii.tile))
            .background(if (active) colors.primary else semantics.surfaceAlt)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = dayColor,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )
        }
        if (hasBooking) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (active) Color.White else colors.primary)
            )
        }
    }
}

/* ───────────────────────────────────────────────────────────
 * Body — Meine Buchungen · Stats · Räume
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun BibBody(
    state: BibUiState,
    onCancel: (MyBooking) -> Unit,
    onBook: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val snapshot = state.data.snapshot
    val today = snapshot?.today
    val selectedDate = state.selectedDate ?: today
    val isToday = selectedDate != null && selectedDate == today

    val bookingsForDay = remember(snapshot, selectedDate) {
        snapshot?.myBookings.orEmpty().filter { it.date == selectedDate }
    }
    val roomsForDay = remember(snapshot, selectedDate) {
        if (snapshot == null || selectedDate == null) emptyList()
        else BibConfig.ROOM_IDS.map { roomId ->
            snapshot.forRoomDay(selectedDate, roomId)
                ?: RoomDayAvailability(selectedDate, roomId, emptyList())
        }
    }
    val freeCount = roomsForDay.count { it.openCount > 0 && it.utilization < 0.8f }
    val totalCapacity = BibConfig.ROOM_META.values.sumOf { it.capacityMax }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp, end = 18.dp, top = 16.dp, bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (bookingsForDay.isNotEmpty()) {
            item(key = "my-header") {
                SectionLabel("Meine Buchungen")
            }
            items(bookingsForDay, key = { it.id }) { booking ->
                MyBookingCard(
                    booking = booking,
                    onCancel = { onCancel(booking) },
                    cancelDisabled = state.cancelInProgress
                )
            }
            item(key = "rooms-spacer") {
                Spacer(Modifier.height(4.dp))
            }
        }

        if (snapshot == null && state.data.loading) {
            item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            return@LazyColumn
        }
        if (snapshot == null) {
            item(key = "no-data") {
                Text(
                    text = state.data.lastError ?: "Noch keine Daten geladen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted
                )
            }
            return@LazyColumn
        }

        item(key = "rooms-header") {
            SectionLabel("Räume")
        }
        items(roomsForDay, key = { it.roomId }) { day ->
            val ownBookingsForRoom = bookingsForDay.filter { it.roomId == day.roomId }
            RoomCard(
                day = day,
                isToday = isToday,
                ownBookings = ownBookingsForRoom,
                onBook = { onBook(day.roomId) }
            )
        }

        item(key = "floorplan") {
            Spacer(Modifier.height(6.dp))
            BibFloorplan()
        }
    }
}

@Composable
private fun StatsPanel(freeCount: Int, roomCount: Int, capacity: Int) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StatColumn("Verfügbar", freeCount.toString(), colors.primary)
            StatColumn("Räume", roomCount.toString(), colors.onSurface)
            StatColumn("Plätze", capacity.toString(), colors.onSurface)
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = HiUniColors.semantics.onSurfaceMuted,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

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

/* ───────────────────────────────────────────────────────────
 * Eigene Buchungen — primärfarbene Karte mit Check
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun MyBookingCard(
    booking: MyBooking,
    onCancel: () -> Unit,
    cancelDisabled: Boolean
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.primary,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = (-20).dp)
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(HiUniRadii.tile - 2.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gruppenraum ${booking.roomLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${formatTime(booking.startTime)} – ${formatTime(booking.endTime)} · ${formatDuration(booking.startTime, booking.endTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Surface(
                    color = Color.White.copy(alpha = if (cancelDisabled) 0.08f else 0.18f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable(enabled = !cancelDisabled, onClick = onCancel)
                ) {
                    Text(
                        text = "Stornieren",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = if (cancelDisabled) 0.5f else 1f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

/* ───────────────────────────────────────────────────────────
 * Raum-Karte
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun RoomCard(
    day: RoomDayAvailability,
    isToday: Boolean,
    ownBookings: List<MyBooking>,
    onBook: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val meta = BibConfig.ROOM_META[day.roomId]
    val isClosed = day.openCount == 0
    val isFull = day.utilization >= 0.95f
    val canShowBookPill = !isClosed && !isFull
    val hasOwn = ownBookings.isNotEmpty()

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (canShowBookPill) Modifier.clickable(onClick = onBook) else Modifier
            )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Gruppenraum ${meta?.label ?: "F${day.roomId}"}",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        if (hasOwn) {
                            StatusPill(
                                text = "GEBUCHT",
                                color = colors.onPrimary,
                                background = colors.primary
                            )
                        }
                    }
                    Text(
                        text = meta?.let { "F-Gebäude · ${it.capacityMin}–${it.capacityMax} Personen" }
                            ?: "F-Gebäude",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (canShowBookPill) {
                    BookPill(onClick = onBook)
                } else if (isClosed) {
                    StatusPill(
                        text = "Geschlossen",
                        color = semantics.onSurfaceMuted,
                        background = semantics.surfaceAlt
                    )
                } else if (isFull) {
                    StatusPill(
                        text = "Belegt",
                        color = semantics.red,
                        background = semantics.redSurface
                    )
                }
            }

            if (hasOwn) {
                Spacer(Modifier.height(8.dp))
                ownBookings.forEach { b ->
                    Text(
                        text = "✓ %02d:%02d – %02d:%02d".format(
                            b.startTime.hour, b.startTime.minute,
                            b.endTime.hour, b.endTime.minute
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private enum class StatusBucket(val label: String) {
    FREE("Frei"),
    PARTIAL("Teilweise"),
    FULL("Belegt"),
    CLOSED("Geschlossen")
}

/* ───────────────────────────────────────────────────────────
 * Bausteine
 * ─────────────────────────────────────────────────────────── */

@Composable
private fun StatusPill(text: String, color: Color, background: Color) {
    Surface(
        color = background,
        shape = RoundedCornerShape(8.dp)
    ) {
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
private fun BookPill(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.primaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Buchen",
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun UtilizationBar(utilization: Float, color: Color) {
    val semantics = HiUniColors.semantics
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(semantics.surfaceAlt)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(utilization.coerceIn(0f, 1f))
                .height(6.dp)
                .background(color)
        )
    }
}

private fun formatTime(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

private fun formatDuration(start: LocalTime, end: LocalTime): String {
    val mins = java.time.Duration.between(start, end).toMinutes().toInt().coerceAtLeast(0)
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

private fun isOpenNow(): Boolean {
    val now = LocalTime.now()
    return now >= LocalTime.of(8, 0) && now < LocalTime.of(20, 0)
}
