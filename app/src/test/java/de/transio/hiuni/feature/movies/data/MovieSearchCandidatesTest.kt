package de.transio.hiuni.feature.movies.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieSearchCandidatesTest {

    private fun movie(title: String, subtitle: String? = null, posterSlug: String? = null) = MovieEntity(
        filmId = "1",
        sessionId = "1",
        title = title,
        subtitle = subtitle,
        posterSlug = posterSlug
    )

    @Test
    fun `surprise screening without subtitle returns no candidates - no TMDB guesswork`() {
        val candidates = tmdbSearchCandidates(movie("Filmabend"))
        assertEquals(emptyList<String>(), candidates)
    }

    @Test
    fun `surprise marker with subtitle is treated as known film`() {
        val candidates = tmdbSearchCandidates(movie("Filmabend", "Tausend Sterne"))
        assertEquals("Filmabend", candidates.first())
        assertTrue("Should contain subtitle candidate", candidates.contains("Tausend Sterne"))
    }

    @Test
    fun `non-generic title stays primary`() {
        val candidates = tmdbSearchCandidates(movie("Stadt am Meer", "Heimkehr"))
        assertEquals("Stadt am Meer", candidates.first())
    }

    @Test
    fun `missing subtitle returns title only`() {
        val candidates = tmdbSearchCandidates(movie("Nachtschicht"))
        assertEquals(listOf("Nachtschicht"), candidates)
    }

    @Test
    fun `case-insensitive sneak preview is surprise`() {
        val candidates = tmdbSearchCandidates(movie("SNEAK PREVIEW"))
        assertEquals(emptyList<String>(), candidates)
    }

    @Test
    fun `generic collection title Open Air puts subtitle first`() {
        val candidates = tmdbSearchCandidates(movie("Open Air", "Citizen Kane"))
        assertEquals("Citizen Kane", candidates.first())
    }

    @Test
    fun `poster slug used as candidate when different from title`() {
        val candidates = tmdbSearchCandidates(
            movie(title = "Fluch der Karibik", posterSlug = "pirates of the caribbean fluch der karibik")
        )
        assertEquals("Fluch der Karibik", candidates.first())
        assertTrue("Slug should be a candidate", candidates.contains("pirates of the caribbean fluch der karibik"))
    }
}
