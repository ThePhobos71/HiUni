package de.transio.hiuni.feature.lsf.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import biweekly.Biweekly
import biweekly.util.Frequency
import de.transio.hiuni.core.auth.CasCookieStore
import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.runCatchingApp
import de.transio.hiuni.di.IoDispatcher
import de.transio.hiuni.feature.calendar.data.CustomEventDao
import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.courses.data.CourseDao
import de.transio.hiuni.feature.courses.data.CourseEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class StundenplanSyncResult(
    val imported: Int,
    val updated: Int,
    val pruned: Int
)

interface LsfStundenplanRepository {
    /**
     * Holt den persönlichen Stundenplan via LSF-iCal-Export und upsertet alle
     * VEVENTs als CustomEventEntity mit sourceKind=LSF_STUNDENPLAN. Re-Imports
     * werden via UID-Match deduped, gestrichene Veranstaltungen (Feiertage etc.)
     * werden entfernt.
     */
    suspend fun sync(): AppResult<StundenplanSyncResult>
}

@Singleton
class LsfStundenplanRepositoryImpl @Inject constructor(
    private val casSession: CasSession,
    private val cookieStore: CasCookieStore,
    private val httpClient: OkHttpClient,
    private val eventDao: CustomEventDao,
    private val courseDao: CourseDao,
    @IoDispatcher private val io: CoroutineDispatcher
) : LsfStundenplanRepository {

    override suspend fun sync(): AppResult<StundenplanSyncResult> = runCatchingApp {
        withContext(io) {
            // 1) ST für LSF-Bootstrap holen, dann mit Ticket die JSESSIONID etablieren.
            val bootstrapTicket = casSession.getServiceTicket(LsfClient.LSF_LOGIN_SERVICE)
            val ua = cookieStore.userAgent()
            val lsfClient = httpClient.newBuilder()
                .followRedirects(true)
                .cookieJar(SingleHostCookieJar(LSF_HOST))
                .build()

            // Bootstrap-Call mit Ticket — LSF setzt JSESSIONID + redirected zum Portal.
            executeLsf(lsfClient, ua, "${LsfClient.LSF_LOGIN_SERVICE}&ticket=$bootstrapTicket").close()

            // 2) Stundenplan-HTML holen und iCal-Export-URL extrahieren.
            val stundenplanHtml = executeLsf(lsfClient, ua, LsfClient.LSF_STUNDENPLAN)
                .use { it.body?.string().orEmpty() }
            val icalUrl = extractIcalUrl(stundenplanHtml)
                ?: error("Konnte iCalendar-Export-URL im LSF-HTML nicht finden")
            Timber.i("LSF Stundenplan: ical=$icalUrl")

            // 3) ICS herunterladen + parsen. biweekly's getDateIterator() filtert
            //    EXDATEs in dieser Konstellation NICHT zuverlässig — wir expandieren
            //    die RRULE manuell mit ZonedDateTime in Europe/Berlin.
            val ics = executeLsf(lsfClient, ua, icalUrl)
                .use { it.body?.string().orEmpty() }
            // LSF schreibt EXDATE-Listen mit trailing comma. Biweekly verwirft die
            // Property dadurch — fixen wir simpel im Roh-ICS bevor wir parsen.
            val cleanedIcs = sanitizeIcs(ics)
            val ical = Biweekly.parse(cleanedIcs).first()
                ?: error("LSF lieferte leeren iCalendar-Stream")
            Timber.i("LSF Stundenplan: ${ical.events.size} VEVENT-Serien (ICS=${cleanedIcs.length}ch)")

            // 4) Lookup-Map "Modulcode → LSF-publishid" einmal vorbauen, damit wir
            //    jedem VEVENT seine zugehörige Veranstaltung zuordnen können.
            val courseByCode = courseDao.findBySource(CourseEntity.SOURCE_LSF)
                .mapNotNull { course ->
                    val code = LEADING_CODE_REGEX.find(course.name)?.groupValues?.get(1)
                    val lsfId = course.lsfId
                    if (code != null && lsfId != null) code to lsfId else null
                }
                .toMap()

            // 5) Jede Serie expandieren und Instanzen upserten.
            val berlinZone = java.time.ZoneId.of("Europe/Berlin")
            var imported = 0
            var updated = 0
            val keepRefs = mutableListOf<String>()

            for (event in ical.events) {
                val baseUid = event.uid?.value ?: continue
                val dtstart = event.dateStart?.value ?: continue
                val dtend = event.dateEnd?.value ?: continue
                val duration = Duration.ofMillis(dtend.time - dtstart.time)
                if (duration.isZero || duration.isNegative) continue

                val summary = event.summary?.value?.takeIf { it.isNotBlank() } ?: "(Ohne Titel)"
                val description = event.description?.value?.takeIf { it.isNotBlank() }
                val location = event.location?.value?.takeIf { it.isNotBlank() }
                // Modulcode aus dem Summary ziehen (LSF rendert "NNNN Modulname"
                // im VEVENT-Titel) und mit der vorgebauten Map auf publishid mappen.
                val courseCode = LEADING_CODE_REGEX.find(summary)?.groupValues?.get(1)
                val courseLsfId = courseCode?.let { courseByCode[it] }

                val startZdt = Instant.ofEpochMilli(dtstart.time).atZone(berlinZone)
                val rrule = event.recurrenceRule?.value
                val exDates = event.exceptionDates
                    .flatMap { it.values }
                    .map { Instant.ofEpochMilli(it.time) }
                    .toSet()

                val occurrences: List<Instant> = if (rrule == null || rrule.frequency != Frequency.WEEKLY) {
                    // Einzeltermin oder anderer Frequenz-Typ — einfach DTSTART übernehmen
                    listOf(startZdt.toInstant())
                } else {
                    expandWeekly(startZdt, rrule, exDates, berlinZone)
                }

                Timber.d("VEVENT uid=$baseUid series=${occurrences.size} exdates=${exDates.size}")

                for (startInstant in occurrences) {
                    val endInstant = startInstant.plus(duration)
                    val ref = "$baseUid#${startInstant.toEpochMilli()}"
                    keepRefs += ref
                    val existing = eventDao.findBySourceReference(
                        CustomEventEntity.SOURCE_LSF_STUNDENPLAN, ref
                    )
                    val entity = CustomEventEntity(
                        id = existing?.id ?: 0,
                        title = summary,
                        description = description,
                        location = location,
                        startTime = startInstant,
                        endTime = endInstant,
                        sourceKind = CustomEventEntity.SOURCE_LSF_STUNDENPLAN,
                        sourceReference = ref,
                        reminderMinutesBefore = existing?.reminderMinutesBefore,
                        courseLsfId = courseLsfId
                    )
                    if (existing == null) {
                        eventDao.insert(entity); imported += 1
                    } else {
                        eventDao.update(entity); updated += 1
                    }
                }
            }

            // Pruning: alles was nicht mehr im aktuellen Export ist (z.B. neuer EXDATE).
            val before = eventDao.sourceReferencesFor(CustomEventEntity.SOURCE_LSF_STUNDENPLAN)
            val toPrune = before.toSet() - keepRefs.toSet()
            if (keepRefs.isNotEmpty()) {
                eventDao.pruneBySourceKind(CustomEventEntity.SOURCE_LSF_STUNDENPLAN, keepRefs)
            }
            Timber.i("LSF Stundenplan: imported=$imported updated=$updated pruned=${toPrune.size}")
            StundenplanSyncResult(imported, updated, toPrune.size)
        }
    }

    /**
     * Expandiert eine FREQ=WEEKLY RRULE manuell — biweekly's getDateIterator()
     * filtert EXDATEs in unserer Konstellation nicht zuverlässig.
     *
     * Unterstützt: INTERVAL, BYDAY (genau ein Wochentag, wie LSF es nutzt), UNTIL.
     * Wenn DTSTART nicht auf den BYDAY-Tag fällt, springen wir zum ersten matchenden
     * Tag (so handhabt CAL-SPEC + LSF es: erste Vorlesung am ersten Tag der Woche,
     * der BYDAY entspricht).
     */
    private fun expandWeekly(
        startZdt: java.time.ZonedDateTime,
        rrule: biweekly.util.Recurrence,
        exDates: Set<Instant>,
        @Suppress("UNUSED_PARAMETER") zone: java.time.ZoneId
    ): List<Instant> {
        val interval = rrule.interval?.coerceAtLeast(1) ?: 1
        val untilDate = rrule.until
        // UNTIL fehlt: 1 Jahr ab DTSTART als Sicherheitsobergrenze.
        val untilInstant = untilDate
            ?.let { Instant.ofEpochMilli(it.time) }
            ?: startZdt.toInstant().plus(Duration.ofDays(365))

        // BYDAY: LSF nutzt genau einen Wochentag. Wir nehmen den ersten als Ziel.
        val targetDow: java.time.DayOfWeek? = rrule.byDay
            ?.firstOrNull()
            ?.day
            ?.let { dow ->
                // biweekly.util.DayOfWeek → java.time.DayOfWeek
                when (dow.name.uppercase()) {
                    "MONDAY" -> java.time.DayOfWeek.MONDAY
                    "TUESDAY" -> java.time.DayOfWeek.TUESDAY
                    "WEDNESDAY" -> java.time.DayOfWeek.WEDNESDAY
                    "THURSDAY" -> java.time.DayOfWeek.THURSDAY
                    "FRIDAY" -> java.time.DayOfWeek.FRIDAY
                    "SATURDAY" -> java.time.DayOfWeek.SATURDAY
                    "SUNDAY" -> java.time.DayOfWeek.SUNDAY
                    else -> null
                }
            }

        // Auf den ersten Tag rücken, der BYDAY entspricht (falls DTSTART nicht passt).
        var current = startZdt
        if (targetDow != null && current.dayOfWeek != targetDow) {
            val delta = ((targetDow.value - current.dayOfWeek.value) + 7) % 7
            current = current.plusDays(delta.toLong())
        }

        val occurrences = mutableListOf<Instant>()
        while (!current.toInstant().isAfter(untilInstant)) {
            val instant = current.toInstant()
            if (instant !in exDates) {
                occurrences += instant
            }
            current = current.plusWeeks(interval.toLong())
        }
        return occurrences
    }

    /**
     * Fixt LSF-Quirks im Roh-ICS bevor biweekly drüberläuft.
     *
     * Aktuell: trailing comma in EXDATE-Listen entfernen. LSF schreibt:
     *   `EXDATE;TZID=Europe/Berlin:20260414T080000,…,20260526T080000,`
     * was biweekly als invalid abweist → komplette EXDATE-Property fällt weg →
     * ausgefallene Wochen werden nicht ausgeschlossen.
     */
    private fun sanitizeIcs(raw: String): String =
        raw.lines().joinToString("\n") { line ->
            if (line.startsWith("EXDATE", ignoreCase = true)) line.trimEnd(',', ' ', '\t', '\r')
            else line
        }

    /**
     * Findet im Stundenplan-HTML den `<a href="…moduleCall=iCalendarPlan…">` Link.
     */
    private fun extractIcalUrl(html: String): String? {
        return runCatching {
            val doc = Jsoup.parse(html)
            doc.select("a[href*=moduleCall=iCalendarPlan]")
                .firstOrNull()
                ?.absUrl("href")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun executeLsf(
        client: OkHttpClient,
        userAgent: String?,
        url: String
    ): okhttp3.Response {
        val builder = Request.Builder().url(url)
            .header("Accept", "text/html,text/calendar,application/xhtml+xml,*/*;q=0.8")
        userAgent?.takeIf { it.isNotBlank() }?.let { builder.header("User-Agent", it) }
        return client.newCall(builder.build()).execute()
    }

    companion object {
        const val LSF_HOST = "lsf.uni-hildesheim.de"
        /** Greift den führenden Modul-Code aus Titeln wie "3204 Logistik und Produktion 1". */
        private val LEADING_CODE_REGEX = Regex("^(\\d+)\\s+")
    }
}

/**
 * Cookie-Jar das nur Cookies für einen einzelnen Host speichert — verhindert,
 * dass z.B. das CAS-TGC versehentlich an LSF gesendet wird oder umgekehrt.
 */
private class SingleHostCookieJar(private val host: String) : okhttp3.CookieJar {
    private val cookies = mutableListOf<okhttp3.Cookie>()
    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
        if (url.host != host) return
        synchronized(this.cookies) {
            this.cookies.removeAll { existing -> cookies.any { it.name == existing.name } }
            this.cookies.addAll(cookies)
        }
    }
    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
        if (url.host != host) return emptyList()
        return synchronized(this.cookies) { this.cookies.toList() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LsfStundenplanRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindLsfStundenplanRepository(
        impl: LsfStundenplanRepositoryImpl
    ): LsfStundenplanRepository
}
