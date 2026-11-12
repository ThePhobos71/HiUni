package de.transio.hiuni.core.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Lädt die APK eines Releases herunter. */
interface ApkDownloader {
    /**
     * @param onProgress Fortschritt in Prozent (0 bis 100).
     * @return die vollständig geladene, in der Größe geprüfte Datei.
     * @throws UpdateException bei Netzfehlern oder falscher Größe.
     */
    suspend fun download(asset: ReleaseAsset, onProgress: (Int) -> Unit): File

    /** Entfernt alle geladenen Update-Dateien. */
    fun clearDownloads()
}

/**
 * Lädt nach `cacheDir/updates/`. Vor jedem Download werden alte Dateien gelöscht,
 * es liegt also nie mehr als eine APK herum. Geschrieben wird erst in eine
 * `.part`-Datei, umbenannt erst nach erfolgreicher Größenprüfung.
 */
@Singleton
class OkHttpApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient
) : ApkDownloader {

    private val client = okHttpClient.newBuilder()
        .cache(null)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val dir: File get() = File(context.cacheDir, DIR_NAME)

    override suspend fun download(asset: ReleaseAsset, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            clearDownloads()
            dir.mkdirs()
            val target = File(dir, safeName(asset.name))
            val part = File(dir, target.name + ".part")
            val request = Request.Builder()
                .url(asset.downloadUrl)
                .header("User-Agent", GitHubReleaseSource.USER_AGENT)
                .header("Accept", "application/octet-stream")
                .build()
            val call = client.newCall(request)
            try {
                executeCancellable(call).use { res ->
                    if (!res.isSuccessful) throw UpdateException("Download fehlgeschlagen (HTTP ${res.code})")
                    val body = res.body ?: throw UpdateException("Download fehlgeschlagen, leere Antwort")
                    val total = asset.sizeBytes.takeIf { it > 0 } ?: body.contentLength()
                    var written = 0L
                    var lastPercent = -1
                    body.byteStream().use { input ->
                        part.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total > 0) {
                                    val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        onProgress(percent)
                                    }
                                }
                            }
                        }
                    }
                    if (asset.sizeBytes > 0 && !ApkVerification.sizeMatches(asset.sizeBytes, part.length())) {
                        throw UpdateException("Download unvollständig, bitte erneut versuchen")
                    }
                }
                if (!part.renameTo(target)) throw UpdateException("APK konnte nicht gespeichert werden")
                target
            } catch (e: IOException) {
                part.delete()
                throw UpdateException("Download abgebrochen, bitte Verbindung prüfen", e)
            } catch (e: Throwable) {
                part.delete()
                throw e
            }
        }

    override fun clearDownloads() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
            .onFailure { Timber.w(it, "Alte Update-Dateien nicht gelöscht") }
    }

    private suspend fun executeCancellable(call: Call) = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { call.cancel() }
        try {
            cont.resumeWith(Result.success(call.execute()))
        } catch (e: IOException) {
            cont.resumeWith(Result.failure(e))
        }
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "update.apk" }

    companion object {
        const val DIR_NAME = "updates"
        private const val BUFFER_SIZE = 64 * 1024
    }
}
