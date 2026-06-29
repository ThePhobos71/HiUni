package de.transio.hiuni.core.network

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import kotlin.random.Random

/**
 * Verzögert ausgehende Requests zu „empfindlichen" Hosts (LSF & CAS der Uni
 * Hildesheim) zufällig zwischen [minDelayMs] und [maxDelayMs], damit unsere
 * App keinen Burst-Traffic gegen die Uni-Infrastruktur fährt.
 *
 * Rationale: LSF läuft auf eher schmalbrüstiger Hardware. Wenn drei oder vier
 * Sync-Phasen direkt nacheinander den Server treffen (MyCourses → Stundenplan
 * → Klausuren → Probe-Request), kann das Probleme machen. Zufalls-Delay
 * spreizt die Last und imitiert menschliches Browsing.
 *
 * NICHT betroffen: STW-Mensa-API, OMDB, alle anderen externen Hosts — die
 * sind robuster und sollen nicht künstlich gebremst werden.
 */
class PolitenessInterceptor(
    private val minDelayMs: Long = 200L,
    private val maxDelayMs: Long = 1200L,
    private val sensitiveHosts: Set<String> = DEFAULT_SENSITIVE_HOSTS
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        if (host in sensitiveHosts) {
            val delay = Random.nextLong(minDelayMs, maxDelayMs + 1)
            Timber.v("PolitenessInterceptor: %dms delay vor %s", delay, host)
            try {
                Thread.sleep(delay)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            }
        }
        return chain.proceed(chain.request())
    }

    companion object {
        val DEFAULT_SENSITIVE_HOSTS: Set<String> = setOf(
            "lsf.uni-hildesheim.de",
            // CAS läuft auf dem gleichen Cluster, gleiche Politeness-Regel
            "cas.uni-hildesheim.de",
            // Learnweb (Moodle) liegt unter www.uni-hildesheim.de — gleiche
            // Infrastruktur-Familie, deshalb auch hier random-delay damit
            // Refresh-Phasen nicht als Burst auflaufen.
            "www.uni-hildesheim.de"
        )
    }
}
