package de.transio.hiuni.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii

/**
 * Settings-Hub. Statt einer einzigen langen LazyColumn mit ~15 SectionCards
 * zeigt die Wurzel-Sicht jetzt 6 Kategorien (Konto/Sync/Mail/Erscheinungs-
 * bild/Erinnerungen/Mensa) plus die schon existierenden Layout-Sub-Screens
 * (Tab-Leiste/Startseite/Schnellzugriff). Jede Kategorie öffnet einen
 * eigenen Sub-Screen, in dem dann die feingranularen Cards sitzen.
 *
 * Snackbar-Feedback aus `viewModel.message` läuft pro Sub-Screen — hier im
 * Hub brauchen wir keinen ViewModel-Bezug, weil keine Actions getriggert
 * werden, die Messages erzeugen würden.
 */
@Composable
fun SettingsScreen(
    onOpenAccount: () -> Unit = {},
    onOpenSync: () -> Unit = {},
    onOpenMail: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenReminders: () -> Unit = {},
    onOpenMensa: () -> Unit = {},
    onOpenNavSettings: () -> Unit = {},
    onOpenHomeSettings: () -> Unit = {},
    onOpenQuickAccessSettings: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SettingsHeader()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    CategoryRow(
                        icon = Icons.Outlined.Person,
                        title = "Konto",
                        subtitle = "CAS-Login, LSF-Anmeldung und Mail-Zugangsdaten",
                        onClick = onOpenAccount
                    )
                }
                item {
                    CategoryRow(
                        icon = Icons.Outlined.CloudSync,
                        title = "Synchronisation",
                        subtitle = "Hintergrund-Sync-Intervalle und Status",
                        onClick = onOpenSync
                    )
                }
                item {
                    CategoryRow(
                        icon = Icons.Outlined.Mail,
                        title = "Mail",
                        subtitle = "Wisch-Gesten, Biometrie und Lösch-Verhalten",
                        onClick = onOpenMail
                    )
                }
                item {
                    CategoryRow(
                        icon = Icons.Outlined.DarkMode,
                        title = "Erscheinungsbild",
                        subtitle = "Theme und Anzeigename",
                        onClick = onOpenAppearance
                    )
                }
                item {
                    CategoryRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Erinnerungen & Push",
                        subtitle = "Reminder-Vorlauf und Mitteilungen",
                        onClick = onOpenReminders
                    )
                }
                item {
                    CategoryRow(
                        icon = Icons.Outlined.LocalDining,
                        title = "Mensa",
                        subtitle = "Standort wählen",
                        onClick = onOpenMensa
                    )
                }
                // Layout-Sektion: die drei existierenden Sub-Screens bleiben
                // unverändert erreichbar — Pattern war schon vor dem Umbau so.
                item { Spacer(Modifier.height(4.dp)) }
                item { SectionLabel("LAYOUT") }
                item {
                    CategoryRow(
                        icon = Icons.Outlined.GridView,
                        title = "Tab-Leiste anpassen",
                        subtitle = "Reihenfolge und sichtbare Tabs ändern",
                        onClick = onOpenNavSettings
                    )
                }
                item {
                    CategoryRow(
                        icon = Icons.Outlined.Home,
                        title = "Startseite anpassen",
                        subtitle = "Sektionen sichtbar machen, ausblenden und sortieren",
                        onClick = onOpenHomeSettings
                    )
                }
                item {
                    CategoryRow(
                        icon = Icons.Outlined.Apps,
                        title = "Schnellzugriff anpassen",
                        subtitle = "Kacheln auf der Startseite wählen und sortieren",
                        onClick = onOpenQuickAccessSettings
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun SettingsHeader() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp)
    ) {
        Text(
            text = "Einstellungen",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onSurface
        )
        Text(
            text = "Wähle eine Kategorie",
            style = MaterialTheme.typography.bodyMedium,
            color = HiUniColors.semantics.onSurfaceMuted,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = HiUniColors.semantics.onSurfaceMuted,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun CategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = colors.primaryContainer,
                shape = RoundedCornerShape(HiUniRadii.tile)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = semantics.onSurfaceMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
