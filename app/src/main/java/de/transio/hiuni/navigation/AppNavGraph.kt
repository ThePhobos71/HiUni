package de.transio.hiuni.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.transio.hiuni.feature.about.ui.AboutScreen
import de.transio.hiuni.feature.bib.ui.BibScreen
import de.transio.hiuni.feature.calendar.ui.CalendarScreen
import de.transio.hiuni.feature.email.ui.EmailScreen
import de.transio.hiuni.feature.home.ui.HomeScreen
import de.transio.hiuni.feature.mensa.ui.MensaScreen
import de.transio.hiuni.feature.movies.ui.MoviesScreen
import de.transio.hiuni.feature.settings.ui.SettingsScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Destination.Home.route,
        modifier = modifier
    ) {
        composable(Destination.Home.route) { HomeScreen() }
        composable(Destination.Calendar.route) { CalendarScreen() }
        composable(Destination.Mensa.route) { MensaScreen() }
        composable(Destination.Movies.route) { MoviesScreen() }
        composable(Destination.Bib.route) { BibScreen() }
        composable(Destination.Email.route) { EmailScreen() }
        composable(Destination.Settings.route) { SettingsScreen() }
        composable(Destination.About.route) { AboutScreen() }
    }
}
