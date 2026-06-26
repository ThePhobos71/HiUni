package de.transio.hiuni.feature.email.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.feature.email.EmailComposeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailComposeScreen(
    onBack: () -> Unit,
    viewModel: EmailComposeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    // Snackbar für Erfolg & Fehler. Bei Erfolg schließen wir den Screen — der
    // ViewModel-State ist dann eh leer, der User soll wieder in der Inbox landen.
    LaunchedEffect(state.sentMessage) {
        val msg = state.sentMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeMessage()
        onBack()
    }
    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeMessage()
    }

    val attemptBack: () -> Unit = {
        if (state.isDirty && !state.isSending) showDiscardDialog = true else onBack()
    }
    BackHandler(enabled = !state.isSending) { attemptBack() }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Entwurf verwerfen?") },
            text = { Text("Dieser Entwurf wird nicht gespeichert.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) { Text("Verwerfen") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Weiter bearbeiten") }
            }
        )
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = attemptBack, enabled = !state.isSending) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Zurück",
                            tint = colors.onSurface
                        )
                    }
                    Text(
                        text = "Neue Mail",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.onSurface
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    if (state.isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = colors.primary
                        )
                    } else {
                        IconButton(onClick = viewModel::send, enabled = state.canSend) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = "Senden",
                                tint = if (state.canSend) colors.primary
                                else semantics.onSurfaceMuted
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))

            if (!state.hasCredentials) {
                Surface(
                    color = semantics.surfaceAlt,
                    shape = RoundedCornerShape(HiUniRadii.card),
                    modifier = Modifier.fillMaxWidth().padding(22.dp)
                ) {
                    Text(
                        text = "Keine Zugangsdaten — bitte in Settings einloggen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        modifier = Modifier.padding(18.dp)
                    )
                }
                return@Column
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.to,
                    onValueChange = viewModel::updateTo,
                    label = { Text("An") },
                    placeholder = { Text("max@uni-hildesheim.de, …") },
                    singleLine = true,
                    enabled = !state.isSending,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.showCcBcc) {
                    OutlinedTextField(
                        value = state.cc,
                        onValueChange = viewModel::updateCc,
                        label = { Text("CC") },
                        singleLine = true,
                        enabled = !state.isSending,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.bcc,
                        onValueChange = viewModel::updateBcc,
                        label = { Text("BCC") },
                        singleLine = true,
                        enabled = !state.isSending,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TextButton(
                        onClick = viewModel::toggleCcBcc,
                        enabled = !state.isSending,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = "CC / BCC anzeigen",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary
                        )
                    }
                }

                OutlinedTextField(
                    value = state.subject,
                    onValueChange = viewModel::updateSubject,
                    label = { Text("Betreff") },
                    singleLine = true,
                    enabled = !state.isSending,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.body,
                    onValueChange = viewModel::updateBody,
                    label = { Text("Nachricht") },
                    enabled = !state.isSending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp)
                )

                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Versand über mail.uni-hildesheim.de · STARTTLS",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}
