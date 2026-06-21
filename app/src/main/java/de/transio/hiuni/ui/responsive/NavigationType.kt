package de.transio.hiuni.ui.responsive

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

enum class NavigationType {
    BOTTOM_NAVIGATION,
    NAVIGATION_RAIL,
    PERMANENT_DRAWER;

    companion object {
        fun fromWindowSize(windowSizeClass: WindowSizeClass): NavigationType =
            when (windowSizeClass.widthSizeClass) {
                WindowWidthSizeClass.Compact -> BOTTOM_NAVIGATION
                WindowWidthSizeClass.Medium -> NAVIGATION_RAIL
                WindowWidthSizeClass.Expanded -> PERMANENT_DRAWER
                else -> BOTTOM_NAVIGATION
            }
    }
}
