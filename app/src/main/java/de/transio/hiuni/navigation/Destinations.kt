package de.transio.hiuni.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Destination("home", "Home", Icons.Filled.Home)
    data object Calendar : Destination("calendar", "Kalender", Icons.Filled.CalendarMonth)
    data object Mensa : Destination("mensa", "Mensa", Icons.Filled.LocalDining)
    data object Movies : Destination("movies", "Filme", Icons.Filled.Movie)
    data object Courses : Destination("courses", "Kurse", Icons.Filled.MenuBook)
    data object Bib : Destination("bib", "Bibliothek", Icons.Filled.LocalLibrary)
    data object Email : Destination("email", "E-Mail", Icons.Filled.Email)
    data object Todos : Destination("todos", "Aufgaben", Icons.Filled.AssignmentTurnedIn)
    data object Settings : Destination("settings", "Einstellungen", Icons.Filled.Settings)
    data object About : Destination("about", "Über", Icons.Filled.Info)
    data object Profile : Destination("profile", "Profil", Icons.Filled.Person)
    data object Notifications : Destination("notifications", "Mitteilungen", Icons.Filled.Notifications)
    data object Sport : Destination("sport", "Sport", Icons.Filled.SportsBasketball)
    data object Exams : Destination("exams", "Klausuren", Icons.Filled.AssignmentLate)

    // Sub-Action der E-Mail-Sicht — kein Tab-Ziel, daher nicht in defaultPrimary/secondary.
    data object EmailCompose : Destination("email/compose", "Verfassen", Icons.Filled.Edit)

    object MovieDetail {
        const val ROUTE_PATTERN = "movie-detail/{filmId}/{sessionId}"
        fun route(filmId: String, sessionId: String): String = "movie-detail/$filmId/$sessionId"
    }

    object SportDetail {
        const val ROUTE_PATTERN = "sport-detail/{slotId}"
        fun route(slotId: Long): String = "sport-detail/$slotId"
    }

    object NavSettings {
        const val ROUTE = "settings/nav"
    }

    object HomeSettings {
        const val ROUTE = "settings/home"
    }

    object QuickAccessSettings {
        const val ROUTE = "settings/quick-access"
    }

    object MensaCard {
        const val ROUTE = "mensa-card"
    }

    companion object {
        // Default Primary-Tabs — User kann via NavSettings überschreiben.
        val defaultPrimary: List<Destination> = listOf(Home, Calendar, Mensa, Courses, Email)

        // Settings reachable via Home-Quicktile/Cog; Movies via Home-Teaser; Bib Stub bis Phase 3.
        @Deprecated("Use NavTabsViewModel.tabs for the user-configurable list")
        val primary: List<Destination> = defaultPrimary
        val secondary: List<Destination> = listOf(Movies, Bib, Todos, Sport, Exams, Notifications, Profile, Settings, About)
        val all: List<Destination> = defaultPrimary + secondary

        fun fromRoute(route: String?): Destination? = when (route) {
            Home.route -> Home
            Calendar.route -> Calendar
            Mensa.route -> Mensa
            Movies.route -> Movies
            Courses.route -> Courses
            Bib.route -> Bib
            Email.route -> Email
            Todos.route -> Todos
            Settings.route -> Settings
            About.route -> About
            Profile.route -> Profile
            Notifications.route -> Notifications
            Sport.route -> Sport
            Exams.route -> Exams
            else -> null
        }
    }
}
