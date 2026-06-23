package de.transio.hiuni.core.notifications.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Geloggte Benachrichtigung — wird beim Feuern (NotificationReceiver) eingefügt
 * und bleibt im Push-Center sichtbar, bis der User sie als gelesen markiert oder
 * die Aufräum-Logik sie nach 30 Tagen entfernt.
 *
 * `refKey` zeigt optional auf den Quell-Eintrag (Event-ID, Mail-UID, Kurs-ID, …)
 * — als String gehalten, damit unterschiedliche Quellen ohne Foreign-Key-Geflecht
 * mitspielen können. Beim Tap kann das später für Deep-Links genutzt werden.
 */
@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["isRead", "firedAt"]),
        Index(value = ["firedAt"])
    ]
)
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: NotificationKind,
    val title: String,
    val body: String? = null,
    val firedAt: Instant = Instant.now(),
    val isRead: Boolean = false,
    val refKey: String? = null
)
