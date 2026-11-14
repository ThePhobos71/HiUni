package de.transio.hiuni.feature.rooms.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Raumliste und Wochenplan mit gekürzten echten LSF-Abrufen vom 2026-11-14. */
class LsfRoomParsersTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/raumfinder/$name")) { "Fixture $name fehlt" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    @Test
    fun `Raumliste liefert nur Lernraeume mit bekanntem Campus`() {
        val rooms = LsfRoomListParser.parse(fixture("raumliste.html"))
        assertEquals(
            listOf(
                "BC.L.0.47", "BC.L.0.57", "HC.E.0.Hörsaal 3",
                "KC.52.1.23", "SC.A.0.09", "SC.B.0.37", "SH.0.23"
            ).sorted(),
            rooms.map { it.code }.sorted()
        )
    }

    @Test
    fun `Raumliste trennt Code, Raumart, Campus und rgid`() {
        val rooms = LsfRoomListParser.parse(fixture("raumliste.html")).associateBy { it.code }
        assertEquals(LsfRoom(2, "SC.A.0.09", "Großer Seminarraum", Campus.SAMELSON), rooms["SC.A.0.09"])
        assertEquals(LsfRoom(6, "HC.E.0.Hörsaal 3", "Hörsaal", Campus.HAUPTCAMPUS), rooms["HC.E.0.Hörsaal 3"])
        assertEquals("stud. Arbeitsraum", rooms["BC.L.0.57"]?.type)
        assertEquals(Campus.BUEHLER, rooms["BC.L.0.57"]?.campus)
        assertEquals("Theatermediothek/Seminarraum", rooms["KC.52.1.23"]?.type)
        assertEquals(Campus.SCHUETZENALLEE, rooms["SH.0.23"]?.campus)
    }

    @Test
    fun `Filter schliesst Labore, Gesperrtes, Freiluft und Sonstiges aus`() {
        assertTrue(LsfRoomListParser.isStudyRoom("HC.K.1.Musiksaal (großer Seminarraum)"))
        assertTrue(LsfRoomListParser.isStudyRoom("BC.L.0.47 (kl. Hörsaal)"))
        assertFalse(LsfRoomListParser.isStudyRoom("HC.D.0.06 (Fachseminarraum Biologie, Labortische)"))
        assertFalse(LsfRoomListParser.isStudyRoom("gesperrt-HC.E.1.02 (Seminarraum)"))
        assertFalse(LsfRoomListParser.isStudyRoom("HC.H.1.Hörsaal 2-gesperrt ab 18.07.26"))
        assertFalse(LsfRoomListParser.isStudyRoom("KC.46.Frei/Freiluftseminarraum"))
        assertFalse(LsfRoomListParser.isStudyRoom("BC.L.0.62 (Bistro Bühler-Campus)"))
        assertFalse(LsfRoomListParser.isStudyRoom("Online-Lehre (ggf. folgt Raumangabe für Ausnahme Präsenzlehre)"))
        assertFalse(LsfRoomListParser.isStudyRoom("HC.E.-1.18 (Studiobühne 1)"))
    }

    @Test
    fun `Wochenplan liefert den iCal-Link mit den Termin-IDs der Woche`() {
        val result = LsfRoomPlanParser.parse(fixture("raumplan_woche.html"))
        assertTrue(result is LsfRoomPlanParser.Result.Ical)
        val url = (result as LsfRoomPlanParser.Result.Ical).url
        assertTrue(url, url.startsWith("https://lsf.uni-hildesheim.de/qisserver/rds?state=verpublish"))
        assertTrue(url, url.contains("termine=329009,325386,319190"))
        assertTrue(url, url.contains("moduleCall=iCalendarPlan"))
        assertFalse(url, url.contains("&amp;"))
    }

    @Test
    fun `Wochenplan ohne Termine ist leer, nicht kaputt`() {
        assertEquals(LsfRoomPlanParser.Result.NoEvents, LsfRoomPlanParser.parse(fixture("raumplan_leer.html")))
    }

    @Test
    fun `Seite ohne Plan-Raster ist ungueltig`() {
        assertEquals(
            LsfRoomPlanParser.Result.Invalid,
            LsfRoomPlanParser.parse("<html><body><h1>Wartung</h1></body></html>")
        )
    }
}
