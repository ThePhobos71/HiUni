package de.transio.hiuni.feature.movies.data

import io.mockk.mockk
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class MovieScraperTest {

    private val scraper = MovieScraper(client = mockk())

    @Test
    fun `parses film entry with matching showcase via data-id and data-sid`() {
        val html = """
            <html><body>
              <ul>
                <li class="film" data-id="123" data-sid="456">
                  <img src="https://example.com/poster.jpg" />
                </li>
              </ul>
              <div class="film-showcase" data-id="123" data-sid="456">
                <h1 class="headline-h3">
                  <span>Stadt am Meer</span>
                  <span class="headline-normalcase">Heimkehr</span>
                </h1>
                <ul class="film-info-termin">
                  <li class="datum">Di, 19.05.2026</li>
                  <li class="uhrzeit">20:00</li>
                  <li class="raum">Audimax</li>
                </ul>
                <ul class="film-info-filmdaten">
                  <li>R: Anna Beispiel</li>
                  <li>FSK 12</li>
                  <li>DE, FR</li>
                  <li>112 Min.</li>
                  <li>Drama</li>
                </ul>
                <div class="film-info-text">
                  <p>Ein junger Architekt kehrt zurück.</p>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val movies = scraper.parse(Jsoup.parse(html, "https://www.unifilm.de/studentenkinos/Hildesheim"))
        assertEquals(1, movies.size)
        val movie = movies.first()

        assertEquals("123", movie.filmId)
        assertEquals("456", movie.sessionId)
        assertEquals("Stadt am Meer", movie.title)
        assertEquals("Heimkehr", movie.subtitle)
        assertEquals(LocalDate.of(2026, 5, 19), movie.date)
        assertEquals(LocalTime.of(20, 0), movie.time)
        assertEquals("Audimax", movie.location)
        assertEquals("Anna Beispiel", movie.director)
        assertEquals("FSK 12", movie.fsk)
        assertEquals("DE, FR", movie.country)
        assertEquals(112, movie.durationMinutes)
        assertEquals("Drama", movie.genre)
        assertTrue("description should include the paragraph", movie.description!!.contains("Architekt"))
    }

    @Test
    fun `skips entries without matching showcase`() {
        val html = """
            <html><body>
              <ul>
                <li class="film" data-id="999" data-sid="0"></li>
              </ul>
            </body></html>
        """.trimIndent()
        val movies = scraper.parse(Jsoup.parse(html, "https://www.unifilm.de/studentenkinos/Hildesheim"))
        assertTrue(movies.isEmpty())
    }

    @Test
    fun `marks past entries via film-past class`() {
        val html = """
            <html><body>
              <ul>
                <li class="film film-past" data-id="1" data-sid="2"></li>
              </ul>
              <div class="film-showcase" data-id="1" data-sid="2">
                <h1 class="headline-h3"><span>Alter Film</span></h1>
              </div>
            </body></html>
        """.trimIndent()
        val movies = scraper.parse(Jsoup.parse(html, "https://www.unifilm.de/studentenkinos/Hildesheim"))
        assertEquals(1, movies.size)
        assertTrue(movies.first().isPast)
    }

    @Test
    fun `parses partial date without year using current year as fallback`() {
        val html = """
            <html><body>
              <ul>
                <li class="film" data-id="7" data-sid="7"></li>
              </ul>
              <div class="film-showcase" data-id="7" data-sid="7">
                <h1 class="headline-h3"><span>Test</span></h1>
                <ul class="film-info-termin">
                  <li class="datum">22.06.</li>
                </ul>
              </div>
            </body></html>
        """.trimIndent()
        val movies = scraper.parse(Jsoup.parse(html, "https://www.unifilm.de/studentenkinos/Hildesheim"))
        assertNotNull(movies.first().date)
        assertEquals(6, movies.first().date!!.monthValue)
        assertEquals(22, movies.first().date!!.dayOfMonth)
    }
}
