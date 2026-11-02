package de.transio.hiuni.feature.bib.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Reine Regeln der Raum-Alarm-Einstellung: Ablauf von „Nur heute", Fenster, Status-Zeile. */
class BibRoomAlarmTest {

    private val today = LocalDate.of(2026, 10, 26)

    @Test
    fun `Standardwerte sind 2 Stunden und 08 bis 20 Uhr`() {
        val alarm = BibRoomAlarm()
        assertEquals(BibAlarmMode.OFF, alarm.mode)
        assertEquals(120, alarm.durationMinutes)
        assertEquals(8, alarm.fromHour)
        assertEquals(20, alarm.toHour)
    }

    @Test
    fun `Nur heute gilt am Aktivierungstag und ab dem Folgetag als Aus`() {
        val alarm = BibRoomAlarm(mode = BibAlarmMode.TODAY_ONLY, activatedOn = today)

        assertEquals(BibAlarmMode.TODAY_ONLY, alarm.effectiveMode(today))
        assertTrue(alarm.isActive(today))
        assertEquals(BibAlarmMode.OFF, alarm.effectiveMode(today.plusDays(1)))
        assertFalse(alarm.isActive(today.plusDays(1)))
    }

    @Test
    fun `Nur heute ohne Aktivierungsdatum ist Aus`() {
        assertFalse(BibRoomAlarm(mode = BibAlarmMode.TODAY_ONLY, activatedOn = null).isActive(today))
    }

    @Test
    fun `Immer bleibt an jedem Tag aktiv, Aus nie`() {
        assertTrue(BibRoomAlarm(mode = BibAlarmMode.ALWAYS).isActive(today.plusDays(30)))
        assertFalse(BibRoomAlarm(mode = BibAlarmMode.OFF).isActive(today))
    }

    @Test
    fun `Migration, Bestandsnutzer mit Favoriten behalten den Alarm, alle anderen starten mit Aus`() {
        assertEquals(BibAlarmMode.ALWAYS, BibRoomAlarm.defaultModeFor(hasFavorites = true))
        assertEquals(BibAlarmMode.OFF, BibRoomAlarm.defaultModeFor(hasFavorites = false))
    }

    @Test
    fun `Zeitfenster ist von inklusive bis exklusive`() {
        val alarm = BibRoomAlarm(fromHour = 10, toHour = 14)
        assertFalse(alarm.inWindow(LocalTime.of(9, 59)))
        assertTrue(alarm.inWindow(LocalTime.of(10, 0)))
        assertTrue(alarm.inWindow(LocalTime.of(13, 59)))
        assertFalse(alarm.inWindow(LocalTime.of(14, 0)))
    }

    @Test
    fun `Von nach hinten schiebt Bis mit, Bis nach vorne schiebt Von mit`() {
        val alarm = BibRoomAlarm(fromHour = 8, toHour = 12)

        assertEquals(14 to 15, alarm.withFromHour(14).let { it.fromHour to it.toHour })
        assertEquals(8 to 12, alarm.withFromHour(8).let { it.fromHour to it.toHour })
        assertEquals(8 to 10, alarm.withToHour(10).let { it.fromHour to it.toHour })

        val late = BibRoomAlarm(fromHour = 16, toHour = 20)
        assertEquals(9 to 10, late.withToHour(10).let { it.fromHour to it.toHour })
    }

    @Test
    fun `Fensterwerte werden auf die Bib-Zeiten begrenzt`() {
        val alarm = BibRoomAlarm()
        assertEquals(19, alarm.withFromHour(23).fromHour)
        assertEquals(20, alarm.withFromHour(23).toHour)
        assertEquals(8, alarm.withFromHour(3).fromHour)
        assertEquals(20, alarm.withToHour(24).toHour)
        assertEquals(9, alarm.withToHour(1).toHour)
    }

    @Test
    fun `Status-Zeile je Zustand`() {
        assertEquals(
            "Keine Lieblings-Räume markiert",
            BibRoomAlarm(mode = BibAlarmMode.ALWAYS).statusLine(hasFavorites = false, today = today)
        )
        assertEquals("Aus", BibRoomAlarm(mode = BibAlarmMode.OFF).statusLine(true, today))
        assertEquals(
            "Aktiv bis heute 23:59",
            BibRoomAlarm(mode = BibAlarmMode.TODAY_ONLY, activatedOn = today).statusLine(true, today)
        )
        assertEquals(
            "Aus",
            BibRoomAlarm(mode = BibAlarmMode.TODAY_ONLY, activatedOn = today.minusDays(1)).statusLine(true, today)
        )
        assertEquals(
            "Immer aktiv, 08:00 bis 20:00",
            BibRoomAlarm(mode = BibAlarmMode.ALWAYS).statusLine(true, today)
        )
    }
}
