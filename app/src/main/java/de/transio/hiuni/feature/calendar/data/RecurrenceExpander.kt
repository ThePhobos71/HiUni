package de.transio.hiuni.feature.calendar.data

import timber.log.Timber
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.max

/**
 * Expandiert einen Master-Event mit Recurrence-Rule in N virtuelle Instanzen, die
 * in das Fenster `[from, to)` fallen.
 *
 * Die Instanzen sind read-only-Views auf den Master:
 * - gleiche [CustomEventEntity.id] (damit Edits/Deletes ans Master gehen)
 * - neuer `startTime`/`endTime` pro Vorkommen
 * - sonst identische Felder (title, location, sourceKind, …)
 *
 * Der Master wird selber ebenfalls als Vorkommen behandelt, falls er ins Fenster fällt.
 * So müssen Konsumenten keine Spezialbehandlung machen — sie sehen einfach eine Liste
 * von Events. Edits gehen via `event.id` an den Master in der DB.
 *
 * Performance:
 * - Hard cap auf [MAX_OCCURRENCES_PER_MASTER] pro Master/Window, danach Warn-Log + Break.
 * - Wenn `until == null`: implizites Cap auf 2 Jahre nach Master-Start, damit Expansion
 *   nicht runaway läuft.
 *
 * Nicht thread-safe, aber pure (kein State) — kann gefahrlos aus mehreren Coroutines
 * parallel aufgerufen werden.
 */
object RecurrenceExpander {

    /** Hard cap pro Master-Event und Window. Falls erreicht → Warn-Log + früher Abbruch. */
    const val MAX_OCCURRENCES_PER_MASTER = 365

    /** Implizites until-Cap, falls die Regel keines hat. 2 Jahre nach Master-Start. */
    private const val UNBOUNDED_CAP_YEARS = 2L

