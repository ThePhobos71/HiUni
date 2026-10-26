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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
 * de-registriert, das Prozess-Ende räumt ihn ab). Würde jedes ViewModel selbst
 * einen Callback registrieren, sammelten sich Leaks und redundante System-Hooks an.
 *
 * "Online" heißt hier: es gibt ein Default-Netz MIT [NetworkCapabilities
 * .NET_CAPABILITY_VALIDATED], das für die App nicht blockiert ist. Ein reines
 * NET_CAPABILITY_INTERNET reicht nicht: ein Captive-Portal- oder frisch
 * verbundenes WLAN ohne echten Durchgriff würde sonst fälschlich als online zählen.
 *
 * Der Status wird ausschließlich aus den Callback-ARGUMENTEN gebaut, nicht per
 * synchroner Abfrage (`activeNetwork`, `getNetworkCapabilities`). Laut Android-Doku
 * können diese Abfragen innerhalb eines Callbacks veraltet oder null sein, und
 * `activeNetwork` liefert null, solange das Netz für die App blockiert ist (Doze,
 * App-Standby, Datensparmodus im Hintergrund). Kam die App zurück, meldete Android
 * nur `onBlockedStatusChanged(false)`, das vorher nicht behandelt wurde: der
 * Offline-Banner blieb hängen, bis zufällig ein weiterer Netzwechsel kam.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Zustand des vom Default-Callback getrackten Netzes. [network] null = kein
     * Default-Netz. Alle Felder werden nur aus Callback-Argumenten gesetzt.
     */
    private data class DefaultNetworkState(
        val network: Network?,
        val validatedInternet: Boolean,
        val blocked: Boolean,
    ) {
        val isOnline: Boolean get() = network != null && validatedInternet && !blocked
    }

    // WhileSubscribed hätte den Callback bei 0 Subscribern abgemeldet. Für einen
    // prozessweiten Netz-Status wollen wir ihn dauerhaft laufen lassen, damit der
    // erste Consumer sofort den korrekten Wert sieht statt "online (Default)".
    val isOnline: StateFlow<Boolean> = run {
        // Startwert per synchroner Abfrage: außerhalb eines Callbacks ist das
        // zulässig. Direkt nach der Registrierung liefert Android ohnehin
        // onAvailable + onCapabilitiesChanged + onBlockedStatusChanged für das
        // aktuelle Default-Netz nach.
        val state = MutableStateFlow(initialState())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Neues Default-Netz. Ab Android O folgt garantiert sofort
                // onCapabilitiesChanged; bis dahin gilt es als nicht validiert.
                state.update { DefaultNetworkState(network, validatedInternet = false, blocked = false) }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                state.update {
                    it.copy(network = network, validatedInternet = networkCapabilities.isValidatedInternet())
                }
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                state.update { it.copy(network = network, blocked = blocked) }
            }

            override fun onLost(network: Network) {
                // Nur das aktuell getrackte Netz zählt. Ein verspätetes onLost des
                // alten Netzes nach einem Wechsel (WLAN -> Mobilfunk) wird ignoriert.
                state.update { if (it.network == network) it.copy(network = null) else it }
            }

            override fun onUnavailable() {
                state.update { it.copy(network = null) }
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        // onCapabilitiesChanged feuert bei Netzwechseln (z.B. WLAN<->Mobilfunk-
        // Handoff) mehrfach in kurzer Folge, dabei kurz auch ohne
        // NET_CAPABILITY_VALIDATED. Der Debounce verhindert, dass Banner/UI bei jedem
        // Zwischenzustand auf-/abblitzen. stateIn bindet den Flow an den
        // ApplicationScope; Eagerly, damit der Wert ab Prozess-Start gepflegt wird.
        state
            .map { it.isOnline }
            .distinctUntilChanged()
            .debounce(DEBOUNCE_MILLIS)
            .stateIn(scope, SharingStarted.Eagerly, state.value.isOnline)
    }

    private fun initialState(): DefaultNetworkState {
        val network = connectivityManager.activeNetwork
            ?: return DefaultNetworkState(network = null, validatedInternet = false, blocked = false)
        val caps = connectivityManager.getNetworkCapabilities(network)
        return DefaultNetworkState(
            network = network,
            validatedInternet = caps?.isValidatedInternet() == true,
            blocked = false,
        )
    }

    private fun NetworkCapabilities.isValidatedInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
