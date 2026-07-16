package de.transio.hiuni.feature.profile.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.design.components.HiUniTopBar
import de.transio.hiuni.core.design.components.QuickTile
import de.transio.hiuni.core.design.components.SectionLabel
import de.transio.hiuni.feature.profile.ProfileViewModel
import de.transio.hiuni.navigation.Destination
import de.transio.hiuni.ui.responsive.LocalWindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

@Composable
fun ProfileScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val isExpanded = LocalWindowSizeClass.current?.widthSizeClass ==
        WindowWidthSizeClass.Expanded
    // Auf Tablet-Landscape 3 Spalten — entlastet die Vertikale, auf Phones 2.
    val tileColumns = if (isExpanded) 3 else 2

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            HiUniTopBar(title = "Profil", onBack = onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    IdentityCard(
                        state = state,
                        onLogin = { onNavigate(Destination.Settings) }
                    )
                }

                item {
                    InfoCard(
                        state = state,
                        onMailClick = { mail ->
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$mail"))
                                )
                            }
                        },
                        onCopyMatrikel = { mtknr ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            clipboard.setText(AnnotatedString(mtknr))
                            Toast.makeText(
                                context,
                                "Matrikelnummer kopiert",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }

                item {
                    SectionLabel(text = "Schnellzugriff")
                }

                // Settings als Hero-Tile: voll-breit, größer, primary-gefärbt.
                // Liegt direkt unter dem Section-Label, damit es als „erstes
                // Anlaufpunkt für App-Anpassung" sofort ins Auge fällt.
                item {
                    SettingsHeroTile(onClick = { onNavigate(Destination.Settings) })
                }

                val tiles = Destination.all.filter {
                    it !is Destination.Profile &&
                        it !is Destination.Home &&
                        it !is Destination.Settings
                }
                items(
                    items = tiles.chunked(tileColumns),
                    key = { rowTiles -> rowTiles.joinToString("-") { it.route } }
                ) { rowTiles ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowTiles.forEach { destination ->
                            QuickTile(
                                modifier = Modifier.weight(1f),
                                icon = destination.icon,
                                title = destination.label,
                                subtitle = subtitleFor(destination),
                                accent = colors.primary,
                                surface = colors.surface,
                                onClick = { onNavigate(destination) }
                            )
                        }
                        // Füll-Spacer falls die letzte Reihe nicht voll ist —
                        // sonst werden die letzten Tiles disproportional breit.
                        repeat(tileColumns - rowTiles.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun IdentityCard(
    state: de.transio.hiuni.feature.profile.ProfileUiState,
    onLogin: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val profile = state.profile
    val initials = buildString {
        profile.firstName?.take(1)?.let { append(it) }
        profile.nachname?.take(1)?.let { append(it) }
    }.uppercase().ifBlank { "?" }

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = colors.primary
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = profile.fullName ?: "Nicht angemeldet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Spacer(Modifier.height(4.dp))
            if (state.isAuthenticated) {
                profile.matrikel?.let { mtknr ->
                    Text(
                        text = "Matrikel: $mtknr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = semantics.onSurfaceMuted
                    )
                }
            } else {
                Text(
                    text = "Melde dich an, um dein Uni-Profil zu sehen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onLogin) {
                    Text("Anmelden")
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    state: de.transio.hiuni.feature.profile.ProfileUiState,
    onMailClick: (String) -> Unit,
    onCopyMatrikel: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val profile = state.profile

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            InfoRow(
                icon = Icons.Outlined.Mail,
                label = "Uni-Mail",
                value = profile.mail ?: "—",
                onClick = profile.mail?.takeIf { it.isNotBlank() }?.let { mail ->
                    { onMailClick(mail) }
                }
            )
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            InfoRow(
                icon = Icons.Outlined.Badge,
                label = "UID",
                value = profile.uid ?: "—"
            )
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            InfoRow(
                icon = Icons.Outlined.Numbers,
                label = "Matrikelnummer",
                value = profile.matrikel ?: "—",
                onLongClick = profile.matrikel?.takeIf { it.isNotBlank() }?.let { mtknr ->
                    { onCopyMatrikel(mtknr) }
                }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val baseModifier = Modifier
        .fillMaxWidth()
        .let { mod ->
            when {
                onClick != null && onLongClick != null ->
                    mod.combinedClickableSafe(
                        onClick = onClick,
                        onClickLabel = "$label öffnen",
                        onLongClick = onLongClick,
                        onLongClickLabel = "$label kopieren"
                    ).semantics { role = Role.Button }
                onClick != null -> mod
                    .clickable(onClickLabel = "$label öffnen") { onClick() }
                    .semantics { role = Role.Button }
                onLongClick != null -> mod
                    .combinedClickableSafe(
                        onClick = {},
                        onLongClick = onLongClick,
                        onLongClickLabel = "$label kopieren"
                    )
                else -> mod
            }
        }
        .padding(horizontal = 16.dp, vertical = 14.dp)

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(HiUniRadii.tile))
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = semantics.onSurfaceMuted
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Wrapper um Foundation's combinedClickable damit der @OptIn-Marker nicht durch
 * den ganzen Screen propagieren muss.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableSafe(
    onClick: () -> Unit,
    onClickLabel: String? = null,
    onLongClick: () -> Unit,
    onLongClickLabel: String? = null
): Modifier = this.combinedClickable(
    onClick = onClick,
    onClickLabel = onClickLabel,
    onLongClick = onLongClick,
    onLongClickLabel = onLongClickLabel
)

/**
 * Voll-breite Hero-Variante des Settings-Tiles. Liegt im Quick-Access oben,
 * fällt durch primary-gefüllte Surface, größeren Icon-Container und
 * `titleLarge`-Schrift sofort ins Auge — der „Settings"-Eintrag ist der
 * meistgesuchte Einstieg von der Profil-Seite aus.
 */
@Composable
private fun SettingsHeroTile(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.primary,
        shape = RoundedCornerShape(HiUniRadii.card),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile))
                    .background(colors.onPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Destination.Settings.icon,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Einstellungen",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "App anpassen — Sync, Mitteilungen, Icon, Theme",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimary.copy(alpha = 0.85f)
                )
            }
        }
    }
}

private fun subtitleFor(destination: Destination): String = when (destination) {
    Destination.Calendar -> "Stundenplan ansehen"
    Destination.Mensa -> "Mensa-Pläne"
    Destination.Courses -> "Meine Veranstaltungen"
    Destination.Email -> "Posteingang"
    Destination.Movies -> "Uni-Kino-Programm"
    Destination.Bib -> "Räume & Öffnungszeiten"
    Destination.Todos -> "Offene Aufgaben"
    Destination.Sport -> "Hochschulsport"
    Destination.Exams -> "Termine & Countdown"
    Destination.Grades -> "Notenspiegel & Schnitt"
    Destination.Notifications -> "Push-Center"
    Destination.Settings -> "App anpassen"
    Destination.About -> "Über HiUni"
    else -> ""
}