    /**
     * Hauptentry-Point: expandiert einen einzelnen Master-Event. Wenn `master.recurrenceRule`
     * null oder nicht parsebar ist, kommt entweder `[master]` (falls im Window) oder `[]`
     * zurück. So kann der Caller blind `flatMap { expand(it, from, to) }` machen.
     */
    fun expand(
        master: CustomEventEntity,
        from: Instant,
        to: Instant,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<CustomEventEntity> {
        val rule = RecurrenceRule.fromJsonString(master.recurrenceRule)
        if (rule == null) {
            // Single-shot: liefere den Master nur dann, wenn er das Fenster überlappt.
            return if (overlapsWindow(master.startTime, master.endTime, from, to)) {
                listOf(master)
            } else {
                emptyList()
            }
        }
        return expandRecurring(master, rule, from, to, zone)
    }

    /**
     * Expandiert eine Liste von Events (Mix aus single-shot + recurring) in einem Fenster.
     * Nutzt [expand] pro Event und flattet die Ergebnisse.
     */
    fun expandAll(
        masters: List<CustomEventEntity>,
        from: Instant,
        to: Instant,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<CustomEventEntity> = masters.flatMap { expand(it, from, to, zone) }

    private fun expandRecurring(
        master: CustomEventEntity,
        rule: RecurrenceRule,
        from: Instant,
        to: Instant,
        zone: ZoneId
    ): List<CustomEventEntity> {
        val masterStart = master.startTime.atZone(zone).toLocalDateTime()
        val duration = Duration.between(master.startTime, master.endTime)
        // Hard cap: until oder masterStart+2J, je nachdem was näher liegt.
        val untilCap = rule.until ?: masterStart.toLocalDate().plusYears(UNBOUNDED_CAP_YEARS)
        // Window-End als LocalDate (exklusiv).
        val windowEndDate = to.atZone(zone).toLocalDate()
        val effectiveUntil = if (untilCap.isBefore(windowEndDate)) untilCap else windowEndDate

        val occurrences = mutableListOf<CustomEventEntity>()
        var safetyCount = 0

        when (rule.freq) {
            RecurrenceRule.Freq.DAILY -> {
                var cursor = masterStart
                while (!cursor.toLocalDate().isAfter(effectiveUntil.minusDays(1))) {
                    if (safetyCount >= MAX_OCCURRENCES_PER_MASTER) {
                        Timber.w(
                            "RecurrenceExpander: cap %d für Master id=%d erreicht (DAILY)",
                            MAX_OCCURRENCES_PER_MASTER, master.id
                        )
                        break
                    }
                    addIfInWindow(occurrences, master, cursor, duration, zone, from, to)
                    cursor = cursor.plusDays(rule.interval.toLong())
                    safetyCount++
                }
            }
            RecurrenceRule.Freq.WEEKLY -> {
                // byDays: explizite Wochentage, sonst der DayOfWeek vom Master.
                val targetDays = rule.byDays?.takeIf { it.isNotEmpty() }
                    ?: listOf(masterStart.dayOfWeek)
                // Wir iterieren Woche-für-Woche; pro Woche gehen wir die targetDays in
                // Wochenreihenfolge durch und schieben den cursor vom Wochenstart aus.
                val masterWeekStart = masterStart.toLocalDate()
                    .with(java.time.DayOfWeek.MONDAY)
                var weekStart = masterWeekStart
                while (!weekStart.isAfter(effectiveUntil.minusDays(1))) {
                    if (safetyCount >= MAX_OCCURRENCES_PER_MASTER) {
                        Timber.w(
                            "RecurrenceExpander: cap %d für Master id=%d erreicht (WEEKLY)",
                            MAX_OCCURRENCES_PER_MASTER, master.id
                        )
                        break
                    }
                    for (day in targetDays.sortedBy { it.value }) {
                        val occDate = weekStart.plusDays((day.value - 1).toLong())
                        // Vor Master-Start ignorieren (sonst würden wir Phantominstanzen
                        // erzeugen, die zeitlich vor dem ersten Auftreten liegen).
                        if (occDate.isBefore(masterStart.toLocalDate())) continue
                        if (!occDate.isBefore(effectiveUntil)) continue
                        val occStart = LocalDateTime.of(occDate, masterStart.toLocalTime())
                        addIfInWindow(occurrences, master, occStart, duration, zone, from, to)
                        safetyCount++
                        if (safetyCount >= MAX_OCCURRENCES_PER_MASTER) break
                    }
                    weekStart = weekStart.plusWeeks(rule.interval.toLong())
                }
            }
            RecurrenceRule.Freq.MONTHLY -> {
                // Gleicher Tag-im-Monat; Februar 30. → skip.
                val dayOfMonth = masterStart.dayOfMonth
                var cursor = masterStart
                while (!cursor.toLocalDate().isAfter(effectiveUntil.minusDays(1))) {
                    if (safetyCount >= MAX_OCCURRENCES_PER_MASTER) {
                        Timber.w(
                            "RecurrenceExpander: cap %d für Master id=%d erreicht (MONTHLY)",
                            MAX_OCCURRENCES_PER_MASTER, master.id
                        )
                        break
                    }
                    // Skippe Monate ohne diesen Tag (z.B. 31. Februar).
                    val cursorMonth = cursor.toLocalDate().withDayOfMonth(1)
                    val maxDay = cursorMonth.lengthOfMonth()
                    if (dayOfMonth <= maxDay) {
                        addIfInWindow(occurrences, master, cursor, duration, zone, from, to)
                    }
                    // Nächste Iteration: cursor um `interval` Monate vorrücken, dann
                    // dayOfMonth restaurieren (oder skippen wenn invalid).
                    val nextMonth = cursor.plusMonths(rule.interval.toLong())
                        .toLocalDate().withDayOfMonth(1)
                    val nextMax = nextMonth.lengthOfMonth()
                    val nextDay = if (dayOfMonth <= nextMax) dayOfMonth else {
                        // Tag existiert im Zielmonat nicht — wir lassen den cursor
                        // einfach auf den 1. springen und entscheiden im nächsten
                        // Loop-Eintritt via `if (dayOfMonth <= maxDay)`.
                        1
                    }
                    cursor = LocalDateTime.of(
                        nextMonth.withDayOfMonth(nextDay),
                        masterStart.toLocalTime()
                    )
                    safetyCount++
                }
            }
        }
        return occurrences
    }

    private fun addIfInWindow(
        out: MutableList<CustomEventEntity>,
        master: CustomEventEntity,
        startLocal: LocalDateTime,
        duration: Duration,
        zone: ZoneId,
        from: Instant,
        to: Instant
    ) {
        val occStart = startLocal.atZone(zone).toInstant()
        val occEnd = occStart.plus(duration)
        if (!overlapsWindow(occStart, occEnd, from, to)) return
        out.add(master.copy(startTime = occStart, endTime = occEnd))
    }

    /**
     * Liefert den nächsten Occurrence-Start ≥ [now], oder null wenn keine weitere
     * Occurrence im Cap-Range. Wird vom Reminder-Scheduler verwendet, um nach einem
     * Trigger den nächsten Alarm einzuplanen.
     */
    fun nextOccurrenceAfter(
        master: CustomEventEntity,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault()
    ): Instant? {
        val rule = RecurrenceRule.fromJsonString(master.recurrenceRule)
        if (rule == null) {
            // Single-shot: Master selbst, wenn er noch in der Zukunft liegt.
            return master.startTime.takeIf { !it.isBefore(now) }
        }
        // Wir schaffen ein großes Fenster ab `now` und nehmen das erste Ergebnis.
        // Cap ist 2 Jahre — ausreichend für jeden praktischen Fall.
        val zonedNow = now.atZone(zone).toLocalDate()
        val effectiveCap = rule.until ?: zonedNow.plusYears(UNBOUNDED_CAP_YEARS)
        val capInstant = effectiveCap.atStartOfDay(zone).toInstant()
        val windowFrom = max(now.toEpochMilli(), master.startTime.toEpochMilli())
        val occurrences = expandRecurring(
            master = master,
            rule = rule,
            from = Instant.ofEpochMilli(windowFrom),
            to = capInstant,
            zone = zone
        )
        return occurrences
            .map { it.startTime }
            .firstOrNull { !it.isBefore(now) }
    }

    private fun overlapsWindow(
        startA: Instant, endA: Instant,
        startB: Instant, endB: Instant
    ): Boolean = startA.isBefore(endB) && endA.isAfter(startB)
}
