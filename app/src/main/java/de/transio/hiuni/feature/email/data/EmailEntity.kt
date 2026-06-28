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
    val toAddresses: String? = null,
    val ccAddresses: String? = null,
    val bccAddresses: String? = null,
    val hasAttachments: Boolean = false,
    val hasCalendarInvite: Boolean = false,
    val receivedAt: Instant,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    /**
     * RFC 5322 Message-ID Header der Mail (inkl. der spitzen Klammern, z.B.
     * `<abc.def@example.com>`). Wird für In-Reply-To/References beim Antworten
     * benötigt, damit Mail-Clients server-seitig den Thread erkennen.
     * Null für Bestands-Mails vor dem messageId-Sync (Migration 25→26).
     */
    val messageId: String? = null,
    /**
     * Original-References-Header der eingegangenen Mail (whitespace-separated
     * Message-IDs). Beim Antworten hängen wir die eigene Reply-Message-ID hinten an.
     */
    val referencesHeader: String? = null,
    /**
     * Lokales „Soft-Delete"-Flag. Wird gesetzt, wenn der User die Mail bei
     * aktiviertem „nur lokal löschen"-Setting wegwischt — die Mail bleibt auf
     * dem IMAP-Server, verschwindet aber aus allen lokalen Listen. Nächster
     * Sync würde sie sonst wieder neu pullen, deshalb behalten wir die Row
     * und filtern via Flag statt sie zu löschen.
     */
    val isHiddenLocally: Boolean = false
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
        /**
         * Logischer Sent-Folder-Name. Server-seitig variiert das (Sent/Gesendet/INBOX.Sent/…),
         * intern speichern wir IMMER unter "Sent" damit DAO/UI einheitlich filtern können.
         * Discovery des echten Server-Namens passiert in [ImapClient.discoverSentFolder].
         */
        const val FOLDER_SENT = "Sent"

        /**
         * Logischer Archive-Folder-Name. Server-seitig variiert das (Archive/Archiv/INBOX.Archive/…),
         * intern speichern wir IMMER unter "Archive" damit DAO/UI einheitlich filtern können.
         * Discovery des echten Server-Namens passiert in [ImapClient.discoverArchiveFolder].
         */
        const val FOLDER_ARCHIVE = "Archive"
    }
}
