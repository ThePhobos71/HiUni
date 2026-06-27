package de.transio.hiuni.feature.mensacard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.mensacard.MensaCardStats
import de.transio.hiuni.feature.mensacard.MensaCardUiState
import de.transio.hiuni.feature.mensacard.MensaCardViewModel
import de.transio.hiuni.feature.mensacard.TransientScan
import de.transio.hiuni.feature.mensacard.data.MensaCardTransactionEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Volle Mensa-Karten-Sicht — Hero mit Guthaben, NFC-Reader-Visualisierung,
 * Verlauf der letzten Transaktionen. Spiegelt das Design `MensaCardScreen`
 * aus `screens.jsx` (Amber-Gradient-Hero, 130dp Reader-Circle mit Ripples,
 * Transaktionsliste mit Icon-Tiles).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MensaCardScreen(
    onBack: () -> Unit,
    viewModel: MensaCardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    // Scan-Modus immer aktiv solange diese Sicht offen ist und kein Friend-
    // Banner Aufmerksamkeit braucht — User soll die Karte einfach dranhalten,
    // ohne erst auf den Reader zu tippen.
    DisposableEffect(Unit) {
        viewModel.startScan()
        onDispose { viewModel.cancelScan() }
    }
    LaunchedEffect(state.transientScan, state.scanning) {
        if (state.transientScan == null && !state.scanning) {
            viewModel.startScan()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { ScreenHeader(onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "hero") { BalanceHero(state) }
            if (state.stats.hasData) {
                item(key = "stats") { StatsPanel(state.stats) }
            }
            state.transientScan?.let { transient ->
                if (showAsFriend(transient, state)) {
                    item(key = "friend") {
                        FriendCardBanner(
                            transient = transient,
                            hasPrimary = state.hasPrimary,
                            onAdoptClick = viewModel::adoptTransientAsPrimary,
                            onDismiss = viewModel::dismissTransient
                        )
                    }
                }
            }
            item(key = "scan") {
                ScanCard(state = state)
            }
            state.error?.let { msg ->
                item(key = "error") {
                    ErrorBanner(msg, onDismiss = viewModel::consumeError)
                }
            }
            // delta == 0 sind reine Saldo-Checks, die der User nicht im
            // Verlauf sehen will. Aufladungen + Abbuchungen bleiben.
            val visibleHistory = state.history.filter { it.deltaMilliEuro != 0 }
            if (visibleHistory.isNotEmpty()) {
                item(key = "history-label") { SectionLabel("LETZTE BUCHUNGEN") }
                items(visibleHistory, key = { it.id }) { tx -> TransactionRow(tx) }
            }
        }
    }
}

@Composable
private fun ScreenHeader(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Mensa-Karte",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Guthaben & Buchungen",
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun BalanceHero(state: MensaCardUiState) {
    val semantics = HiUniColors.semantics
    val amber = semantics.amber
    val amberDarker = Color(
        red = (amber.red * 0.78f).coerceIn(0f, 1f),
        green = (amber.green * 0.78f).coerceIn(0f, 1f),
        blue = (amber.blue * 0.78f).coerceIn(0f, 1f),
        alpha = 1f
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HiUniRadii.card))
            .background(Brush.linearGradient(listOf(amber, amberDarker)))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(140.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
            Text(
                text = "AKTUELLES GUTHABEN",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (state.hasBalance) formatEuro(state.primaryBalanceMilliEuro) else "—,— €",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = cardSubtitle(state),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
                fontWeight = FontWeight.Medium
            )
            if (state.hasOnCardLastDebit) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(HiUniRadii.tile - 4.dp)
                ) {
                    Text(
                        text = "Letzte Buchung am Chip · −${formatEuro(state.onCardLastDebitMilliEuro)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanCard(state: MensaCardUiState) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val scanning = state.scanning
    val justScanned = state.transientScan != null
    val circleBg = when {
        justScanned -> semantics.greenSurface
        scanning -> semantics.amberSurface
        else -> colors.primaryContainer
    }
    val iconTint = when {
        justScanned -> semantics.green
        scanning -> semantics.amber
        else -> colors.primary
    }
    val icon: ImageVector = if (justScanned) Icons.Outlined.Check else Icons.Outlined.CreditCard
    val title = when {
        justScanned && state.transientScan?.isOwn == true -> "Karte aktualisiert"
        justScanned -> "Fremde Karte erkannt"
        else -> "Karte auflegen"
    }
    val subtitle = when {
        justScanned -> "Direkt nochmal auflegen für neuen Saldo."
        else -> "Halte deine Karte an die Rückseite deines Smartphones."
    }

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (scanning) ScanRipples()
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(circleBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(if (justScanned) 56.dp else 50.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted
            )
        }
    }
}

@Composable
private fun ScanRipples() {
    val semantics = HiUniColors.semantics
    val transition = rememberInfiniteTransition(label = "scan-ripples")
    listOf(0, 533, 1066).forEachIndexed { idx, offsetMs ->
        val scale by transition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, delayMillis = offsetMs),
                repeatMode = RepeatMode.Restart
            ),
            label = "scale-$idx"
        )
        val alpha by transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, delayMillis = offsetMs),
                repeatMode = RepeatMode.Restart
            ),
            label = "alpha-$idx"
        )
        Box(
            modifier = Modifier
                .size(130.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(semantics.amber.copy(alpha = alpha * 0.25f))
        )
    }
}

