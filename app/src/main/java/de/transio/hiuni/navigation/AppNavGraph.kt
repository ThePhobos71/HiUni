package de.transio.hiuni.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.nfc.NfcScanController
import de.transio.hiuni.core.notifications.NotificationDeepLinkController
import de.transio.hiuni.feature.about.ui.AboutScreen
import de.transio.hiuni.feature.bib.ui.BibScreen
import de.transio.hiuni.feature.calendar.ui.CalendarScreen
import de.transio.hiuni.feature.courses.ui.CoursesScreen
import de.transio.hiuni.feature.email.ui.EmailComposeScreen
import de.transio.hiuni.feature.email.ui.EmailScreen
import de.transio.hiuni.feature.exams.ui.ExamsScreen
import de.transio.hiuni.feature.home.ui.HomeScreen
import de.transio.hiuni.feature.mensa.ui.MensaScreen
import de.transio.hiuni.feature.mensacard.ui.MensaCardScreen
import de.transio.hiuni.feature.movies.ui.MovieDetailScreen
import de.transio.hiuni.feature.movies.ui.MoviesScreen
import de.transio.hiuni.feature.notifications.ui.NotificationsScreen
import de.transio.hiuni.feature.profile.ui.ProfileScreen
import de.transio.hiuni.feature.search.ui.GlobalSearchScreen
import de.transio.hiuni.feature.sport.ui.SportDetailScreen
import de.transio.hiuni.feature.sport.ui.SportScreen
import de.transio.hiuni.feature.settings.ui.HomeSettingsScreen
import de.transio.hiuni.feature.settings.ui.NavSettingsScreen
import de.transio.hiuni.feature.settings.ui.QuickAccessSettingsScreen
import de.transio.hiuni.feature.settings.ui.SettingsScreen
import de.transio.hiuni.feature.todos.ui.TodosScreen
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Transition-Helpers
// ---------------------------------------------------------------------------
//
// Bottom-Bar-Tabs sind peer-Ziele: nur ein schneller Crossfade, damit der
// Wechsel nicht ruckelig wirkt, aber auch nicht modal-schwer.
// Detail-/Sub-Screens (Profil, Notifications, Settings-Unterseiten, Detail-
// Screens) schieben von rechts rein und ziehen sich beim Pop wieder dahin
// zurück — Material-Standard für "tief in einer Hierarchie".

private const val TAB_FADE_MS = 150
private const val PUSH_MS = 220
private const val PUSH_FADE_OUT_MS = 120

private fun NavGraphBuilder.tabComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = { fadeIn(animationSpec = tween(TAB_FADE_MS)) },
        exitTransition = { fadeOut(animationSpec = tween(TAB_FADE_MS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(TAB_FADE_MS)) },
        popExitTransition = { fadeOut(animationSpec = tween(TAB_FADE_MS)) },
        content = content
    )
}

private fun NavGraphBuilder.pushComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth / 6 },
                animationSpec = tween(PUSH_MS)
            ) + fadeIn(animationSpec = tween(PUSH_MS))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(PUSH_FADE_OUT_MS))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(PUSH_FADE_OUT_MS))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth / 6 },
                animationSpec = tween(PUSH_MS)
            ) + fadeOut(animationSpec = tween(PUSH_MS))
        },
        content = content
    )
}

