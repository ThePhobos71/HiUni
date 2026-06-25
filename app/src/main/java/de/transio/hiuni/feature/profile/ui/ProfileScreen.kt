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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.design.components.QuickTile
import de.transio.hiuni.core.design.components.SectionLabel
import de.transio.hiuni.feature.profile.ProfileViewModel
import de.transio.hiuni.navigation.Destination

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

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Top bar — matched to NavSettingsScreen / HomeHeader styling.
            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(
                    bottomStart = HiUniRadii.big,
                    bottomEnd = HiUniRadii.big
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Zurück",
                            tint = colors.onSurface
                        )
                    }
                    Text(
                        text = "Profil",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                }
            }

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

                if (state.isAuthenticated) {
                    item {
                        StudiCardSection(profile = state.profile)
                    }
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

                val tiles = Destination.all.filter {
                    it !is Destination.Profile && it !is Destination.Home
                }
                items(items = tiles.chunked(2)) { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { destination ->
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
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
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
                    mod.combinedClickableSafe(onClick = onClick, onLongClick = onLongClick)
                onClick != null -> mod.clickable { onClick() }
                onLongClick != null -> mod.combinedClickableSafe(onClick = {}, onLongClick = onLongClick)
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
    onLongClick: () -> Unit
): Modifier = this.combinedClickable(
    onClick = onClick,
    onLongClick = onLongClick
)

private fun subtitleFor(destination: Destination): String = when (destination) {
    Destination.Calendar -> "Stundenplan ansehen"
    Destination.Mensa -> "Mensa-Pläne"
    Destination.Courses -> "Meine Veranstaltungen"
    Destination.Email -> "Posteingang"
    Destination.Movies -> "Uni-Kino-Programm"
    Destination.Bib -> "Räume & Öffnungszeiten"
    Destination.Todos -> "Offene Aufgaben"
    Destination.Settings -> "App anpassen"
    Destination.About -> "Über HiUni"
    else -> ""
}
