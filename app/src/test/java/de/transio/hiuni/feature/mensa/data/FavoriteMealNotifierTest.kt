package de.transio.hiuni.feature.mensa.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Reine Entscheidungslogik des Lieblingsgericht-Alarms, ohne Android und ohne Netz. */
class FavoriteMealNotifierTest {

    private fun meal(name: String, date: LocalDate, loc: Int = 152) = MealEntity(
        sourceId = "$name-$date",
        locationId = loc,
        date = date,
        category = "Mittag",
        name = name,
        description = null,
        priceStudentCents = null,
        priceEmployeeCents = null,
        priceGuestCents = null,
        tags = ""
    )

    private val today = LocalDate.of(2026, 11, 16) // Montag
    private val thu = LocalDate.of(2026, 11, 19)

    @Test
    fun `neuer Treffer ergibt eine Mitteilung mit Stichwort und Tag`() {
        val d = FavoriteMealNotifier.decide(
            listOf("Currywurst"),
            listOf(meal("Currywurst mit Pommes frites", thu)),
            emptySet(),
            today
        )
        assertEquals("Currywurst am Donnerstag", d.push!!.title)
        assertEquals("Currywurst mit Pommes frites", d.push!!.body)
        assertEquals(setOf("152|currywurst|2026-11-19"), d.notified)
    }

    @Test
    fun `bereits gemeldeter Treffer meldet nicht erneut`() {
        val d = FavoriteMealNotifier.decide(
            listOf("Currywurst"),
            listOf(meal("Currywurst", thu)),
            setOf("152|currywurst|2026-11-19"),
            today
        )
        assertNull(d.push)
    }

    @Test
    fun `Gericht verschwindet und kommt am selben Tag wieder ohne zweite Mitteilung`() {
        val gone = FavoriteMealNotifier.decide(
            listOf("Currywurst"),
            emptyList(),
            setOf("152|currywurst|2026-11-19"),
            today
        )
        assertTrue("152|currywurst|2026-11-19" in gone.notified)
        val back = FavoriteMealNotifier.decide(
            listOf("Currywurst"),
            listOf(meal("Currywurst", thu)),
            gone.notified,
            today
        )
        assertNull(back.push)
    }

    @Test
    fun `andere Mensa gilt als neuer Treffer`() {
        val d = FavoriteMealNotifier.decide(
            listOf("Currywurst"),
            listOf(meal("Currywurst", thu, loc = 160)),
            setOf("152|currywurst|2026-11-19"),
            today
        )
        assertNotNull(d.push)
    }

    @Test
    fun `Gross und Kleinschreibung egal, Umlaut wird nicht aufgeloest`() {
        assertTrue(FavoriteMealNotifier.matches("currywurst", meal("CURRYWURST vom Schwein", thu)))
        assertFalse(FavoriteMealNotifier.matches("kaese", meal("Käsespätzle", thu)))
    }

    @Test
    fun `Stichwort trifft nur am Wortanfang, nicht mitten im Wort`() {
        // "Eis" steckt in Reis, Fleisch und Speise: das waere ein Dauer-Alarm.
        assertFalse(FavoriteMealNotifier.matches("Eis", meal("Schweinefleisch mit Reis", thu)))
        assertFalse(FavoriteMealNotifier.matches("Eis", meal("Tagesspeise", thu)))
        assertTrue(FavoriteMealNotifier.matches("Eis", meal("Vanille-Eis mit Sahne", thu)))
        assertTrue(FavoriteMealNotifier.matches("Eis", meal("Eisbecher", thu)))
        assertTrue(FavoriteMealNotifier.matches("Currywurst", meal("Vegane Currywurst (Soja)", thu)))
        assertTrue(FavoriteMealNotifier.matches("Lasagne Bolognese", meal("Hausgemachte Lasagne Bolognese", thu)))
    }

    @Test
    fun `leeres Stichwort trifft nichts`() {
        assertFalse(FavoriteMealNotifier.matches("  ", meal("Currywurst", thu)))
    }

    @Test
    fun `mehrere neue Treffer werden gebuendelt`() {
        val d = FavoriteMealNotifier.decide(
            listOf("Currywurst", "Lasagne"),
            listOf(meal("Currywurst", thu), meal("Lasagne Bolognese", today.plusDays(1))),
            emptySet(),
            today
        )
        assertEquals("2 Lieblingsgerichte im Speiseplan", d.push!!.title)
        assertEquals("Lasagne morgen, Currywurst am Donnerstag", d.push!!.body)
    }

    @Test
    fun `zwei Gerichte am selben Tag zum selben Stichwort zaehlen einmal`() {
        val d = FavoriteMealNotifier.decide(
            listOf("Currywurst"),
            listOf(meal("Currywurst", thu), meal("Vegane Currywurst", thu)),
            emptySet(),
            today
        )
        assertEquals(1, d.newHits.size)
    }

    @Test
    fun `vergangene Schluessel und geloeschte Stichwoerter werden aufgeraeumt`() {
        val d = FavoriteMealNotifier.decide(
            listOf("Lasagne"),
            emptyList(),
            setOf("152|lasagne|2026-11-10", "152|currywurst|2026-11-19"),
            today
        )
        assertTrue(d.notified.isEmpty())
    }

    @Test
    fun `notify false schreibt nur fort`() {
        val d = FavoriteMealNotifier.decide(
            listOf("Currywurst"),
            listOf(meal("Currywurst", thu)),
            emptySet(),
            today,
            notify = false
        )
        assertNull(d.push)
        assertEquals(1, d.notified.size)
    }

    @Test
    fun `Treffer heute heisst heute`() {
        val d = FavoriteMealNotifier.decide(
            listOf("Currywurst"),
            listOf(meal("Currywurst", today)),
            emptySet(),
            today
        )
        assertEquals("Currywurst heute", d.push!!.title)
    }

    @Test
    fun `Treffer in 8 Tagen nennt das Datum`() {
        val d = FavoriteMealNotifier.decide(
            listOf("Currywurst"),
            listOf(meal("Currywurst", LocalDate.of(2026, 11, 24))),
            emptySet(),
            today
        )
        assertEquals("Currywurst am Dienstag, 24.11.", d.push!!.title)
    }

    @Test
    fun `Vorschlag kuerzt auf das Hauptgericht`() {
        assertEquals("Currywurst", FavoriteMealNotifier.suggestKeyword("Currywurst mit Pommes frites"))
        assertEquals("Lasagne Bolognese", FavoriteMealNotifier.suggestKeyword("Lasagne Bolognese (Rind)"))
    }

    @Test
    fun `nextHits liefert je Stichwort das frueheste Gericht`() {
        val hits = FavoriteMealNotifier.nextHits(
            listOf("Currywurst", "Lasagne"),
            listOf(meal("Currywurst", thu), meal("Currywurst", today.plusDays(1)))
        )
        assertEquals(today.plusDays(1), hits["Currywurst"]!!.date)
        assertNull(hits["Lasagne"])
    }
}