@HiltViewModel
internal class NfcNavViewModel @Inject constructor(
    nfcScanController: NfcScanController,
    notificationDeepLink: NotificationDeepLinkController
) : ViewModel() {
    val openMensaCard: SharedFlow<Unit> = nfcScanController.openMensaCard
    val openNotificationsCenter: SharedFlow<Unit> = notificationDeepLink.openCenter
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
    LaunchedEffect(Unit) {
        nfcNav.openNotificationsCenter.collect {
            // Tap auf eine OS-Notification → direkt ins Push-Center, statt
            // den User auf Home landen zu lassen.
            navController.navigate(Destination.Notifications.route) {
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
    val openSportDetail: (Long) -> Unit = { slotId ->
        navController.navigate(Destination.SportDetail.route(slotId)) {
            launchSingleTop = true
        }
    }
    NavHost(
        navController = navController,
        startDestination = Destination.Home.route,
        modifier = modifier
    ) {
        tabComposable(Destination.Home.route) {
            HomeScreen(
                onNavigate = navigate,
                onOpenMovie = openMovie,
                onOpenMensaCard = { navController.navigate(Destination.MensaCard.ROUTE) },
                onOpenCourse = openCourseByLsfId
            )
        }
        tabComposable(Destination.Calendar.route) { CalendarScreen(onOpenCourse = openCourseByLsfId) }
        tabComposable(Destination.Mensa.route) {
            MensaScreen(onOpenMensaCard = { navController.navigate(Destination.MensaCard.ROUTE) })
        }
        pushComposable(Destination.MensaCard.ROUTE) {
            MensaCardScreen(onBack = { navController.popBackStack() })
        }
        tabComposable(Destination.Movies.route) {
            MoviesScreen(onOpenMovie = openMovie)
        }
        pushComposable(
            route = Destination.MovieDetail.ROUTE_PATTERN,
            arguments = listOf(
                navArgument("filmId") { type = NavType.StringType },
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) {
            MovieDetailScreen(onBack = { navController.popBackStack() })
        }
        tabComposable(
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
        tabComposable(Destination.Bib.route) { BibScreen() }
        tabComposable(Destination.Email.route) {
            EmailScreen(
                onCompose = { navController.navigate(Destination.EmailCompose.route) }
            )
        }
        pushComposable(Destination.EmailCompose.route) {
            EmailComposeScreen(onBack = { navController.popBackStack() })
        }
        tabComposable(Destination.Todos.route) { TodosScreen() }
        tabComposable(Destination.Settings.route) {
            SettingsScreen(
                onOpenNavSettings = { navController.navigate(Destination.NavSettings.ROUTE) },
                onOpenHomeSettings = { navController.navigate(Destination.HomeSettings.ROUTE) },
                onOpenQuickAccessSettings = { navController.navigate(Destination.QuickAccessSettings.ROUTE) }
            )
        }
        pushComposable(Destination.NavSettings.ROUTE) {
            NavSettingsScreen(onBack = { navController.popBackStack() })
        }
        pushComposable(Destination.HomeSettings.ROUTE) {
            HomeSettingsScreen(onBack = { navController.popBackStack() })
        }
        pushComposable(Destination.QuickAccessSettings.ROUTE) {
            QuickAccessSettingsScreen(onBack = { navController.popBackStack() })
        }
        tabComposable(Destination.About.route) { AboutScreen() }
        pushComposable(Destination.Profile.route) {
            ProfileScreen(
                onNavigate = navigate,
                onBack = { navController.popBackStack() }
            )
        }
        pushComposable(Destination.Notifications.route) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOpenRef = navigate
            )
        }
        tabComposable(Destination.Sport.route) {
            SportScreen(onOpenDetail = openSportDetail)
        }
        pushComposable(
            route = Destination.SportDetail.ROUTE_PATTERN,
            arguments = listOf(navArgument("slotId") { type = NavType.LongType })
        ) {
            SportDetailScreen(onBack = { navController.popBackStack() })
        }
        pushComposable(Destination.Exams.route) {
            ExamsScreen(onBack = { navController.popBackStack() })
        }
        pushComposable(Destination.Search.route) {
            // Spotlight: Tap auf einen Treffer schickt den User in den passenden
            // Tab. Wir nutzen `navigate` (Tab-Stack-aware) statt `popBackStack`,
            // damit der Sprung von einem Movies-Tab in den Kalender den Calendar-
            // Tab als aktiv markiert. Search-Screen selbst poppt sich beim Tap
            // implizit, weil die Tab-Navigation den Backstack bis zum
            // Start-Destination zurückrollt.
            GlobalSearchScreen(
                onBack = { navController.popBackStack() },
                onNavigate = navigate,
                onOpenSportDetail = openSportDetail
            )
        }
    }
}
