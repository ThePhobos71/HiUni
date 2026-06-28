package de.transio.hiuni.feature.onboarding.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.transio.hiuni.core.auth.CasLoginContract
import de.transio.hiuni.core.design.HiUniColors
import de.transio.hiuni.core.design.HiUniRadii
import de.transio.hiuni.core.security.BiometricAvailability
import de.transio.hiuni.core.security.deviceBiometricAvailability
import de.transio.hiuni.core.security.rememberMailUnlockPrompt
import de.transio.hiuni.feature.onboarding.OnboardingUiState
import de.transio.hiuni.feature.onboarding.OnboardingViewModel
import kotlinx.coroutines.delay

/**
 * First-Launch-Onboarding-Pager. 5 Slides: Begrüßung, Features, CAS-Login (mit
 * In-Slide-Sync-Status nach erfolgreichem Login), Bio-Schutz (Opt-in für
 * BiometricPrompt vor Mail-Liste) und Notifications-Permission. Wird in
 * [de.transio.hiuni.MainActivity] vor dem AdaptiveScaffold gerendert, solange
 * `settingsDataStore.onboardingCompleted` `false` ist. Nach Klick auf "Loslegen"
 * → [OnboardingViewModel.markCompleted] setzt das Flag und `onCompleted`
 * triggert die Recomposition zurück in die App.
 */
