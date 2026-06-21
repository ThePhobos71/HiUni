package de.transio.hiuni.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Destination("home", "Home", Icons.Filled.Home)
    data object Calendar : Destination("calendar", "Kalender", Icons.Filled.CalendarMonth)
    data object Mensa : Destination("mensa", "Mensa", Icons.Filled.LocalDining)
    data object Movies : Destination("movies", "Filme", Icons.Filled.Movie)
    data object Bib : Destination("bib", "Bibliothek", Icons.Filled.LocalLibrary)
    data object Email : Destination("email", "E-Mail", Icons.Filled.Email)
    data object Settings : Destination("settings", "Einstellungen", Icons.Filled.Settings)
    data object About : Destination("about", "Über", Icons.Filled.Info)

    companion object {
        val primary: List<Destination> = listOf(Home, Calendar, Mensa, Movies, Bib, Email)
        val secondary: List<Destination> = listOf(Settings, About)
        val all: List<Destination> = primary + secondary
    }
}
