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
    val co2Grams: Int? = null
) {
    val priceLabel: String
        get() = priceStudentCents?.let { "%.2f €".format(it / 100.0) } ?: ""
}
