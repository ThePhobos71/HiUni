package de.transio.hiuni.ui.responsive

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

enum class NavigationType {
    BOTTOM_NAVIGATION,
    NAVIGATION_RAIL;

    companion object {
        /**
         * Phone (Compact) → Bottom-Bar.
         * Medium + Tablet (Expanded) → linke Rail mit den vom User in
         * NavSettings konfigurierten und umordbaren Primary-Tabs.
         *
         * Vorher: Expanded → PermanentDrawer mit ALLEN 14 Destinations. War
         * unhandlich (zeigte z.B. "About" und "Mensa-Karte" gleichberechtigt
         * neben Home) und ignorierte die Tab-Anpassung aus den Settings.
         */
        fun fromWindowSize(windowSizeClass: WindowSizeClass): NavigationType =
            when (windowSizeClass.widthSizeClass) {
                WindowWidthSizeClass.Compact -> BOTTOM_NAVIGATION
                WindowWidthSizeClass.Medium -> NAVIGATION_RAIL
                WindowWidthSizeClass.Expanded -> NAVIGATION_RAIL
                else -> BOTTOM_NAVIGATION
            }
    }
}
