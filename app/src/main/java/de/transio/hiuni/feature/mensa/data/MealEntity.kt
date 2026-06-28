package de.transio.hiuni.feature.mensa.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "meals",
    primaryKeys = ["sourceId", "locationId"],
    indices = [
        Index(value = ["date", "locationId"]),
        Index(value = ["locationId"])
    ]
)
data class MealEntity(
    val sourceId: String,
    val locationId: Int,
    val date: LocalDate,
    val category: String,
    val name: String,
    val description: String?,
    val priceStudentCents: Int?,
    val priceEmployeeCents: Int?,
    val priceGuestCents: Int?,
    val tags: String,
    val co2Grams: Int? = null,
    /** Englische Übersetzung des Gerichts (`name_en` aus der STW-API). Nullable. */
    val nameEn: String? = null,
    /** Nährwert-Block per 100g — als JSON-Map gespeichert. Keys sind die API-Labels
     *  (`caloric_value`, `fat`, `protein`, `salt`, `sugar`, …), Werte die rohen Strings
     *  ("586 kJ (140 kcal)", "3,7 g") — der Detail-Screen rendert sie 1:1. */
    val nutritionalValuesJson: String? = null,
    /** Zusatzstoffe ("mit Konservierungsstoff", "mit Farbstoff", …) als comma-joined
     *  String, analog zu [tags]. Aus `tags.additives` der API. */
    val additives: String = "",
    /** Spezial-Markierungen ("aus Fleischstücken zusammengefügt", "enthält
     *  Putenformfleischkochschinken", …) als comma-joined String. Aus
     *  `special_tags` der API — das Top-Level-Feld, NICHT `tags.special`. */
    val specialTags: String = ""
) {
    val priceLabel: String
        get() = priceStudentCents?.let { "%.2f €".format(it / 100.0) } ?: ""
}
