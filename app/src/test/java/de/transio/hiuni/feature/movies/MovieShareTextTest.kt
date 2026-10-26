package de.transio.hiuni.feature.movies

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class MovieShareTextTest {

    @Test
    fun `Titel mit Datum und Uhrzeit`() {
        assertEquals(
            "Hey, im Uni-Kino läuft „Sweeney Todd“. Wollen wir da hin? Ist am Freitag, 30. Oktober um 20:30 Uhr.",
            movieShareText("Sweeney Todd", isSurprise = false, LocalDate.of(2026, 10, 30), LocalTime.of(20, 30))
        )
    }

    @Test
    fun `ohne Uhrzeit nur das Datum`() {
        assertEquals(
            "Hey, im Uni-Kino läuft „Dune“. Wollen wir da hin? Ist am Freitag, 30. Oktober.",
            movieShareText("Dune", isSurprise = false, LocalDate.of(2026, 10, 30), null)
        )
    }

    @Test
    fun `ohne Termin nur die Frage`() {
        assertEquals(
            "Hey, im Uni-Kino läuft „Dune“. Wollen wir da hin?",
            movieShareText("Dune", isSurprise = false, null, null)
        )
    }

    @Test
    fun `Ueberraschungsfilm ohne Titel`() {
        assertEquals(
            "Hey, im Uni-Kino läuft ein Überraschungsfilm. Wollen wir da hin? Ist am Freitag, 30. Oktober um 20:30 Uhr.",
            movieShareText("geheim", isSurprise = true, LocalDate.of(2026, 10, 30), LocalTime.of(20, 30))
        )
    }
}
