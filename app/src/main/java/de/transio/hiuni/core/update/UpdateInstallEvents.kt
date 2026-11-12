package de.transio.hiuni.core.update

import android.content.pm.PackageInstaller
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface InstallOutcome {
    /** Android zeigt den Bestätigungsdialog. */
    data object PendingUserAction : InstallOutcome
    data object Success : InstallOutcome
    /** Nutzer hat im Dialog abgebrochen. */
    data object Aborted : InstallOutcome
    data class Failure(val message: String) : InstallOutcome
}

/** Übersetzt PackageInstaller-Statuscodes in [InstallOutcome] mit deutscher Meldung. */
object InstallStatusMapper {
    fun map(status: Int, systemMessage: String?): InstallOutcome = when (status) {
        PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallOutcome.PendingUserAction
        PackageInstaller.STATUS_SUCCESS -> InstallOutcome.Success
        PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.Aborted
        PackageInstaller.STATUS_FAILURE_STORAGE -> InstallOutcome.Failure("Zu wenig Speicher für das Update")
        PackageInstaller.STATUS_FAILURE_CONFLICT,
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            InstallOutcome.Failure("Update passt nicht zur installierten App")
        PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallOutcome.Failure("Installation vom System blockiert")
        PackageInstaller.STATUS_FAILURE_INVALID -> InstallOutcome.Failure("APK ungültig, Update abgebrochen")
        else -> InstallOutcome.Failure(
            if (systemMessage.isNullOrBlank()) "Installation fehlgeschlagen"
            else "Installation fehlgeschlagen ($systemMessage)"
        )
    }
}

/**
 * Brücke vom [UpdateInstallReceiver] zum Update-Screen. Ohne Replay: ist der
 * Screen nicht offen, interessiert das Ergebnis niemanden mehr (bei Erfolg wird
 * der Prozess ohnehin neu gestartet).
 */
@Singleton
class UpdateInstallEvents @Inject constructor() {
    private val flow = MutableSharedFlow<InstallOutcome>(extraBufferCapacity = 4)

    val outcomes: Flow<InstallOutcome> = flow.asSharedFlow()

    fun publish(outcome: InstallOutcome) {
        flow.tryEmit(outcome)
    }
}
