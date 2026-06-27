package de.transio.hiuni.ui.responsive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import de.transio.hiuni.feature.settings.NavTabsViewModel
import de.transio.hiuni.navigation.Destination

@Composable
fun AdaptiveScaffold(
    navController: NavHostController,
    windowSizeClass: WindowSizeClass,
    navTabsViewModel: NavTabsViewModel = hiltViewModel(),
    content: @Composable (PaddingValues) -> Unit
) {
    val navigationType = NavigationType.fromWindowSize(windowSizeClass)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val primaryTabs by navTabsViewModel.tabs.collectAsStateWithLifecycle()

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
    val drawerColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = colors.primaryContainer,
        selectedIconColor = colors.primary,
        selectedTextColor = colors.primary,
        // Unselected ohne sichtbaren Hintergrund — der Default der aktuellen M3-Version
        // rendert hier eine surfaceContainerLow-Pille (deutlich graue Backplate),
        // was unselected Items wie ein hängender Hover-State aussehen lässt.
        unselectedContainerColor = Color.Transparent,
        unselectedIconColor = colors.onSurfaceVariant,
        unselectedTextColor = colors.onSurfaceVariant
    )

    when (navigationType) {
        NavigationType.BOTTOM_NAVIGATION -> {
            Scaffold(
                containerColor = colors.background,
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
            ) { padding -> content(padding) }
        }

        NavigationType.NAVIGATION_RAIL -> {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = colors.surface,
                    modifier = Modifier.verticalScroll(rememberScrollState())
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
                Scaffold(containerColor = colors.background) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        AdaptiveContentBox { content(PaddingValues(0.dp)) }
                    }
                }
            }
        }

        NavigationType.PERMANENT_DRAWER -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(drawerContainerColor = colors.surface) {
                        // 14 Destinations passen auf einem ~800dp-hohen Tablet-Display nicht
                        // alle gleichzeitig in voller 56dp-Höhe — Column scrollbar machen,
                        // damit die Items nicht zerquetscht werden müssen.
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Destination.all.forEach { dest ->
                                NavigationDrawerItem(
                                    selected = isSelected(currentRoute, dest),
                                    onClick = { onSelect(dest) },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(dest.label) },
                                    colors = drawerColors,
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                            }
                        }
                    }
                }
            ) {
                Scaffold(containerColor = colors.background) { padding ->
                    AdaptiveContentBox { content(padding) }
                }
            }
        }
    }
}

private fun isSelected(currentRoute: String?, destination: Destination): Boolean =
    currentRoute == destination.route
