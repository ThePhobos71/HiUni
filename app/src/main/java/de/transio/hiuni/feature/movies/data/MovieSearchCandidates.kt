package de.transio.hiuni.feature.movies.data

/**
 * unifilm.de gibt manchmal generische Titel wie "Filmabend" oder "Sneak Preview" zurück und packt
 * den echten Filmnamen in den Subtitle. Diese Helper-Funktion baut eine geordnete Liste von
 * Titel-Kandidaten, die TMDB nacheinander probieren kann.
 */
/**
 * Titel die als generische Sammlung dienen, aber wo ein echter Film im Subtitle/Slug stecken könnte
 * (z.B. „Open Air: Citizen Kane"). Bei diesen wird Subtitle/Slug als TMDB-Hint genutzt.
 */
private val GenericTitleMarkers = listOf(
    "vorpremiere",
    "open air",
    "filmreihe",
    "spezial",
    "special",
    "double feature",
    "kinotag"
)

/**
 * Titel die explizit „Überraschungsfilm" bedeuten — kein Film bekannt, TMDB-Match wäre Müll.
 * Diese Filme werden mit Hint angezeigt und TMDB-Enrichment komplett übersprungen.
 */
private val SurpriseTitleMarkers = listOf(
    "filmabend",
    "filmnacht",
    "sneak preview",
    "sneak"
)

/**
 * True wenn der Eintrag ein Überraschungsfilm ohne realen Film-Namen ist und auch der Subtitle/Slug
 * keinen Hinweis gibt.
 */
internal fun MovieEntity.isSurpriseScreening(): Boolean {
    val lower = title.lowercase()
    val isSurpriseTitle = SurpriseTitleMarkers.any { lower.contains(it) }
    if (!isSurpriseTitle) return false
    // Wenn Subtitle einen klaren Filmtitel hat (z.B. ein anderer Filmname), ist es nicht surprise.
    val hasSubtitle = !subtitle.isNullOrBlank()
    // Bei reinem Filmabend ohne weitere Hinweise: surprise.
    return !hasSubtitle
}

/** Ob ein Text nur ein Platzhalter wie „Filmabend" ist, kein echter Filmtitel. */
private fun String.isPlaceholderTitle(): Boolean =
    lowercase().let { lower -> SurpriseTitleMarkers.any { lower.contains(it) } }

internal fun tmdbSearchCandidates(movie: MovieEntity): List<String> {
    // Überraschungsfilme NICHT enrichen, sonst rät TMDB irgendeinen falschen Film.
    if (movie.isSurpriseScreening()) return emptyList()

    val title = movie.title.trim()
    val subtitle = movie.subtitle?.trim()?.takeIf { it.isNotBlank() }
    val slug = movie.posterSlug?.trim()?.takeIf { it.isNotBlank() }

    // Platzhalter-Titel („Filmabend" mit Untertitel): nur Untertitel/Slug suchen,
    // nie den Platzhalter selbst. Die Suche nach „Filmabend" lieferte bei TMDB
    // Zufallstreffer samt fremdem Poster, Cast und Bewertung. Ist auch der
    // Untertitel bzw. Slug nur ein Platzhalter, gibt es keinen Kandidaten und die
    // App zeigt den Platzhalter-Hero.
    if (title.isPlaceholderTitle()) {
        return listOfNotNull(subtitle, slug)
            .filterNot { it.isPlaceholderTitle() }
            .distinct()
    }
    val isGeneric = title.lowercase().let { lower -> GenericTitleMarkers.any { lower.contains(it) } }

    val ordered = mutableListOf<String>()
    if (isGeneric) {
        // Bei generischen Sammeltiteln (Open Air, Filmreihe …) ist der Subtitle/Slug der echte Film.
        slug?.let { ordered += it }
        subtitle?.let { ordered += it }
        if (subtitle != null) ordered += "$title $subtitle"
        ordered += title
    } else {
        ordered += title
        slug?.let { if (!it.equals(title, ignoreCase = true)) ordered += it }
        subtitle?.let { ordered += it }
        if (subtitle != null) ordered += "$title $subtitle"
    }
    return ordered.filter { it.isNotBlank() }.distinct()
}
