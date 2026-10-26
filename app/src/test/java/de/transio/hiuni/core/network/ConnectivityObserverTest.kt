package de.transio.hiuni.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Robolectric-Tests für [ConnectivityObserver]. Getestet wird die Ableitung des
 * [ConnectivityObserver.isOnline]-Flows aus dem [ConnectivityManager]-Zustand
 * sowie die Reaktion auf die registrierten [ConnectivityManager.NetworkCallback]-
 * Events (über [ShadowConnectivityManager.getNetworkCallbacks]).
 *
 * Kern-Invariante: "online" gilt NUR bei einem Default-Netz mit INTERNET **und**
 * VALIDATED — ein reines TRANSPORT-Netz (Robolectric-Default: Mobile ohne
 * Capabilities) zählt als offline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ConnectivityObserverTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var shadow: ShadowConnectivityManager

    // Eigener Scope für den Observer statt der TestScope: der stateIn-Collector läuft
    // Eagerly und endet nie, an eine runTest-Scope gehängt würde er als „unfinished
    // coroutine" den Test failen lassen. Der UnconfinedTestDispatcher lässt die
    // MutableStateFlow -> stateIn-Weitergabe synchron laufen; den Debounce spulen wir
    // über den gemeinsamen [scheduler] per [settle] vor.
    private val scheduler = TestCoroutineScheduler()
    private val observerScope = CoroutineScope(UnconfinedTestDispatcher(scheduler))

    /** Virtuelle Zeit über den Debounce hinaus vorspulen, damit [isOnline] nachzieht. */
    private fun settle() {
        scheduler.advanceTimeBy(5_000)
        scheduler.runCurrent()
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadow = shadowOf(connectivityManager)
    }

    @After
    fun tearDown() {
        observerScope.cancel()
    }

    /** Baut Capabilities mit INTERNET + VALIDATED (= "echt online"). */
    private fun validatedCaps(): NetworkCapabilities {
        val caps = ShadowNetworkCapabilities.newInstance()
        val s = shadowOf(caps)
        s.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        s.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return caps
    }

    /** Versieht das aktuell aktive Default-Netz mit "echt online"-Capabilities. */
    private fun makeActiveNetworkOnline() {
        val active = connectivityManager.activeNetwork!!
        shadow.setNetworkCapabilities(active, validatedCaps())
    }

    private fun newObserver() = ConnectivityObserver(context, observerScope)

    @Test
    fun `Default-Robolectric-Netz ohne VALIDATED zaehlt als offline`() {
        val observer = newObserver()
        // Robolectric-Default: Mobile-Netz aktiv, aber ohne INTERNET/VALIDATED-Caps.
        assertFalse(observer.isOnline.value)
    }

    @Test
    fun `validiertes Default-Netz beim Start zaehlt als online`() {
        makeActiveNetworkOnline()
        val observer = newObserver()
        assertTrue(observer.isOnline.value)
    }

    @Test
    fun `kein aktives Default-Netz zaehlt als offline`() {
        shadow.setDefaultNetworkActive(false) // getActiveNetwork() -> null
        val observer = newObserver()
        assertFalse(observer.isOnline.value)
    }

    @Test
    fun `Observer registriert einen Default-Netz-Callback`() {
        newObserver()
        assertTrue(shadow.networkCallbacks.isNotEmpty())
    }

    @Test
    fun `onAvailable nach Validierung schaltet auf online um`() {
        val observer = newObserver()
        assertFalse(observer.isOnline.value)

        // Netz wird "echt" verfügbar → Observer fragt currentlyOnline() neu ab.
        makeActiveNetworkOnline()
        val active = connectivityManager.activeNetwork!!
        shadow.networkCallbacks.forEach {
            it.onAvailable(active)
            it.onCapabilitiesChanged(active, validatedCaps())
        }
        settle()
        assertTrue(observer.isOnline.value)
    }

    @Test
    fun `onLost schaltet zurueck auf offline`() {
        makeActiveNetworkOnline()
        val active = connectivityManager.activeNetwork!!
        val observer = newObserver()
        assertTrue(observer.isOnline.value)

        // Netz verschwindet: kein aktives Default-Netz mehr. Der Observer wertet den
        // Gesamt-Status via currentlyOnline() neu aus; das übergebene Network-Objekt
        // im Callback ist dafür irrelevant.
        shadow.setDefaultNetworkActive(false)
        shadow.networkCallbacks.forEach { it.onLost(active) }
        settle()
        assertFalse(observer.isOnline.value)
    }

    @Test
    fun `Capabilities aus dem Callback zaehlen, nicht die synchrone Abfrage`() {
        makeActiveNetworkOnline()
        val active = connectivityManager.activeNetwork!!
        val observer = newObserver()

        // Laut Doku dürfen synchrone ConnectivityManager-Abfragen im Callback veraltet
        // oder null sein. Simuliert: activeNetwork liefert null, der Callback selbst
        // meldet aber ein validiertes Netz.
        shadow.setDefaultNetworkActive(false)
        shadow.networkCallbacks.forEach { it.onCapabilitiesChanged(active, validatedCaps()) }
        settle()
        assertTrue(observer.isOnline.value)
    }

    @Test
    fun `blockiert im Hintergrund ist offline, Entsperren macht wieder online`() {
        makeActiveNetworkOnline()
        val active = connectivityManager.activeNetwork!!
        val observer = newObserver()

        // App im Hintergrund vom Netz getrennt (Doze/App-Standby): activeNetwork
        // liefert dann null, Android meldet onBlockedStatusChanged(true).
        shadow.setDefaultNetworkActive(false)
        shadow.networkCallbacks.forEach {
            it.onBlockedStatusChanged(active, true)
            it.onCapabilitiesChanged(active, validatedCaps())
        }
        settle()
        assertFalse(observer.isOnline.value)

        // Zurück im Vordergrund: Android meldet NUR das Entsperren, kein neues
        // onCapabilitiesChanged. Das muss reichen, um wieder online zu sein.
        shadow.setDefaultNetworkActive(true)
        shadow.networkCallbacks.forEach { it.onBlockedStatusChanged(active, false) }
        settle()
        assertTrue(observer.isOnline.value)
    }

    @Test
    fun `onLost eines alten Netzes nach Wechsel auf neues bleibt online`() {
        makeActiveNetworkOnline()
        val oldNetwork = connectivityManager.activeNetwork!!
        val observer = newObserver()
        val newNetwork = ShadowNetwork.newInstance(4711)

        // WLAN -> Mobilfunk: neues Default-Netz wird gemeldet, das onLost des alten
        // kann danach noch eintreffen und darf den Status nicht umwerfen.
        shadow.networkCallbacks.forEach {
            it.onAvailable(newNetwork)
            it.onCapabilitiesChanged(newNetwork, validatedCaps())
            it.onLost(oldNetwork)
        }
        settle()
        assertTrue(observer.isOnline.value)
    }
}
