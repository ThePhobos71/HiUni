package de.transio.hiuni.feature.sport.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Ein gescrapter Eintrag aus dem supersaas-Hochschulsport-Plan.
 *
 * `supersaasSlotId` ist die stabile ID aus dem `var app=[…]`-Array und wird via
 * uniquem Index als logischer Primärschlüssel benutzt — der autogenerate-`rowId`
 * existiert nur, damit Room beim REPLACE-Upsert keine FK-Referenzen kaputt
 * macht (es gibt keine, aber Pattern matched die anderen Entities).
 *
 * Capacity-Schema: positiv = Maximalteilnehmer, negativ (z. B. -3) = das
 * supersaas-Backend hat den Slot als "fällt aus" markiert. Wir spiegeln das in
 * [isCancelled] zusätzlich, weil viele Slots stattdessen den String
 * "FÄLLT AUS!" im Titel haben.
 */
@Entity(
    tableName = "sport_events",
    indices = [
        Index("startTime"),
        Index(value = ["supersaasSlotId"], unique = true)
    ]
)
data class SportEventEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val supersaasSlotId: Long,
    val startTime: Instant,
    val endTime: Instant,
    val title: String,
    val description: String?,
    val location: String?,
    val capacity: Int,
    val currentBookings: Int,
    val waitlistCount: Int,
    val isCancelled: Boolean,
    val isPaidOnly: Boolean,
    val fetchedAt: Instant = Instant.now()
) {
    /** Freie Plätze, sofern Kapazität bekannt UND positiv. Bei Cancellation null. */
    val freeSpots: Int?
        get() = if (capacity <= 0) null else (capacity - currentBookings).coerceAtLeast(0)
}
