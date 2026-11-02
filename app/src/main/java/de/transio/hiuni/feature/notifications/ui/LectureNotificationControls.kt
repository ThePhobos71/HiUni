package de.transio.hiuni.feature.notifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.notifications.lecture.CourseNotificationPrefs

/**
 * Bausteine der Vorlesungs-Mitteilungen je Kurs, gemeinsam genutzt vom Kurs-Detail
 * und der Vorlesungs-Liste in den Einstellungen. Handgestylte Pills wie im Rest der
 * App, einzig der Schalter ist ein Material-Switch (wie in den Einstellungen).
 */
@Composable
fun LectureLiveSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Live-Anzeige während der Vorlesung",
    subtitle: String? = null
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Pills „Aus / 5 Min / 10 Min / 15 Min / 30 Min" für den Erinnerungs-Vorlauf.
 * [compact] verkleinert die Pills für die Zeilen der Kurs-Liste.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LectureReminderChips(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val options: List<Int?> = listOf(null) + CourseNotificationPrefs.REMINDER_OPTIONS
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp)
    ) {
        options.forEach { option ->
            ReminderPill(
                label = reminderLabel(option),
                active = option == selected,
                compact = compact,
                onClick = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun ReminderPill(label: String, active: Boolean, compact: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val description = if (label == "Aus") "Erinnerung aus" else "Erinnerung $label vorher"
    Surface(
        color = if (active) colors.primary else semantics.surfaceAlt,
        shape = RoundedCornerShape(HiUniRadii.pill),
        onClick = onClick,
        modifier = Modifier.semantics {
            role = Role.RadioButton
            selected = active
            contentDescription = description
        }
    ) {
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) colors.onPrimary else semantics.onSurfaceMuted,
            modifier = Modifier.padding(
                horizontal = if (compact) 11.dp else 15.dp,
                vertical = if (compact) 5.dp else 7.dp
            )
        )
    }
}

/** „Aus" bzw. „15 Min". */
fun reminderLabel(minutes: Int?): String = if (minutes == null) "Aus" else "$minutes Min"
