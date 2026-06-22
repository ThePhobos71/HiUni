package de.transio.hiuni.feature.email.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "emails",
    indices = [
        Index(value = ["folder", "receivedAt"]),
        Index(value = ["folder", "uid"], unique = true)
    ]
)
data class EmailEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0L,
    val uid: Long,
    val folder: String,
    val fromAddress: String,
    val fromName: String?,
    val subject: String,
    val snippet: String,
    val bodyPlain: String?,
    val bodyHtml: String? = null,
    val attachmentsJson: String? = null,
    val receivedAt: Instant,
    val isRead: Boolean = false,
    val isStarred: Boolean = false
) {
    val displayFrom: String get() = fromName?.takeIf { it.isNotBlank() } ?: fromAddress

    val initials: String
        get() = displayFrom
            .split(' ', '.', '-', '@')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }

    companion object {
        const val FOLDER_INBOX = "INBOX"
    }
}
