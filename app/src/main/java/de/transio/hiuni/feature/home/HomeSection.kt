package de.transio.hiuni.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.ui.graphics.vector.ImageVector

sealed class HomeSection(
    val id: String,
    val label: String,
    val description: String,
    val icon: ImageVector
) {
    data object QuickAccess : HomeSection(
        id = "quick_access",
        label = "Schnellzugriff",
        description = "Mensa, Bibliothek, Mails, Aufgaben",
        icon = Icons.Outlined.GridView
    )

    data object Today : HomeSection(
        id = "today",
        label = "Heute",
        description = "Aktuelle Mensa-Gerichte als Karten",
        icon = Icons.Outlined.LocalDining
    )

    data object Films : HomeSection(
        id = "films",
        label = "Uni-Kino",
        description = "Anstehende Filme im Audimax",
        icon = Icons.Outlined.Movie
    )

    data object Todos : HomeSection(
        id = "todos",
        label = "Offene Aufgaben",
        description = "Demo-Liste — noch ohne Backend",
        icon = Icons.Outlined.CheckBox
    )

    data object News : HomeSection(
        id = "news",
        label = "Neuigkeiten",
        description = "Demo-Meldungen — noch ohne Backend",
        icon = Icons.AutoMirrored.Outlined.Article
    )

    companion object {
        val all: List<HomeSection> = listOf(QuickAccess, Today, Films, Todos, News)
        val defaultVisible: List<HomeSection> = listOf(QuickAccess, Films)

        fun fromId(id: String): HomeSection? = all.firstOrNull { it.id == id }
    }
}
