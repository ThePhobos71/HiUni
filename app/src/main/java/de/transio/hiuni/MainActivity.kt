package de.transio.hiuni

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.design.HiUniTheme
import de.transio.hiuni.core.design.ThemeMode
import de.transio.hiuni.core.nfc.NfcScanController
import de.transio.hiuni.core.notifications.NotificationDeepLinkController
import de.transio.hiuni.core.notifications.NotificationPresenter
import de.transio.hiuni.core.startup.StartupRefresher
import de.transio.hiuni.feature.onboarding.ui.OnboardingScreen
import de.transio.hiuni.navigation.AppNavGraph
import androidx.compose.runtime.CompositionLocalProvider
import de.transio.hiuni.ui.responsive.AdaptiveScaffold
import de.transio.hiuni.ui.responsive.LocalWindowSizeClass
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var startupRefresher: StartupRefresher
    @Inject lateinit var nfcScanController: NfcScanController
    @Inject lateinit var notificationDeepLink: NotificationDeepLinkController
    @Inject lateinit var settingsDataStore: SettingsDataStore

    private val nfcAdapter by lazy { NfcAdapter.getDefaultAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startupRefresher.trigger()
        // Cold-Start via TECH_DISCOVERED-Intent: Tag direkt einspeisen + ein
        // Open-MensaCard-Event triggern, das der NavGraph aufgreift.
        handleNfcIntent(intent, unsolicited = true)
        // Cold-Start via Push-Center-Tap: extra parsen + NavGraph informieren.
        handleNotificationNavIntent(intent)
        setContent {
            val themeKey by settingsDataStore.themeMode
                .collectAsStateWithLifecycle(initialValue = "system")
            HiUniTheme(themeMode = ThemeMode.fromKey(themeKey)) {
                // initialValue = null verhindert ein Flackern, bei dem das Onboarding
                // kurz aufpoppt obwohl der User es schon abgeschlossen hat. Bis der
                // DataStore-Wert da ist, rendern wir absichtlich nichts — die echte
                // SplashScreen-API hält das Splash sichtbar (installSplashScreen oben).
                val onboardingCompleted by settingsDataStore.onboardingCompleted
                    .collectAsStateWithLifecycle(initialValue = null)

                // Crossfade glättet den Übergang Onboarding → Main; ohne diese
                // Animation flackert beim ersten Composition-Zyklus die Home-Liste
                // weil noch keine Flows ausgegeben haben. 600ms gibt den Streams
                // genug Zeit, sodass das Cross-Fade-Ziel schon Content trägt.
                androidx.compose.animation.Crossfade(
                    targetState = onboardingCompleted,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 600),
                    label = "onboardingToMain"
                ) { completed ->
                    when (completed) {
                        null -> {
                            // Splash bleibt aktiv — keine UI rendern um Flicker zu vermeiden.
                        }
                        false -> OnboardingScreen(
                            onCompleted = { /* recomposition pulls true */ }
                        )
                        true -> {
                            val navController = rememberNavController()
                            val windowSize = calculateWindowSizeClass(this@MainActivity)
                            CompositionLocalProvider(LocalWindowSizeClass provides windowSize) {
                                AdaptiveScaffold(
                                    navController = navController,
                                    windowSizeClass = windowSize
                                ) { padding ->
                                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                                        AppNavGraph(navController = navController)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Foreground-Dispatch nur aktivieren, solange das ViewModel einen Scan
        // anfordert. Sonst klauen wir jede NFC-Karte, die der User außerhalb
        // der Bezahl-Flow zufällig auflegt.
        lifecycleScope.launch {
            nfcScanController.scanning.collectLatest { active ->
                if (active) enableNfcDispatch() else disableNfcDispatch()
            }
        }
    }

    override fun onPause() {
        disableNfcDispatch()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (nfcScanController.scanning.value) enableNfcDispatch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent, unsolicited = !nfcScanController.scanning.value)
        handleNotificationNavIntent(intent)
    }

    /**
     * Wird die App durch Tap auf eine OS-Notification gestartet/wiederbelebt,
     * setzt [NotificationPresenter] einen Nav-Target-Extra. Hier konsumieren
     * (removeExtra), damit ein späterer onResume das Event nicht wiederholt.
     */
    private fun handleNotificationNavIntent(intent: Intent) {
        val target = intent.getStringExtra(NotificationPresenter.EXTRA_NAV_TARGET) ?: return
        if (target == NotificationPresenter.NAV_TARGET_NOTIFICATIONS_CENTER) {
            notificationDeepLink.signalOpenCenter()
            intent.removeExtra(NotificationPresenter.EXTRA_NAV_TARGET)
        }
    }

    private fun handleNfcIntent(intent: Intent, unsolicited: Boolean): Boolean {
        val action = intent.action ?: return false
        if (action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_NDEF_DISCOVERED) return false
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        tag ?: return false
        // Cold-Start oder externer TECH_DISCOVERED-Launch: unsolicited Pfad
        // setzt scanning + emit + signalisiert dem NavGraph "→ MensaCard".
        // Bei aktivem User-Scan (foreground dispatch + Button "Scannen"):
        // regulärer Pfad ohne Re-Navigation.
        if (unsolicited) nfcScanController.onUnsolicitedTag(tag)
        else nfcScanController.onTagReceived(tag)
        return true
    }

    private fun enableNfcDispatch() {
        val adapter = nfcAdapter ?: return
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        // Tech-Filter: nur ISO-14443-4 Karten (DESfire fällt drunter). Verhindert
        // dass das System anderen Techs den Vortritt lässt und IsoDep blockiert
        // ("Only one TagTechnology can be connected at a time").
        val techLists = arrayOf(arrayOf(IsoDep::class.java.name))
        adapter.enableForegroundDispatch(this, pendingIntent, null, techLists)
    }

    private fun disableNfcDispatch() {
        val adapter = nfcAdapter ?: return
        runCatching { adapter.disableForegroundDispatch(this) }
    }
}
