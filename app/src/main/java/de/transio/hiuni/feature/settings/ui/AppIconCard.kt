package de.transio.hiuni.feature.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AppShortcut
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.transio.hiuni.R
import de.transio.hiuni.core.common.Semester
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii

/**
 * Picker für das Launcher-Icon. Zeigt eine horizontal scrollende Reihe
 * mit Vorschau-Tiles — die ausgewählte Variante hat einen Akzent-Border.
 *
 * Die Preview-Drawables sind exakt die Adaptive-Icons aus mipmap-anydpi/.
 * Wir laden sie über `painterResource(R.mipmap.ic_launcher_<variant>)`.
 * Adaptive-Icon-XMLs rendern in Compose nicht automatisch mit dem Launcher-
 * Maskenshape (Circle/Squircle), deshalb cliphen wir die Tile selbst rund,
 * damit die Vorschau wie im echten Launcher aussieht.
 */
@Composable
internal fun AppIconCard(
    selectedVariant: String,
    firstSemester: Semester?,
    currentSemester: Semester,
    isAuthenticated: Boolean,
    onSelect: (String) -> Unit
) {
    val variants = appIconOptions()
    val semestersSinceFirst = firstSemester
        ?.let { Semester.semestersBetween(it, currentSemester) }
        ?: 0
    val subtitle = if (isAuthenticated) {
        "Neue Varianten schalten sich jedes Semester frei"
    } else {
        "Ohne Uni-Login alle Varianten frei zum Ausprobieren"
    }
    SectionCard(
        icon = Icons.Outlined.AppShortcut,
        title = "App-Icon",
        subtitle = subtitle
    ) {
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            variants.forEach { option ->
                // Not-Logged-In = Demo-Modus: alle Icons offen. Sonst gilt das
                // Semester-Gate gegen den firstSemester-Anchor.
                val isUnlocked = !isAuthenticated ||
                    option.unlocksAfterSemesters <= semestersSinceFirst
                val unlockSemester = if (isUnlocked || firstSemester == null) null
                else Semester.advance(firstSemester, option.unlocksAfterSemesters)
                AppIconTile(
                    option = option,
                    selected = option.key == selectedVariant,
                    locked = !isUnlocked,
                    unlockHint = unlockSemester?.let { "Ab ${it.displayLabel()}" },
                    onClick = { if (isUnlocked) onSelect(option.key) }
                )
            }
        }
    }
}

@Composable
private fun AppIconTile(
    option: AppIconOption,
    selected: Boolean,
    locked: Boolean,
    unlockHint: String?,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val haptics = LocalHapticFeedback.current
    val borderColor = if (selected) colors.primary else Color.Transparent
    // Locked-Tiles: gedimmtes Background, kein Vorschau-Foreground, stattdessen
    // ein Schloss-Icon zentral. Cap-Color bleibt als Teaser sichtbar, damit der
    // User erahnt was sich später freischaltet.
    val tileBackground = if (locked) option.backgroundFallback.copy(alpha = 0.35f)
    else option.backgroundFallback
    val clickLabel = if (locked) {
        unlockHint?.let { "${option.label}, gesperrt bis $it" } ?: "${option.label}, gesperrt"
    } else {
        "${option.label} auswählen"
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(HiUniRadii.tile))
            .clickable(onClickLabel = clickLabel) {
                if (!locked && !selected) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            }
            .semantics { role = Role.RadioButton }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(tileBackground)
                .border(BorderStroke(if (selected) 3.dp else 0.dp, borderColor), CircleShape)
        ) {
            if (locked) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = unlockHint,
                    tint = colors.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp)
                )
            } else {
                Image(
                    painter = painterResource(id = option.previewRes),
                    contentDescription = option.label,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                )
            }
            if (selected && !locked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.primary else semantics.onSurfaceMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private data class AppIconOption(
    val key: String,
    val label: String,
    val previewRes: Int,
    /**
     * Fallback-Background, falls das Adaptive-Icon-XML in Compose's
     * painterResource nur das Foreground rendert. Matched die Background-
     * Color des jeweiligen Adaptive-Icons in colors.xml.
     */
    val backgroundFallback: Color,
    /**
     * Wie viele Semester nach dem ersten App-Start die Variante freigeschaltet
     * wird. `0` = sofort verfügbar. Wir vergleichen gegen
     * `Semester.semestersBetween(firstSemester, currentSemester)`.
     */
    val unlocksAfterSemesters: Int = 0
)

@Composable
private fun appIconOptions(): List<AppIconOption> {
    // Die Background-Farben sind Spiegel der `ic_launcher_bg_*`-Colors aus
    // res/values/colors.xml — Compose kennt keine direkten Adaptive-Icon-
    // Renderings, also brauchen wir den Hex-Wert hier nochmal hardcoded
    // als Fallback hinter dem Foreground.
    // WICHTIG: painterResource() kann **keine** Adaptive-Icon-XMLs aus mipmap/
    // laden (die sind kein <vector>). Wir referenzieren direkt die Foreground-
    // Drawables aus drawable/ — die sind Vektoren und werden über das
    // backgroundFallback-Color-Layer gerendert, was visuell dem Adaptive-Icon
    // im Launcher entspricht.
    return listOf(
        AppIconOption(
            key = SettingsDataStore.APP_ICON_VARIANT_DEFAULT,
            label = "Standard",
            previewRes = R.drawable.ic_launcher_foreground,
            backgroundFallback = Color(0xFF3D3FBF),
            unlocksAfterSemesters = 0
        ),
        AppIconOption(
            key = SettingsDataStore.APP_ICON_VARIANT_DARK,
            label = "Dunkel",
            previewRes = R.drawable.ic_launcher_foreground_dark,
            backgroundFallback = Color(0xFF26264F),
            unlocksAfterSemesters = 1
        ),
        AppIconOption(
            key = SettingsDataStore.APP_ICON_VARIANT_CLASSIC,
            label = "Klassisch",
            previewRes = R.drawable.ic_launcher_foreground_inverted,
            backgroundFallback = Color(0xFFFFFFFF),
            unlocksAfterSemesters = 2
        ),
        AppIconOption(
            key = SettingsDataStore.APP_ICON_VARIANT_STUDI,
            label = "Studi",
            previewRes = R.drawable.ic_launcher_foreground_studi,
            backgroundFallback = Color(0xFFE4B056),
            unlocksAfterSemesters = 3
        )
    )
}
