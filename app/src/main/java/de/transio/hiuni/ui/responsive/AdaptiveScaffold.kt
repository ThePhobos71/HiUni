package de.transio.hiuni.ui.responsive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import de.transio.hiuni.core.design.components.OfflineBanner
import de.transio.hiuni.core.network.ConnectivityViewModel
import de.transio.hiuni.feature.settings.NavTabsViewModel
import de.transio.hiuni.navigation.Destination

@Composable
fun AdaptiveScaffold(
    navController: NavHostController,
    windowSizeClass: WindowSizeClass,
    navTabsViewModel: NavTabsViewModel = hiltViewModel(),
    connectivityViewModel: ConnectivityViewModel = hiltViewModel(),
    content: @Composable (PaddingValues) -> Unit
) {
    val navigationType = NavigationType.fromWindowSize(windowSizeClass)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val primaryTabs by navTabsViewModel.tabs.collectAsStateWithLifecycle()
    val isOnline by connectivityViewModel.isOnline.collectAsStateWithLifecycle()

    // Das Offline-Banner wird EINMAL zentral über der NavHost-Ebene eingehängt —
    // dadurch erbt es jeder Screen, ohne dass er selbst Netz-Logik kennt. Die
    // Leiste trägt selbst statusBarsPadding, damit ihr Text nicht unter der
    // System-Uhr klebt; sie schiebt (nur solange offline) die darunterliegende
    // Screen-Ebene nach unten. Die Screen-Header padden ihrerseits erneut die
    // Statusbar — im Offline-Fall entsteht dadurch ein kleiner Doppel-Abstand
    // über dem Header. Bewusst in Kauf genommen, um Per-Screen-Umbau zu vermeiden.
    val bannerContent: @Composable (PaddingValues) -> Unit = { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            OfflineBanner(
                visible = !isOnline,
                modifier = Modifier.statusBarsPadding(),
            )
            content(padding)
        }
    }

    val onSelect: (Destination) -> Unit = { dest ->
        if (currentRoute != dest.route) {
            navController.navigate(dest.route) {
                // Tab-Klick poppt alles bis zum Root (inkl. Detail-Screens wie MovieDetail).
                // saveState/restoreState bewusst NICHT — sonst landet ein offener Detail-Screen
                // beim nächsten Re-Tap des Tabs wieder oben drauf.
                popUpTo(navController.graph.startDestinationId) {
                    saveState = false
                    inclusive = false
                }
                launchSingleTop = true
                restoreState = false
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    val navBarColors = NavigationBarItemDefaults.colors(
        selectedIconColor = colors.primary,
        selectedTextColor = colors.primary,
        unselectedIconColor = colors.onSurfaceVariant,
        unselectedTextColor = colors.onSurfaceVariant,
        indicatorColor = colors.primaryContainer
    )
    val railColors = NavigationRailItemDefaults.colors(
        selectedIconColor = colors.primary,
        selectedTextColor = colors.primary,
        unselectedIconColor = colors.onSurfaceVariant,
        unselectedTextColor = colors.onSurfaceVariant,
        indicatorColor = colors.primaryContainer
    )
    when (navigationType) {
        NavigationType.BOTTOM_NAVIGATION -> {
            Scaffold(
                containerColor = colors.background,
                // Top-Inset bewusst NICHT reservieren — die Screen-Header zeichnen ihre
                // Surface bis y=0 durch die Status-Bar und padden ihren Text via
                // statusBarsPadding(). Sonst entsteht ein grauer Strip zwischen
                // System-Statusbar und Header.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar(containerColor = colors.surface) {
                        primaryTabs.forEach { dest ->
                            NavigationBarItem(
                                selected = isSelected(currentRoute, dest),
                                onClick = { onSelect(dest) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                colors = navBarColors
                            )
                        }
                    }
                }
            ) { padding -> bannerContent(padding) }
        }

        NavigationType.NAVIGATION_RAIL -> {
            Row(modifier = Modifier.fillMaxSize()) {
                // Rail füllt die VOLLE Höhe (sonst stoppt sie bei content-Height
                // und es entsteht ein weißer Strip unter den Tab-Items).
                // Kein verticalScroll: 5 Tabs (Max-Konstante) passen immer.
                NavigationRail(
                    containerColor = colors.surface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    primaryTabs.forEach { dest ->
                        NavigationRailItem(
                            selected = isSelected(currentRoute, dest),
                            onClick = { onSelect(dest) },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                            colors = railColors
                        )
                    }
                }
                // weight(1f) macht das Scaffold den gesamten Rest der Row
                // füllen — sonst kollabiert es auf intrinsische Breite und
                // ein schwarzer Streifen bleibt rechts ungenutzt.
                // AdaptiveContentBox bewusst RAUS hier: auf Tablet wollen wir
                // den Platz voll nutzen, kein 1100dp-Cap mit grauer Box drumherum.
                Scaffold(
                    containerColor = colors.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.weight(1f)
                ) { padding -> bannerContent(padding) }
            }
        }

    }
}

private fun isSelected(currentRoute: String?, destination: Destination): Boolean =
    currentRoute == destination.route
