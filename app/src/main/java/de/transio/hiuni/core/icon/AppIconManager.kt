package de.transio.hiuni.core.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import de.transio.hiuni.core.datastore.SettingsDataStore
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Switcht das App-Launcher-Icon zur Laufzeit, indem genau einer der im
 * Manifest deklarierten activity-alias-Einträge enabled und alle anderen
 * disabled werden (iOS/WhatsApp/Telegram-Premium-Pattern). MainActivity selbst
 * trägt keinen MAIN/LAUNCHER-Filter — der Launcher-Einstieg läuft
 * ausschließlich über die Aliases.
 *
 * Mechanik:
 *   1. [setVariant] mapped den DataStore-String auf eine Alias-Komponente.
 *   2. Für JEDEN bekannten Alias wird [PackageManager.setComponentEnabledSetting]
 *      aufgerufen — der gewählte mit ENABLED, alle anderen mit DISABLED.
 *      Wichtig: wenn wir die anderen NICHT explizit deaktivieren, behält der
 *      System-PackageManager beide auf "DEFAULT" → der vorherige Alias bleibt
 *      sichtbar und der User hätte plötzlich zwei App-Icons.
 *   3. Wir setzen [PackageManager.DONT_KILL_APP], sonst killt Android den
 *      laufenden Prozess sofort beim Toggle und der User landet auf seinem
 *      Home-Screen statt zurück in den Settings.
 *   4. Der gewählte String wird parallel in DataStore persistiert, damit die
 *      Settings-UI ihn highlighten kann. Die Persistenz ist redundant zum
 *      Manifest-State (PackageManager merkt sich das eh über Reboots), aber
 *      ohne sie müssten wir beim App-Start aus dem ComponentEnabledSetting
 *      zurückmappen — fragiler, weil das State-Modell dort 3-wertig ist
 *      (ENABLED/DISABLED/DEFAULT) und "DEFAULT" auf das Manifest-Default
 *      zurückfällt.
 */
@Singleton
class AppIconManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDataStore
) {

    /**
     * Alle bekannten Icon-Varianten plus ihr Activity-Alias-Component-Name.
     * Component-Namen müssen exakt den `android:name`-Werten im Manifest
     * entsprechen (Class-Style mit Dot-Prefix wird gegen den `applicationId`-
     * Basisnamen ergänzt — wir geben hier den voll qualifizierten Namen an,
     * weil Build-Varianten den Suffix `.debug` etc. anhängen können).
     */
    private val aliases: Map<String, String> = mapOf(
        SettingsDataStore.APP_ICON_VARIANT_DEFAULT to ALIAS_DEFAULT,
        SettingsDataStore.APP_ICON_VARIANT_DARK to ALIAS_DARK,
        SettingsDataStore.APP_ICON_VARIANT_CLASSIC to ALIAS_CLASSIC,
        SettingsDataStore.APP_ICON_VARIANT_STUDI to ALIAS_STUDI
    )

    /**
     * Aktiviert den Alias zur gewählten Variante und deaktiviert alle anderen.
     *
     * Suspend, damit die DataStore-Persistierung im gleichen Aufruf laufen kann
     * ohne Caller-seitig einen extra Scope zu brauchen. Der PackageManager-
     * Toggle selbst ist synchron und sehr schnell.
     *
     * @param variant einer der `SettingsDataStore.APP_ICON_VARIANT_*`-Werte.
     *                Unbekannte Werte werden auf `default` gemappt — wir
     *                wollen niemals einen Zustand erzeugen, in dem alle
     *                Aliases disabled sind (dann wäre die App vom Launcher
     *                aus unstartbar).
     */
    suspend fun setVariant(variant: String) {
        val target = if (aliases.containsKey(variant)) variant
        else SettingsDataStore.APP_ICON_VARIANT_DEFAULT
        val packageManager = context.packageManager
        val packageName = context.packageName
        aliases.forEach { (key, aliasClass) ->
            val component = ComponentName(packageName, aliasClass)
            val newState = if (key == target) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                packageManager.setComponentEnabledSetting(
                    component,
                    newState,
                    PackageManager.DONT_KILL_APP
                )
            }.onFailure {
                // Auf seltenen OEM-Launchern kann setComponentEnabledSetting
                // SecurityException werfen, wenn die App nicht im Foreground
                // ist. Wir loggen und machen weiter — die anderen Aliases
                // sollten trotzdem geschaltet werden, sonst hängen wir im
                // halb-konsistenten Zwischenstand fest.
                Timber.w(it, "AppIconManager: konnte Alias %s nicht auf %s setzen", aliasClass, newState)
            }
        }
        settings.setAppIconVariant(target)
    }

    companion object {
        // Vollqualifizierte Klassennamen müssen mit `android:name` in der
        // AndroidManifest.xml übereinstimmen. Punkt-Prefix-Notation
        // (.MainActivity.IconDefault) expandiert dort auf "de.transio.hiuni".
        private const val ALIAS_DEFAULT = "de.transio.hiuni.MainActivity.IconDefault"
        private const val ALIAS_DARK = "de.transio.hiuni.MainActivity.IconDark"
        private const val ALIAS_CLASSIC = "de.transio.hiuni.MainActivity.IconClassic"
        private const val ALIAS_STUDI = "de.transio.hiuni.MainActivity.IconStudi"
    }
}
