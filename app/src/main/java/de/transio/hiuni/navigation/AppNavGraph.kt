package de.transio.hiuni.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.nfc.NfcScanController
import de.transio.hiuni.feature.about.ui.AboutScreen
import de.transio.hiuni.feature.bib.ui.BibScreen
import de.transio.hiuni.feature.calendar.ui.CalendarScreen
import de.transio.hiuni.feature.courses.ui.CoursesScreen
import de.transio.hiuni.feature.email.ui.EmailScreen
import de.transio.hiuni.feature.home.ui.HomeScreen
import de.transio.hiuni.feature.mensa.ui.MensaScreen
import de.transio.hiuni.feature.mensacard.ui.MensaCardScreen
import de.transio.hiuni.feature.movies.ui.MovieDetailScreen
import de.transio.hiuni.feature.movies.ui.MoviesScreen
import de.transio.hiuni.feature.settings.ui.HomeSettingsScreen
import de.transio.hiuni.feature.settings.ui.NavSettingsScreen
import de.transio.hiuni.feature.settings.ui.QuickAccessSettingsScreen
import de.transio.hiuni.feature.settings.ui.SettingsScreen
import de.transio.hiuni.feature.todos.ui.TodosScreen
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

@HiltViewModel
internal class NfcNavViewModel @Inject constructor(
    nfcScanController: NfcScanController
) : ViewModel() {
    val openMensaCard: SharedFlow<Unit> = nfcScanController.openMensaCard
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val nfcNav: NfcNavViewModel = hiltViewModel()
    LaunchedEffect(Unit) {
        nfcNav.openMensaCard.collect {
            // Karte angelegt während App geschlossen war → direkt zur
            // Mensa-Karten-Sicht statt User durch Home/Mensa-Tab klicken
            // zu lassen.
            navController.navigate(Destination.MensaCard.ROUTE) {
                launchSingleTop = true
            }
        }
    }
    val navigate: (Destination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val openMovie: (String, String) -> Unit = { filmId, sessionId ->
        navController.navigate(Destination.MovieDetail.route(filmId, sessionId)) {
            launchSingleTop = true
        }
    }
    // Cross-Tab-Sprung: vom Kalender direkt zur Kurs-Detail-Seite des verlinkten
    // LSF-Moduls. Bottom-Bar bleibt sichtbar, der Sprung läuft über denselben
    // Tab-Stack wie der normale Courses-Tab.
    val openCourseByLsfId: (String) -> Unit = { lsfId ->
        navController.navigate("${Destination.Courses.route}?lsfId=$lsfId") {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    NavHost(
        navController = navController,
        startDestination = Destination.Home.route,
        modifier = modifier
    ) {
        composable(Destination.Home.route) {
            HomeScreen(
                onNavigate = navigate,
                onOpenMovie = openMovie,
                onOpenMensaCard = { navController.navigate(Destination.MensaCard.ROUTE) }
            )
        }
        composable(Destination.Calendar.route) { CalendarScreen(onOpenCourse = openCourseByLsfId) }
        composable(Destination.Mensa.route) {
            MensaScreen(onOpenMensaCard = { navController.navigate(Destination.MensaCard.ROUTE) })
        }
        composable(Destination.MensaCard.ROUTE) {
            MensaCardScreen(onBack = { navController.popBackStack() })
        }
        composable(Destination.Movies.route) {
            MoviesScreen(onOpenMovie = openMovie)
        }
        composable(
            route = Destination.MovieDetail.ROUTE_PATTERN,
            arguments = listOf(
                navArgument("filmId") { type = NavType.StringType },
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) {
            MovieDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(
            // Optionaler Query-Param `lsfId`, damit Deep-Links aus dem Kalender den
            // passenden Kurs direkt selektieren. Tab-Navigation ohne Argument matched
            // den Default-Wert null und verhält sich wie bisher.
            route = "${Destination.Courses.route}?lsfId={lsfId}",
            arguments = listOf(
                navArgument("lsfId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            CoursesScreen(initialLsfId = entry.arguments?.getString("lsfId"))
        }
        composable(Destination.Bib.route) { BibScreen() }
        composable(Destination.Email.route) { EmailScreen() }
        composable(Destination.Todos.route) { TodosScreen() }
        composable(Destination.Settings.route) {
            SettingsScreen(
                onOpenNavSettings = { navController.navigate(Destination.NavSettings.ROUTE) },
                onOpenHomeSettings = { navController.navigate(Destination.HomeSettings.ROUTE) },
                onOpenQuickAccessSettings = { navController.navigate(Destination.QuickAccessSettings.ROUTE) }
            )
        }
        composable(Destination.NavSettings.ROUTE) {
            NavSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Destination.HomeSettings.ROUTE) {
            HomeSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Destination.QuickAccessSettings.ROUTE) {
            QuickAccessSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Destination.About.route) { AboutScreen() }
    }
}
