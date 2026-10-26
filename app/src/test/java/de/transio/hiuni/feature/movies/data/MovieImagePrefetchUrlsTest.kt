package de.transio.hiuni.feature.movies.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MovieImagePrefetchUrlsTest {

    private val today = LocalDate.of(2026, 10, 26)

    private fun movie(
        sessionId: String,
        title: String = "Dune",
        date: LocalDate? = today,
        isPast: Boolean = false,
        posterUrl: String? = null,
        backdropUrl: String? = null,
        subtitle: String? = null
    ) = MovieEntity(
        filmId = title,
        sessionId = sessionId,
        title = title,
        subtitle = subtitle,
        date = date,
        isPast = isPast,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl
    )

    @Test
    fun `nimmt Poster und Backdrop kommender Vorstellungen, Poster zuerst`() {
        val urls = movieImagePrefetchUrls(
            listOf(
                movie("1", title = "Dune", posterUrl = "p/dune", backdropUrl = "b/dune"),
                movie("2", title = "Alien", date = today.plusDays(3), posterUrl = "p/alien", backdropUrl = "b/alien")
            ),
            today
        )
        assertEquals(listOf("p/dune", "p/alien", "b/dune", "b/alien"), urls)
    }

    @Test
    fun `vergangene und als past markierte Vorstellungen werden uebersprungen`() {
        val urls = movieImagePrefetchUrls(
            listOf(
                movie("1", title = "Gestern", date = today.minusDays(1), posterUrl = "p/gestern"),
                movie("2", title = "Archiv", isPast = true, posterUrl = "p/archiv"),
                movie("3", title = "Ohne Datum", date = null, posterUrl = "p/ohne-datum"),
                movie("4", title = "Heute", posterUrl = "p/heute")
            ),
            today
        )
        // Sortiert nach Datum, Vorstellungen ohne Datum zuletzt.
        assertEquals(listOf("p/heute", "p/ohne-datum"), urls)
    }

    @Test
    fun `Ueberraschungsfilme werden nicht vorgeladen`() {
        val urls = movieImagePrefetchUrls(
            listOf(
                movie("1", title = "Filmabend", posterUrl = "p/filmabend", backdropUrl = "b/filmabend"),
                movie("2", title = "Dune", posterUrl = "p/dune")
            ),
            today
        )
        assertEquals(listOf("p/dune"), urls)
    }

    @Test
    fun `gleiche URLs mehrerer Vorstellungen nur einmal, leere URLs ignoriert`() {
        val urls = movieImagePrefetchUrls(
            listOf(
                movie("1", posterUrl = "p/dune", backdropUrl = "b/dune"),
                movie("2", date = today.plusDays(1), posterUrl = "p/dune", backdropUrl = "b/dune"),
                movie("3", title = "Leer", posterUrl = "  ", backdropUrl = null)
            ),
            today
        )
        assertEquals(listOf("p/dune", "b/dune"), urls)
    }
}
