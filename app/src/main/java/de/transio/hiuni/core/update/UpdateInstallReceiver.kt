package de.transio.hiuni.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Empfängt den Status der PackageInstaller-Session. Bei
 * `STATUS_PENDING_USER_ACTION` startet er den System-Bestätigungsdialog, alle
 * übrigen Ergebnisse gehen an [UpdateInstallEvents]. `exported=false`: nur der
 * eigene PendingIntent erreicht ihn.
 */
@AndroidEntryPoint
class UpdateInstallReceiver : BroadcastReceiver() {

    @Inject lateinit var events: UpdateInstallEvents

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val outcome = InstallStatusMapper.map(status, message)
        Timber.i("Update-Installation: Status %d (%s)", status, message)
        if (outcome is InstallOutcome.PendingUserAction) {
            val confirm = confirmIntent(intent)
            if (confirm == null) {
                events.publish(InstallOutcome.Failure("Installationsdialog nicht verfügbar"))
                return
            }
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(confirm) }
                .onFailure {
                    Timber.e(it, "Installationsdialog nicht startbar")
                    events.publish(InstallOutcome.Failure("Installationsdialog nicht verfügbar"))
                }
        }
        events.publish(outcome)
    }

    private fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    companion object {
        const val ACTION_INSTALL_STATUS = "de.transio.hiuni.action.UPDATE_INSTALL_STATUS"
    }
}
