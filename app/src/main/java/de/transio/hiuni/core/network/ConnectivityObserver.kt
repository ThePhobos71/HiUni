package de.transio.hiuni.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import de.transio.hiuni.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Kurze Netzwechsel (z.B. WLAN/Mobilfunk-Handoff) sollen nicht als Flackern im UI ankommen. */
private const val DEBOUNCE_MILLIS = 1_500L

/**
 * App-weite Netz-Erkennung. Kapselt den [ConnectivityManager.NetworkCallback] und
 * projiziert ihn auf ein einzelnes [StateFlow]<Boolean> [isOnline], das jeder
 * Consumer (Banner, ViewModels) teilen kann.
 *
 * Bewusst als @Singleton an den [ApplicationScope] gebunden: Der Default-Callback
 * wird genau einmal für die gesamte Prozess-Lebensdauer registriert (nie
 * de-registriert — das Prozess-Ende räumt ihn ab). Würde jedes ViewModel selbst
 * einen Callback registrieren, sammelten sich Leaks und redundante System-Hooks an.
 *
 * "Online" heißt hier: es gibt ein Default-Netz MIT [NetworkCapabilities
 * .NET_CAPABILITY_VALIDATED]. Ein reines NET_CAPABILITY_INTERNET reicht nicht —
 * ein Captive-Portal- oder frisch verbundenes WLAN ohne echten Durchgriff würde
 * sonst fälschlich als online zählen und die Stale-/Offline-Kennzeichnung
 * unterdrücken.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // WhileSubscribed hätte den Callback bei 0 Subscribern abgemeldet — für einen
    // prozessweiten Netz-Status wollen wir ihn dauerhaft laufen lassen, damit der
    // erste Consumer sofort den korrekten Wert sieht statt "online (Default)".
    val isOnline: StateFlow<Boolean> = run {
        // onCapabilitiesChanged feuert bei Netzwechseln (z.B. WLAN<->Mobilfunk-
        // Handoff) mehrfach in kurzer Folge, dabei kurz auch mit fehlendem
        // NET_CAPABILITY_VALIDATED. Ohne Debounce würde das Banner/UI bei jedem
        // dieser Zwischenzustände auf-/abblitzen ("flappen").
        val rawFlow = MutableStateFlow(currentlyOnline())
        val callback = object : ConnectivityManager.NetworkCallback() {
            // Wir tracken nicht ein einzelnes Network-Objekt, sondern fragen bei
            // jedem Ereignis den Gesamt-Status neu ab. So bleibt der Wert korrekt,
            // wenn mehrere Netze (WLAN + Mobilfunk) gleichzeitig auf-/abgehen.
            override fun onAvailable(network: Network) {
                rawFlow.value = currentlyOnline()
            }

            override fun onLost(network: Network) {
                rawFlow.value = currentlyOnline()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                rawFlow.value = currentlyOnline()
            }

            override fun onUnavailable() {
                rawFlow.value = currentlyOnline()
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        // stateIn bindet den Flow an den ApplicationScope; Eagerly, damit der Wert
        // ab Prozess-Start korrekt gepflegt wird, unabhängig von Subscribern.
        rawFlow
            .debounce(DEBOUNCE_MILLIS)
            .stateIn(scope, SharingStarted.Eagerly, rawFlow.value)
    }

    /**
     * Fragt den aktuellen Default-Netz-Status synchron ab. Genutzt für den
     * Initialwert und bei jedem Callback-Ereignis.
     */
    private fun currentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
