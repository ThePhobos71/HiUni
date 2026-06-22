package de.transio.hiuni.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Destination("home", "Home", Icons.Filled.Home)
    data object Calendar : Destination("calendar", "Kalender", Icons.Filled.CalendarMonth)
    data object Mensa : Destination("mensa", "Mensa", Icons.Filled.LocalDining)
    data object Movies : Destination("movies", "Filme", Icons.Filled.Movie)
    data object Courses : Destination("courses", "Kurse", Icons.Filled.MenuBook)
    data object Bib : Destination("bib", "Bibliothek", Icons.Filled.LocalLibrary)
    data object Email : Destination("email", "E-Mail", Icons.Filled.Email)
    data object Settings : Destination("settings", "Einstellungen", Icons.Filled.Settings)
    data object About : Destination("about", "Über", Icons.Filled.Info)

    object MovieDetail {
        const val ROUTE_PATTERN = "movie-detail/{filmId}/{sessionId}"
        fun route(filmId: String, sessionId: String): String = "movie-detail/$filmId/$sessionId"
    }

    object NavSettings {
        const val ROUTE = "settings/nav"
    }

    companion object {
        // Default Primary-Tabs — User kann via NavSettings überschreiben.
        val defaultPrimary: List<Destination> = listOf(Home, Calendar, Mensa, Courses, Email)

        // Settings reachable via Home-Quicktile/Cog; Movies via Home-Teaser; Bib Stub bis Phase 3.
        @Deprecated("Use NavTabsViewModel.tabs for the user-configurable list")
        val primary: List<Destination> = defaultPrimary
        val secondary: List<Destination> = listOf(Movies, Bib, Settings, About)
        val all: List<Destination> = defaultPrimary + secondary

        fun fromRoute(route: String?): Destination? = when (route) {
            Home.route -> Home
            Calendar.route -> Calendar
            Mensa.route -> Mensa
            Movies.route -> Movies
            Courses.route -> Courses
            Bib.route -> Bib
            Email.route -> Email
            Settings.route -> Settings
            About.route -> About
            else -> null
        }
    }
}
