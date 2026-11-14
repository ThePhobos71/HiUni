package de.transio.hiuni.feature.rooms.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FreeRoomCalculatorTest {

    private fun room(code: String) = LsfRoom(code.hashCode(), code, "Seminarraum", Campus.SAMELSON)
    private fun m(hhmm: String): Int = hhmm.substringBefore(':').toInt() * 60 + hhmm.substringAfter(':').toInt()
    private fun day(code: String, vararg spans: Pair<String, String>) =
        RoomDay(room(code), spans.map { BusySpan(m(it.first), m(it.second)) })

    @Test
    fun `freie Raeume am laengsten frei oben, belegte darunter am fruehesten frei oben`() {
        val days = listOf(
            day("SC.A.0.09", "16:00" to "18:00"),
            day("SC.B.0.37", "14:15" to "15:00"),
            day("SC.A.1.02"),
            day("SC.B.1.48", "12:00" to "14:00"),
            day("SC.B.0.25", "13:00" to "15:30", "16:00" to "17:00")
        )
        val result = FreeRoomCalculator.evaluate(days, m("13:42"))

        assertEquals(listOf("SC.A.1.02", "SC.A.0.09", "SC.B.0.37"), result.free.map { it.room.code })
        assertEquals(listOf(null, m("16:00"), m("14:15")), result.free.map { it.freeUntilMinute })
        assertEquals(listOf("SC.B.1.48", "SC.B.0.25"), result.busy.map { it.room.code })
        assertEquals(BusyRoom(room("SC.B.1.48"), m("14:00"), null), result.busy[0])
        assertEquals(BusyRoom(room("SC.B.0.25"), m("15:30"), m("16:00")), result.busy[1])
    }

    @Test
    fun `Grenzen - Ende um genau jetzt ist frei, Beginn um genau jetzt ist belegt`() {
        val ended = FreeRoomCalculator.evaluate(listOf(day("A", "12:00" to "14:00")), m("14:00"))
        assertEquals(listOf(FreeRoom(room("A"), null)), ended.free)

        val starting = FreeRoomCalculator.evaluate(listOf(day("A", "14:00" to "16:00")), m("14:00"))
        assertEquals(listOf(BusyRoom(room("A"), m("16:00"), null)), starting.busy)
    }

    @Test
    fun `angrenzende und ueberlappende Spannen zaehlen als eine Belegung`() {
        val result = FreeRoomCalculator.evaluate(
            listOf(day("A", "10:00" to "12:00", "12:00" to "13:00", "12:30" to "14:00", "16:00" to "18:00")),
            m("11:00")
        )
        assertEquals(listOf(BusyRoom(room("A"), m("14:00"), m("16:00"))), result.busy)
    }

    @Test
    fun `Statustexte`() {
        assertEquals("frei bis 16:00", FreeRoomCalculator.statusText(FreeRoom(room("A"), m("16:00"))))
        assertEquals("frei, heute nichts mehr", FreeRoomCalculator.statusText(FreeRoom(room("A"), null)))
        assertEquals("belegt bis 14:00, danach frei", FreeRoomCalculator.statusText(BusyRoom(room("A"), m("14:00"), null)))
        assertEquals(
            "belegt bis 14:00, danach frei bis 16:15",
            FreeRoomCalculator.statusText(BusyRoom(room("A"), m("14:00"), m("16:15")))
        )
        assertEquals(
            "heute durchgehend belegt",
            FreeRoomCalculator.statusText(BusyRoom(room("A"), FreeRoomCalculator.END_OF_DAY, null))
        )
    }
}
