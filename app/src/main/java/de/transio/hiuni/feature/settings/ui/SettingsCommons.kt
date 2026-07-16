package de.transio.hiuni.feature.settings.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.design.ThemeMode
import de.transio.hiuni.feature.email.MailSwipeAction
import de.transio.hiuni.feature.settings.data.MensaLocation
import java.time.Duration
import java.time.Instant

// ---------------------------------------------------------------------------
// Shared Composables und Helper für den Settings-Hub und seine Sub-Screens.
//
// Die einzelnen Settings-Kategorien (Account/Sync/Mail/...) leben jeweils in
// einem eigenen Sub-Screen und teilen sich die hier definierten Bausteine —
// dadurch bleibt das Aussehen konsistent ohne Duplikation. Alle Composables
// sind `internal`, damit sie für die Sub-Screens sichtbar sind, aber nicht
// nach außen exportiert werden.
// ---------------------------------------------------------------------------

@Composable
internal fun SectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
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
                    modifier = Modifier
                        .clip(RoundedCornerShape(HiUniRadii.tile))
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
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
internal fun LocationRow(
    location: MensaLocation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val background = if (isSelected) colors.primaryContainer else colors.surface
    Surface(
        color = background,
        shape = RoundedCornerShape(HiUniRadii.tile),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClickLabel = "${location.name} auswählen") { onClick() }
                .semantics { role = Role.RadioButton }
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(selected = isSelected, onClick = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) colors.primary else colors.onSurface,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = location.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ChipRow(
    options: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEach { option ->
            val isActive = option == selected
            Surface(
                color = if (isActive) colors.primary else semantics.surfaceAlt,
                shape = RoundedCornerShape(HiUniRadii.pill),
                onClick = { onSelect(option) }
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isActive) colors.onPrimary else semantics.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeModeRow(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ThemeMode.entries.forEach { option ->
            val isActive = option == selected
            Surface(
                color = if (isActive) colors.primary else semantics.surfaceAlt,
                shape = RoundedCornerShape(HiUniRadii.pill),
                onClick = { onSelect(option) }
            ) {
                Text(
                    text = option.displayLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isActive) colors.onPrimary else semantics.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeActionRow(
    selected: MailSwipeAction,
    onSelect: (MailSwipeAction) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        MailSwipeAction.entries.forEach { option ->
            val isActive = option == selected
            Surface(
                color = if (isActive) colors.primary else semantics.surfaceAlt,
                shape = RoundedCornerShape(HiUniRadii.pill),
                onClick = { onSelect(option) }
            ) {
                Text(
                    text = option.displayLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isActive) colors.onPrimary else semantics.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CredentialsCard(
    username: String,
    passwordDraft: String,
    canSave: Boolean,
    hasStored: Boolean,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Uni-Mail-Login (IMAP)",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface
            )
            Text(
                text = if (hasStored) {
                    "Dieselbe RZ-Kennung wie beim CAS-Login. Wird für den IMAP-Mail-Abruf separat gespeichert. " +
                        "Passwort-Feld leerlassen, um nicht zu überschreiben."
                } else {
                    "Dieselbe RZ-Kennung wie beim CAS-Login. Wird für den IMAP-Mail-Abruf gebraucht und " +
                        "lokal AES-256-GCM-verschlüsselt gespeichert."
                },
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = username,
                onValueChange = onUsername,
                label = { Text("RZ-Kennung / Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = passwordDraft,
                onValueChange = onPassword,
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (hasStored) {
                    TextButton(onClick = onClear) {
                        Text("Löschen", color = semantics.red)
                    }
                }
                TextButton(onClick = onSave, enabled = canSave) {
                    Text(if (hasStored) "Aktualisieren" else "Speichern")
                }
            }
        }
    }
}

@Composable
internal fun SyncStatusRow(
    icon: ImageVector,
    label: String,
    lastEpoch: Long,
    isRunning: Boolean,
    onSync: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isRunning) "Synchronisiere…" else "Zuletzt: ${formatRelativeAgo(lastEpoch)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (isRunning) colors.primary else semantics.onSurfaceMuted
            )
        }
        // Beide Zustände in eine 48dp-Box stecken — entspricht der Default-
        // Größe von IconButton (Touch-Target). Damit bleibt die Row-Breite
        // konstant und der Spinner sitzt exakt da, wo vorher das Refresh-Icon
        // war — kein Layout-Glitch beim Toggle.
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colors.primary,
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = onSync) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Jetzt synchronisieren",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SyncStatusDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}

/**
 * Push-Center-Sektion. Beobachtet den POST_NOTIFICATIONS-Status über den
 * Lifecycle (re-check bei ON_RESUME, damit System-Settings-Wechsel sofort
 * sichtbar werden) und zeigt drei Zustände:
 *
 *   1. Granted (oder API < 33): nur Test-Button.
 *   2. Erste Anfrage offen: „Mitteilungen aktivieren" → System-Dialog.
 *   3. Permanent verweigert („Don't ask again"): „Zu App-Einstellungen" →
 *      `ACTION_APP_NOTIFICATION_SETTINGS`.
 */
@Composable
internal fun PushCenterCard(onTestNotification: () -> Unit, isTestRunning: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    var hasPermission by remember { mutableStateOf(checkPermission()) }
    var requestAttempted by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = checkPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        requestAttempted = true
    }

    SectionCard(
        icon = if (hasPermission) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
        title = "Push-Center",
        subtitle = if (hasPermission) "Erinnerungen und Mitteilungen aus der App"
        else "Aktiviere Mitteilungen, damit Reminder durchkommen"
    ) {
        if (!hasPermission) {
            Text(
                text = "Ohne Mitteilungs-Erlaubnis siehst du Erinnerungen nur im Push-Center, nicht auf dem Sperrbildschirm.",
                style = MaterialTheme.typography.bodySmall,
                color = HiUniColors.semantics.onSurfaceMuted
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    if (requestAttempted) {
                        // Nach „Don't ask again" zeigt der System-Dialog nichts —
                        // direkt in die App-Notification-Settings springen.
                        runCatching {
                            val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(
                                    AndroidSettings.EXTRA_APP_PACKAGE,
                                    context.packageName
                                )
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }.onFailure {
                            // Fallback: generische App-Detail-Settings.
                            val fallback = Intent(
                                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            context.startActivity(fallback)
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text(if (requestAttempted) "Zu den App-Einstellungen" else "Mitteilungen aktivieren")
                }
            }
        } else {
            Text(
                text = "Schreibt eine Probe-Mitteilung ins Center und feuert die echte System-Notification.",
                style = MaterialTheme.typography.bodySmall,
                color = HiUniColors.semantics.onSurfaceMuted
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onTestNotification,
                    enabled = !isTestRunning
                ) {
                    Text(if (isTestRunning) "Wird gesendet…" else "Test-Mitteilung senden")
                }
            }
        }
    }
}

@Composable
internal fun MailBiometricCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val availability = remember(context) {
        de.transio.hiuni.core.security.deviceBiometricAvailability(context)
    }
    val canEnable = availability.canUse
    var authError by remember { mutableStateOf<String?>(null) }
    // Beim Aktivieren erst Bio-Auth verlangen (wie andere Apps) — verhindert
    // dass ein Stranger am Gerät den Schutz still einrichtet ohne dass der
    // Besitzer es merkt. Beim Deaktivieren auch — sonst könnte jemand den
    // Schutz von der Mail entfernen ohne Auth.
    val gatedToggle = de.transio.hiuni.core.security.rememberMailUnlockPrompt(
        onSuccess = { onToggle(!enabled) },
        onError = { authError = it }
    )
    val subtitle = when {
        enabled -> "Mail-Tab fragt nach Fingerabdruck bzw. Gerätesperre"
        canEnable -> "Mail-Tab erst nach Fingerabdruck zeigen"
        availability == de.transio.hiuni.core.security.BiometricAvailability.NONE_ENROLLED ->
            "Richte zuerst Fingerabdruck oder PIN in den Geräteeinstellungen ein"
        else -> "Gerät unterstützt keine Biometrie"
    }
    SectionCard(
        icon = Icons.Outlined.Lock,
        title = "Mail mit Fingerabdruck schützen",
        subtitle = subtitle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (enabled) "Aktiv" else "Aus",
                style = MaterialTheme.typography.bodyMedium,
                color = HiUniColors.semantics.onSurfaceMuted
            )
            Switch(
                checked = enabled,
                enabled = canEnable || enabled,
                onCheckedChange = { gatedToggle() }
            )
        }
        authError?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/**
 * Mail-Push-Sektion (FCM / Tickle-Modell). Toggle „Mail-Push" plus zwei
 * Textfelder für Server-URL und API-Key. Der Toggle ist nur aktivierbar, wenn
 * beide Felder befüllt sind — sonst hätte die Server-Registrierung nichts, wohin.
 *
 * Die Textfelder halten lokalen Draft-State (seeded aus dem persistierten Wert),
 * damit das Tippen flüssig bleibt; persistiert wird bei jeder Änderung übers
 * ViewModel (analog zu den übrigen Settings). Der API-Key wird maskiert
 * dargestellt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MailPushCard(
    enabled: Boolean,
    serverUrl: String,
    apiKey: String,
    onToggle: (Boolean) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    var urlDraft by remember(serverUrl) { mutableStateOf(serverUrl) }
    var keyDraft by remember(apiKey) { mutableStateOf(apiKey) }
    val canEnable = urlDraft.isNotBlank() && keyDraft.isNotBlank()

    SectionCard(
        icon = if (enabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
        title = "Mail-Push",
        subtitle = if (enabled) {
            "Neue Mails werden per Push angestoßen und sofort abgerufen"
        } else {
            "Ein Server stößt den Mail-Abruf an, sobald neue Post da ist"
        }
    ) {
        Column {
            Text(
                text = "Ein selbst gehosteter Server schickt nur ein Signal — deine Zugangsdaten " +
                    "bleiben auf dem Gerät und werden nie übertragen. Der eigentliche Mail-Abruf " +
                    "läuft weiter lokal per IMAP.",
                style = MaterialTheme.typography.bodySmall,
                color = semantics.onSurfaceMuted
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = urlDraft,
                onValueChange = {
                    urlDraft = it
                    onServerUrlChange(it)
                },
                label = { Text("Server-URL") },
                placeholder = { Text("https://push.example.org") },
                singleLine = true,
                enabled = !enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = keyDraft,
                onValueChange = {
                    keyDraft = it
                    onApiKeyChange(it)
                },
                label = { Text("API-Key") },
                singleLine = true,
                enabled = !enabled,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (enabled) "Aktiv" else "Aus",
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantics.onSurfaceMuted
                )
                Switch(
                    checked = enabled,
                    enabled = canEnable || enabled,
                    onCheckedChange = { onToggle(it) }
                )
            }
        }
    }
}

@Composable
internal fun MailLocalDeleteCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    SectionCard(
        icon = Icons.Outlined.Delete,
        title = "Mails nur lokal löschen",
        subtitle = if (enabled) {
            "Mails bleiben auf dem Server, verschwinden nur aus der App"
        } else {
            "Mails werden vom Server endgültig entfernt (Standard)"
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (enabled) "Nur lokal" else "Server + lokal",
                style = MaterialTheme.typography.bodyMedium,
                color = HiUniColors.semantics.onSurfaceMuted
            )
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

internal fun formatReminderLabel(minutes: Int): String = when (minutes) {
    0 -> "Aus"
    in 1..59 -> "$minutes Min"
    60 -> "1 Std"
    else -> "${minutes / 60} Std"
}

/**
 * Kurz-Format für "wie lange ist das her", z.B. "vor 5 Min", "vor 3 Std",
 * "vor 2 Tg". Epoch `0` heißt: noch nie synchronisiert.
 */
internal fun formatRelativeAgo(epochMillis: Long): String {
    if (epochMillis <= 0L) return "nie"
    val now = Instant.now().toEpochMilli()
    val diff = Duration.ofMillis(now - epochMillis)
    if (diff.isNegative || diff.toMinutes() < 1) return "gerade eben"
    val minutes = diff.toMinutes()
    if (minutes < 60) return "vor $minutes Min"
    val hours = diff.toHours()
    if (hours < 24) return "vor $hours Std"
    val days = diff.toDays()
    return "vor $days Tg"
}
