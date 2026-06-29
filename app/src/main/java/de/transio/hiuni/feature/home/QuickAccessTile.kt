package de.transio.hiuni.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.ui.graphics.vector.ImageVector

sealed class QuickAccessTile(
    val id: String,
    val label: String,
    val description: String,
    val icon: ImageVector
) {
    data object Mensa : QuickAccessTile(
        id = "mensa",
        label = "Mensa heute",
        description = "Tagesgerichte und Öffnungsstatus",
        icon = Icons.Outlined.LocalDining
    )

    data object Bib : QuickAccessTile(
        id = "bib",
        label = "Bibliothek",
        description = "Gruppenräume und nächste Buchung",
        icon = Icons.Outlined.LocalLibrary
    )

    data object Email : QuickAccessTile(
        id = "email",
        label = "Mails",
        description = "Posteingang mit Ungelesen-Zähler",
        icon = Icons.Outlined.Email
    )

    data object Tasks : QuickAccessTile(
        id = "tasks",
        label = "Aufgaben",
        description = "Offene Todos verwalten",
        icon = Icons.Outlined.CheckBox
    )

    data object Courses : QuickAccessTile(
        id = "courses",
        label = "Kurse",
        description = "Meine LSF-Veranstaltungen",
        icon = Icons.AutoMirrored.Outlined.MenuBook
    )

    data object Movies : QuickAccessTile(
        id = "movies",
        label = "Uni-Kino",
        description = "Anstehendes Programm",
        icon = Icons.Outlined.Movie
    )

    data object MensaCard : QuickAccessTile(
        id = "mensa_card",
        label = "Mensa-Karte",
        description = "Guthaben per NFC scannen",
        icon = Icons.Outlined.CreditCard
    )

    data object Sport : QuickAccessTile(
        id = "sport",
        label = "Hochschulsport",
        description = "Plan aus supersaas — Termine ansehen",
        icon = Icons.Outlined.SportsBasketball
    )

    /**
     * In-App-Liste der im Learnweb (Moodle) eingeschriebenen Kurse. Tippen
     * auf einen Kurs öffnet den Eintrag im Browser, der den CAS-SSO selbst
     * abwickelt — die App synct nur die Liste, nicht die Kursinhalte.
     */
    data object Learnweb : QuickAccessTile(
        id = "learnweb",
        label = "Learnweb",
        description = "Eingeschriebene Moodle-Kurse",
        icon = Icons.Outlined.School
    )

    companion object {
        val all: List<QuickAccessTile> = listOf(
            Mensa, Bib, Email, Tasks, Courses, Movies, MensaCard, Sport, Learnweb
        )
        val defaultVisible: List<QuickAccessTile> = listOf(Mensa, Bib, Email, Tasks)

        fun fromId(id: String): QuickAccessTile? = all.firstOrNull { it.id == id }
    }
}
