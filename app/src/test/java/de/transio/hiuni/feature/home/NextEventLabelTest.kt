package de.transio.hiuni.feature.home

import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class NextEventLabelTest {

    private fun event(source: String) = CustomEventEntity(
        title = "x",
        startTime = Instant.EPOCH,
        endTime = Instant.EPOCH,
        sourceKind = source
    )

    @Test
    fun `Label richtet sich nach der Quelle`() {
        assertEquals("NÄCHSTE VORLESUNG", nextEventLabel(event(CustomEventEntity.SOURCE_LSF_STUNDENPLAN)))
        assertEquals("NÄCHSTER FILM", nextEventLabel(event(CustomEventEntity.SOURCE_MOVIE_PIN)))
        assertEquals("NÄCHSTER SPORTKURS", nextEventLabel(event(CustomEventEntity.SOURCE_SPORT_PIN)))
        assertEquals("NÄCHSTE RAUMBUCHUNG", nextEventLabel(event(CustomEventEntity.SOURCE_BIB_BOOKING)))
        assertEquals("NÄCHSTE ABGABE", nextEventLabel(event(CustomEventEntity.SOURCE_LEARNWEB_ICAL)))
        assertEquals("NÄCHSTE ABGABE", nextEventLabel(event(CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT)))
    }

    @Test
    fun `eigene Termine und kein Termin heissen neutral Naechster Termin`() {
        assertEquals("NÄCHSTER TERMIN", nextEventLabel(event(CustomEventEntity.SOURCE_USER)))
        assertEquals("NÄCHSTER TERMIN", nextEventLabel(null))
    }

    // ── Meta-Zeile unter dem Titel ──

    private val berlin = ZoneId.of("Europe/Berlin")
    private val now = LocalDateTime.of(2026, 10, 27, 9, 0).atZone(berlin).toInstant()

    private fun startingIn(minutes: Long, location: String? = null) = CustomEventEntity(
        title = "x",
        startTime = now.plusSeconds(minutes * 60),
        endTime = now.plusSeconds(minutes * 60 + 5_400),
        location = location
    )

    @Test
    fun `Meta zeigt Minuten Stunden und laufende Termine relativ zu now`() {
        assertEquals("In 45 Min · 09:45 · F101", nextEventMeta(startingIn(45, "F101"), now, berlin))
        assertEquals("In 3 Std · 12:00", nextEventMeta(startingIn(180), now, berlin))
        assertEquals("läuft · 08:50", nextEventMeta(startingIn(-10), now, berlin))
    }

    @Test
    fun `Meta nennt den Tag relativ zu now statt zur Systemuhr`() {
        // Morgen 10:00 ist mehr als 24 h entfernt, der Tag kommt aus `now`, nicht aus LocalDate.now().
        assertEquals("Morgen · 10:00", nextEventMeta(startingIn(25L * 60), now, berlin))
    }

    @Test
    fun `ohne Termin gibt es den Hinweis zum Anlegen`() {
        assertEquals("Lege im Kalender deinen ersten Termin an", nextEventMeta(null, now, berlin))
    }
}
