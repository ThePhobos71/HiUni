package de.transio.hiuni.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Today
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
        description = "Deine LSF-Veranstaltungen und Termine von heute",
        icon = Icons.Outlined.Today
    )

    data object Exams : HomeSection(
        id = "exams",
        label = "Klausuren",
        description = "Anstehende Prüfungen aus LSF",
        icon = Icons.Outlined.EventAvailable
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
        description = "Nächste fällige Todos",
        icon = Icons.Outlined.CheckBox
    )


    companion object {
        val all: List<HomeSection> = listOf(QuickAccess, Today, Exams, Films, Todos)
        val defaultVisible: List<HomeSection> = listOf(QuickAccess, Today, Exams, Films)

        fun fromId(id: String): HomeSection? = all.firstOrNull { it.id == id }
    }
}
