package de.transio.hiuni.feature.notifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.notifications.data.NotificationCategory
import de.transio.hiuni.feature.notifications.NotificationSettingsViewModel

/**
 * Karte „Kategorien" für den Erinnerungen-&-Push-Screen: pro
 * [NotificationCategory] ein Toggle, das VOR dem NotificationPresenter greift
 * (aus = weder OS-Notification noch Push-Center-Eintrag).
 *
 * Ergänzt — überschreibt nicht — die Android-Systemeinstellungen: dort schaltet
 * der Nutzer je Channel die OS-Notification stumm, hier die Kategorie komplett.
 * Eigenständig gehaltene Card mit eigenem VM, damit der große SettingsViewModel
 * unberührt bleibt.
 */
@Composable
fun NotificationCategoriesCard(
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val toggles by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = colors.primaryContainer,
                    shape = RoundedCornerShape(HiUniRadii.tile),
                    modifier = Modifier.clip(RoundedCornerShape(HiUniRadii.tile))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Column {
                    Text(
                        text = "Kategorien",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Welche Mitteilungen die App überhaupt zeigt",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantics.onSurfaceMuted
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            toggles.forEachIndexed { index, toggle ->
                if (index > 0) {
                    HorizontalDivider(color = colors.outline.copy(alpha = 0.25f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = categorySettingsLabel(toggle.category),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = toggle.enabled,
                        onCheckedChange = { viewModel.setEnabled(toggle.category, it) }
                    )
                }
            }
        }
    }
}

private fun categorySettingsLabel(category: NotificationCategory): String = when (category) {
    NotificationCategory.EVENTS -> "Termin-Erinnerungen"
    NotificationCategory.EXAMS -> "Klausuren"
    NotificationCategory.GRADES -> "Noten"
    NotificationCategory.COURSES -> "Kurse"
    NotificationCategory.LEARNWEB -> "Learnweb"
    NotificationCategory.MAIL -> "E-Mail"
    NotificationCategory.SYSTEM -> "System & Sonstiges"
}
