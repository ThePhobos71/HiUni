package de.transio.hiuni

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.transio.hiuni.core.design.HiUniTheme
import de.transio.hiuni.core.nfc.NfcScanController
import de.transio.hiuni.core.startup.StartupRefresher
import de.transio.hiuni.navigation.AppNavGraph
import de.transio.hiuni.ui.responsive.AdaptiveScaffold
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var startupRefresher: StartupRefresher
    @Inject lateinit var nfcScanController: NfcScanController

    private val nfcAdapter by lazy { NfcAdapter.getDefaultAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startupRefresher.trigger()
        setContent {
            HiUniTheme {
                val navController = rememberNavController()
                val windowSize = calculateWindowSizeClass(this)
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
        val action = intent.action ?: return
        if (action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_NDEF_DISCOVERED) return
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        tag?.let { nfcScanController.onTagReceived(it) }
    }

    private fun enableNfcDispatch() {
        val adapter = nfcAdapter ?: return
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        adapter.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    private fun disableNfcDispatch() {
        val adapter = nfcAdapter ?: return
        runCatching { adapter.disableForegroundDispatch(this) }
    }
}