@Composable
private fun StatsPanel(stats: MensaCardStats) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                StatColumn(
                    label = "7 Tage",
                    value = formatEuro(stats.thisWeekMilliEuro),
                    accent = colors.primary
                )
                StatColumn(
                    label = "30 Tage",
                    value = formatEuro(stats.thisMonthMilliEuro),
                    accent = colors.onSurface
                )
                StatColumn(
                    label = "Insgesamt",
                    value = formatEuro(stats.totalMilliEuro),
                    accent = semantics.amber
                )
            }
            stats.periodFrom?.let { from ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Beobachtet seit ${formatShortDate(from)} · ${stats.scanCount} Scans",
                    style = MaterialTheme.typography.labelSmall,
                    color = semantics.onSurfaceMuted,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Hinweis: zwischen zwei Scans können mehrere Buchungen liegen — wir summieren netto.",
                    style = MaterialTheme.typography.labelSmall,
                    color = semantics.onSurfaceMuted.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, accent: Color) {
    val semantics = HiUniColors.semantics
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = semantics.onSurfaceMuted,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = HiUniColors.semantics.onSurfaceMuted,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun TransactionRow(tx: MensaCardTransactionEntity) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val isTopUp = tx.isTopUp
    val tileBg = if (isTopUp) semantics.greenSurface else semantics.surfaceAlt
    val iconTint = if (isTopUp) semantics.green else semantics.onSurfaceMuted
    val icon = if (isTopUp) Icons.Outlined.Add else Icons.Outlined.Restaurant
    val amountColor = if (isTopUp) semantics.green else colors.onSurface

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile - 4.dp))
                    .background(tileBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        tx.deltaMilliEuro == 0 -> "Saldo geprüft"
                        isTopUp -> "Aufladung"
                        else -> "Mensa-Buchung"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatAbsoluteTime(Instant.ofEpochMilli(tx.scannedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = semantics.onSurfaceMuted,
                    fontWeight = FontWeight.Medium
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatSignedEuro(tx.deltaMilliEuro),
                    style = MaterialTheme.typography.titleMedium,
                    color = amountColor,
                    fontWeight = FontWeight.ExtraBold
                )
                // Saldo davor + danach erst sobald wir wirklich eine Änderung
                // beobachtet haben — für reine Saldo-Checks (Delta = 0) macht
                // der Vorher/Nachher-Hinweis keinen Sinn.
                if (tx.deltaMilliEuro != 0) {
                    val balanceBefore = tx.balanceMilliEuro - tx.deltaMilliEuro
                    Text(
                        text = "${formatEuro(balanceBefore)} → ${formatEuro(tx.balanceMilliEuro)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = semantics.onSurfaceMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendCardBanner(
    transient: TransientScan,
    hasPrimary: Boolean,
    onAdoptClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val isInitial = !hasPrimary
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = if (isInitial) "Karte erkannt" else "Fremde Karte",
                style = MaterialTheme.typography.labelMedium,
                color = semantics.onSurfaceMuted,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatEuro(transient.scan.valueMilliEuro),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "UID ${transient.scan.uid.take(10)}…",
                style = MaterialTheme.typography.labelSmall,
                color = semantics.onSurfaceMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = colors.primary,
                    shape = RoundedCornerShape(HiUniRadii.tile),
                    modifier = Modifier.clickable(onClick = onAdoptClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonAdd,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isInitial) "Als meine festlegen" else "Stattdessen meine",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Surface(
                    color = semantics.surfaceAlt,
                    shape = RoundedCornerShape(HiUniRadii.tile),
                    modifier = Modifier.clickable(onClick = onDismiss)
                ) {
                    Text(
                        text = if (isInitial) "Später" else "Schließen",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    val semantics = HiUniColors.semantics
    Surface(
        color = semantics.amberSurface,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = semantics.amber,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

private fun showAsFriend(transient: TransientScan, state: MensaCardUiState): Boolean =
    !transient.isOwn || !state.hasPrimary

private fun cardSubtitle(state: MensaCardUiState): String {
    if (!state.hasPrimary) return "Karte zum Festlegen scannen"
    val tail = state.primaryUid.takeLast(6)
    return "Karten-ID …$tail"
}

private fun formatEuro(milliEuro: Int): String =
    "%,.2f €".format(Locale.GERMAN, milliEuro / 1000.0)

private fun formatSignedEuro(milliEuro: Int): String {
    if (milliEuro == 0) return "±0,00 €"
    val sign = if (milliEuro > 0) "+" else "−"
    return "$sign${formatEuro(kotlin.math.abs(milliEuro))}"
}

private val absoluteFormatter = DateTimeFormatter
    .ofPattern("d. MMM yyyy · HH:mm", Locale.GERMAN)

private val shortDateFormatter = DateTimeFormatter
    .ofPattern("d. MMM", Locale.GERMAN)

private fun formatAbsoluteTime(instant: Instant): String =
    absoluteFormatter.format(instant.atZone(ZoneId.systemDefault()))

private fun formatShortDate(instant: Instant): String =
    shortDateFormatter.format(instant.atZone(ZoneId.systemDefault()))
