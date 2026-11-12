package de.transio.hiuni.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Installiert eine geprüfte APK. */
interface UpdateInstaller {
    /** Darf HiUni Apps installieren („Unbekannte Apps installieren")? */
    fun canRequestInstalls(): Boolean

    /** Öffnet die Systemseite, auf der der Nutzer HiUni das Installieren erlaubt. */
    fun openInstallPermissionSettings()

    /**
     * Schreibt die APK in eine PackageInstaller-Session und committet sie. Das
     * Ergebnis kommt asynchron über den [UpdateInstallReceiver].
     *
     * @throws UpdateException wenn die Session nicht angelegt werden kann.
     */
    suspend fun install(file: File)
}

/**
 * PackageInstaller-Session statt `ACTION_VIEW`: kein FileProvider nötig, und der
 * Status (Bestätigung nötig, Erfolg, Fehler) kommt als Broadcast zurück.
 */
@Singleton
class PackageInstallerUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) : UpdateInstaller {

    override fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "Einstellung für unbekannte Apps nicht erreichbar") }
    }

    override suspend fun install(file: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Ohne Dialog nur, wenn HiUni selbst als Installationsquelle gilt;
                // sonst fragt Android trotzdem nach (STATUS_PENDING_USER_ACTION).
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = try {
            installer.createSession(params)
        } catch (e: IOException) {
            throw UpdateException("Installation konnte nicht gestartet werden", e)
        }
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite(SESSION_FILE_NAME, 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                session.commit(statusIntent(sessionId).intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw UpdateException("Installation konnte nicht gestartet werden", e)
        }
    }

    private fun statusIntent(sessionId: Int): PendingIntent {
        val intent = Intent(context, UpdateInstallReceiver::class.java)
            .setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS)
            .setPackage(context.packageName)
        // MUTABLE ist Pflicht: der PackageInstaller hängt Status-Extras an.
        // Unbedenklich, weil der Intent explizit auf unseren Receiver zeigt.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }

    private companion object {
        const val SESSION_FILE_NAME = "base.apk"
    }
}