@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    // Notifications-Permission beim Start lesen. API < 33: kein Prompt nötig,
    // wir gehen direkt davon aus, dass "erlaubt".
    val initialNotificationsGranted = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    LaunchedEffect(initialNotificationsGranted) {
        viewModel.onNotificationPermissionChanged(initialNotificationsGranted)
    }

    val pagerState = rememberPagerState(pageCount = { OnboardingUiState.SLIDE_COUNT })

    // VM-State → Pager-Position. Wenn das VM nextSlide()/goTo() ruft, animiert
    // der Pager dorthin. Wenn der User swiped, bekommt das VM den neuen Index
    // (siehe LaunchedEffect unten).
    LaunchedEffect(state.currentSlide) {
        if (pagerState.currentPage != state.currentSlide) {
            pagerState.animateScrollToPage(state.currentSlide)
        }
    }
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != state.currentSlide) {
            viewModel.goTo(pagerState.currentPage)
        }
    }

    // 15s-Timeout für den initialen LSF-Sync-Hint auf der Login-Slide. Sobald
    // der User authenticated ist, starten wir den Watchdog: hat der Worker
    // nach 15s noch nicht den Timestamp gesetzt, drehen wir die UI auf den
    // "läuft im Hintergrund weiter, du kannst schon mal weiter"-Hinweis um.
    // delay() statt withTimeout, weil der Sync nicht abgebrochen werden soll —
    // nur die UI-Wartezeit endet.
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated && !state.initialLsfSyncDone) {
            delay(15_000)
            viewModel.markInitialSyncTimedOut()
        }
    }

    val loginLauncher = rememberLauncherForActivityResult(CasLoginContract()) { success ->
        // CasSession.onLoginSuccess wurde von WebLoginActivity bereits gerufen;
        // refreshState() ist defensiv für den Fall, dass der StateFlow noch
        // nicht emitted hat, wenn unser Activity-Result zurückkommt.
        if (success) viewModel.refreshAuthState()
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onNotificationPermissionChanged(granted)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        OnboardingTopBar(
            currentSlide = state.currentSlide,
            onSkip = {
                viewModel.markCompleted()
                onCompleted()
            }
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) { page ->
            when (page) {
                OnboardingUiState.SLIDE_WELCOME -> SlideWelcome()
                OnboardingUiState.SLIDE_FEATURES -> SlideFeatures()
                OnboardingUiState.SLIDE_LOGIN -> SlideLogin(
                    isAuthenticated = state.isAuthenticated,
                    firstName = state.profile.firstName,
                    initialSyncDone = state.initialLsfSyncDone,
                    initialSyncTimedOut = state.initialLsfSyncTimedOut
                )
                OnboardingUiState.SLIDE_BIOMETRIC -> SlideBiometric(
                    onActivated = {
                        viewModel.setMailRequiresBiometric(true)
                        viewModel.nextSlide()
                    },
                    onSkip = viewModel::nextSlide
                )
                OnboardingUiState.SLIDE_NOTIFICATIONS -> SlideNotifications(
                    hasPermission = state.hasNotificationsPermission
                )
            }
        }

        SlideIndicator(
            count = OnboardingUiState.SLIDE_COUNT,
            current = state.currentSlide,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )

        // Bio-Schutz-Slide hat ihre eigenen In-Slide-Buttons (Aktivieren /
        // Später entscheiden), damit "Aktivieren" direkt den BiometricPrompt
        // öffnen kann. Hier wird der globale Action-Button daher ausgeblendet;
        // wir reservieren aber per Box den gleichen vertikalen Platz, damit der
        // Pager-Indicator beim Swipen nicht hüpft.
        if (state.currentSlide == OnboardingUiState.SLIDE_BIOMETRIC) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .heightIn(min = 56.dp)
            )
        } else {
            OnboardingActionButton(
                slide = state.currentSlide,
                isAuthenticated = state.isAuthenticated,
                hasNotificationsPermission = state.hasNotificationsPermission,
                onNext = viewModel::nextSlide,
                onStartLogin = { loginLauncher.launch(Unit) },
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.onNotificationPermissionChanged(true)
                    }
                },
                onFinish = {
                    viewModel.markCompleted()
                    onCompleted()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun OnboardingTopBar(
    currentSlide: Int,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.weight(1f))
        // Auf der letzten Slide kein Skip mehr — der User soll bewusst auf
        // "Loslegen" tappen, damit das Permission-CTA nicht versehentlich
        // übersprungen wird.
        AnimatedVisibility(
            visible = currentSlide < OnboardingUiState.SLIDE_COUNT - 1,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            TextButton(onClick = onSkip) {
                Text(
                    text = "Überspringen",
                    color = HiUniColors.semantics.onSurfaceMuted,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SlideIndicator(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val active = index == current
            Box(
                modifier = Modifier
                    .size(width = if (active) 24.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) colors.primary
                        else colors.primary.copy(alpha = 0.22f)
                    )
            )
        }
    }
}

@Composable
private fun OnboardingActionButton(
    slide: Int,
    isAuthenticated: Boolean,
    hasNotificationsPermission: Boolean,
    onNext: () -> Unit,
    onStartLogin: () -> Unit,
    onRequestNotifications: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val (label, action) = when (slide) {
        OnboardingUiState.SLIDE_WELCOME -> "Weiter" to onNext
        OnboardingUiState.SLIDE_FEATURES -> "Weiter" to onNext
        OnboardingUiState.SLIDE_LOGIN -> if (isAuthenticated) {
            "Weiter" to onNext
        } else {
            "Mit RZ-Kennung einloggen" to onStartLogin
        }
        OnboardingUiState.SLIDE_NOTIFICATIONS -> if (hasNotificationsPermission) {
            "Loslegen" to onFinish
        } else {
            "Mitteilungen erlauben" to onRequestNotifications
        }
        else -> "Weiter" to onNext
    }

    Button(
        onClick = action,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(HiUniRadii.pill),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── Slides ─────────────────────────────────────────────────────────────────

@Composable
private fun SlideWelcome() {
    val colors = MaterialTheme.colorScheme
    val titleText = "Hallo!"
    val bodyText = "Willkommen bei HiUni — deine App rund ums Studium an der Uni Hildesheim."

    // Sobald die Welcome-Slide einmal komplett ausgetippt wurde, bleibt der Voll-Text
    // — Re-Entries via Swipe oder Recomposition zeigen nicht erneut die Tipp-Animation.
    var skipTyping by rememberSaveable { mutableStateOf(false) }

    val typedTitle = rememberTypewriter(titleText, msPerChar = 110, enabled = !skipTyping)
    val titleDone = typedTitle.length == titleText.length
    val typedBody = rememberTypewriter(
        target = if (titleDone || skipTyping) bodyText else "",
        msPerChar = 28,
        enabled = !skipTyping
    )
    val bodyDone = typedBody.length == bodyText.length

    LaunchedEffect(bodyDone) { if (bodyDone) skipTyping = true }

    val cursorOnTitle = rememberBlinkingCursor(active = !titleDone && !skipTyping)
    val cursorOnBody = rememberBlinkingCursor(active = titleDone && !bodyDone && !skipTyping)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                // Ungeduldig? Tap überspringt die Tipp-Animation.
                onClick = { skipTyping = true }
            )
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = colors.primaryContainer,
            modifier = Modifier.size(128.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.School,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = typedTitle + cursorChar(cursorOnTitle),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = typedBody + cursorChar(cursorOnBody),
            style = MaterialTheme.typography.bodyLarge,
            color = HiUniColors.semantics.onSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Tippt [target] Zeichen für Zeichen in [msPerChar]-Intervallen. Bei `enabled=false`
 * (z. B. nach Erst-Visit) wird sofort der volle Text zurückgegeben — keine Animation.
 * Recomposed sich auf jedes Zeichen, ist also nicht für lange Strings gedacht.
 */
@Composable
private fun rememberTypewriter(
    target: String,
    msPerChar: Long,
    enabled: Boolean
): String {
    var typed by remember(target) {
        mutableStateOf(if (!enabled || target.isEmpty()) target else "")
    }
    LaunchedEffect(target, enabled) {
        if (!enabled) {
            typed = target
            return@LaunchedEffect
        }
        // Start jeweils bei aktuellem Stand, damit Re-Composition mit veränderten
        // Voraussetzungen nicht von vorn anfängt.
        for (i in (typed.length + 1)..target.length) {
            typed = target.substring(0, i)
            delay(msPerChar)
        }
    }
    return typed
}

/**
 * Klassische blinkende Caret-Animation. `active=false` versteckt den Cursor sofort.
 */
@Composable
private fun rememberBlinkingCursor(active: Boolean): Boolean {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(active) {
        if (!active) {
            on = false
            return@LaunchedEffect
        }
        while (true) {
            on = true
            delay(500)
            on = false
            delay(500)
        }
    }
    return on && active
}

/** Schmaler Vertikal-Strich als Caret. Leere String wenn Cursor aus, damit Layout stabil bleibt. */
private fun cursorChar(visible: Boolean): String = if (visible) "▍" else ""

@Composable
private fun SlideFeatures() {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val items = remember {
        listOf(
            FeatureSpec(Icons.Outlined.CalendarMonth, "Stundenplan", colors.primary),
            FeatureSpec(Icons.Outlined.RestaurantMenu, "Mensa", semantics.amber),
            FeatureSpec(Icons.Outlined.Apartment, "Bib-Räume", semantics.purple),
            FeatureSpec(Icons.Outlined.Mail, "Uni-Mails", colors.primary),
            FeatureSpec(Icons.Outlined.Checklist, "Aufgaben", semantics.green),
            FeatureSpec(Icons.Outlined.DirectionsRun, "Hochschulsport", semantics.amber)
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Was du hier findest",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Alle Uni-Sachen an einem Ort — kein Tab-Wechsel mehr zwischen LSF, Webmail und Mensa-Plan.",
            style = MaterialTheme.typography.bodyLarge,
            color = semantics.onSurfaceMuted
        )
        Spacer(Modifier.height(24.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items) { feature ->
                FeatureCard(feature)
            }
        }
    }
}

private data class FeatureSpec(
    val icon: ImageVector,
    val label: String,
    val accent: Color
)

@Composable
private fun FeatureCard(spec: FeatureSpec) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(HiUniRadii.card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(HiUniRadii.tile))
                    .background(spec.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = spec.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = spec.label,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SlideLogin(
    isAuthenticated: Boolean,
    firstName: String?,
    initialSyncDone: Boolean,
    initialSyncTimedOut: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = if (isAuthenticated) semantics.greenSurface else colors.primaryContainer,
            modifier = Modifier.size(112.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isAuthenticated) Icons.Outlined.CheckCircle else Icons.Outlined.VpnKey,
                    contentDescription = null,
                    tint = if (isAuthenticated) semantics.green else colors.primary,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = if (isAuthenticated) "Schön, dass du da bist!" else "Mit RZ-Kennung einloggen",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        if (isAuthenticated) {
            val who = firstName?.takeIf { it.isNotBlank() }
            Text(
                text = if (who != null) "Angemeldet als $who." else "Angemeldet.",
                style = MaterialTheme.typography.bodyLarge,
                color = semantics.onSurfaceMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            // Sync-Status-Pille direkt unter der Begrüßung — wechselt von Progress
            // ("wir holen…") über Done ("ist da!") bis Timeout ("läuft weiter").
            InitialSyncStatusCard(
                done = initialSyncDone,
                timedOut = initialSyncTimedOut
            )
        } else {
            Text(
                text = "Damit holen wir dir deinen Stundenplan, deine Kurse und deine Uni-Mails direkt aus dem LSF und Webmail. Deine Zugangsdaten bleiben auf deinem Gerät.",
                style = MaterialTheme.typography.bodyLarge,
                color = semantics.onSurfaceMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Drei Zustände in einer Surface, damit die Position über Re-Composes stabil
 * bleibt:
 *  - done=false, timedOut=false → CircularProgressIndicator + "Wir holen…"
 *  - done=true → grünes Häkchen + "Kurse und Stundenplan sind da."
 *  - done=false, timedOut=true → Cloud-Icon + "Läuft im Hintergrund weiter."
 *
 * Die Surface ist bewusst auf [colors.surface] gestyled (nicht primaryContainer),
 * damit sie sich vom Hero-Circle abgrenzt aber nicht in Konkurrenz zur Haupt-CTA
 * "Weiter" geht — der Action-Button bleibt der primäre Fokus.
 */
@Composable
private fun InitialSyncStatusCard(
    done: Boolean,
    timedOut: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics

    val (icon, iconTint, headline, body) = when {
        done -> Quadruple(
            Icons.Outlined.CheckCircle,
            semantics.green,
            "Deine Kurse sind da!",
            "Stundenplan und Mails sind bereit — du kannst jetzt weitermachen."
        )
        timedOut -> Quadruple(
            Icons.Outlined.CloudSync,
            colors.primary,
            "Sync läuft im Hintergrund",
            "Hat länger gedauert als gedacht — geht aber automatisch weiter. Du kannst schon mal weiter."
        )
        else -> Quadruple(
            Icons.Outlined.CloudSync,
            colors.primary,
            "Wir holen deine Kurse",
            "Stundenplan und Mails einen Moment — du musst hier nichts tun."
        )
    }

    Surface(
        shape = RoundedCornerShape(HiUniRadii.card),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!done && !timedOut) {
                    // Echte Progress-Animation, solange wir aktiv warten — ein
                    // statisches Icon würde den "es passiert was"-Hinweis nicht
                    // tragen.
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = iconTint
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

/**
 * Bio-Schutz-Slide (Position 4 von 5). User entscheidet hier, ob die Mail-Liste
 * beim Öffnen einen BiometricPrompt vorschaltet.
 *  - "Aktivieren" → System-Prompt; bei Success [onActivated] (= Setting + next).
 *  - "Später entscheiden" → [onSkip] ohne Setting-Change.
 *
 * Wenn das Gerät keine Biometrie enrolled hat (= [BiometricAvailability.NONE_ENROLLED])
 * zeigen wir nur den Hinweis + Skip-Button. NO_HARDWARE/HARDWARE_UNAVAILABLE
 * würden den Prompt-Versuch sowieso sofort wegabbrechen — wir blenden den
 * Aktivieren-Button daher dort auch aus, um keinen Fake-Tap-Effekt zu erzeugen.
 */
@Composable
private fun SlideBiometric(
    onActivated: () -> Unit,
    onSkip: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val context = LocalContext.current
    val availability = remember(context) { deviceBiometricAvailability(context) }

    val triggerPrompt = rememberMailUnlockPrompt(
        onSuccess = onActivated,
        onError = { /* Cancel/Fehler → einfach im Slide bleiben, User kann erneut tippen */ }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = colors.primaryContainer,
            modifier = Modifier.size(112.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Mail privat halten?",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Verwende deinen Fingerabdruck oder die Gerätesperre, damit nur du deine Uni-Mails lesen kannst.",
            style = MaterialTheme.typography.bodyLarge,
            color = semantics.onSurfaceMuted,
            textAlign = TextAlign.Center
        )

        if (availability == BiometricAvailability.NONE_ENROLLED) {
            Spacer(Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(HiUniRadii.card),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Richte zuerst Fingerabdruck oder PIN in den Geräteeinstellungen ein — dann kannst du das später unter Einstellungen → Mail aktivieren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Primary "Aktivieren" nur sichtbar, wenn das Gerät theoretisch
        // authentifizieren kann. Bei NONE_ENROLLED hätte der Prompt sofort
        // gefehlert → wir verstecken den Button ganz.
        if (availability.canUse) {
            Button(
                onClick = triggerPrompt,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(HiUniRadii.pill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                )
            ) {
                Text(
                    text = "Aktivieren",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Später entscheiden",
                color = semantics.onSurfaceMuted,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SlideNotifications(hasPermission: Boolean) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = if (hasPermission) semantics.greenSurface else colors.primaryContainer,
            modifier = Modifier.size(112.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (hasPermission) Icons.Outlined.CheckCircle else Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    tint = if (hasPermission) semantics.green else colors.primary,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = if (hasPermission) "Mitteilungen sind erlaubt" else "Erinnerungen erlauben?",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (hasPermission) {
                "Du kannst die Erinnerungen jederzeit in den Einstellungen wieder ausschalten."
            } else {
                "Damit du an Klausuren, Termine und neue Mails erinnert wirst. Du kannst das jederzeit in den Einstellungen ändern."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = semantics.onSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}
