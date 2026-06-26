package de.transio.hiuni.feature.email.data

import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.di.IoDispatcher
import jakarta.mail.FetchProfile
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.UIDFolder
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeUtility
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.eclipse.angus.mail.imap.IMAPFolder
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

data class ImapHeaders(
    val uid: Long,
    val fromAddress: String,
    val fromName: String?,
    val subject: String,
    val snippet: String,
    val receivedAt: Instant,
    val isRead: Boolean,
    val isStarred: Boolean,
    val toAddresses: List<String>,
    val ccAddresses: List<String>,
    val bccAddresses: List<String>,
    val hasAttachments: Boolean,
    val hasCalendarInvite: Boolean,
    /** RFC 5322 Message-ID inkl. spitzer Klammern, z.B. `<abc@host>`. Null wenn Header fehlt. */
    val messageId: String?,
    /** Whitespace-separierte Message-IDs aus dem References-Header. Null/leer wenn nicht gesetzt. */
    val referencesHeader: String?
)

data class ImapBody(
    val uid: Long,
    val plain: String,
    val html: String?,
    val attachments: List<EmailAttachment>
)

@Singleton
class ImapClient @Inject constructor(
    private val credentials: CredentialsManager,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    /**
     * Findet den Server-spezifischen Sent-Folder-Namen.
     *
     * Priorisierung:
     * 1. SPECIAL-USE Flag `\Sent` (RFC 6154) — der robusteste Weg.
     * 2. Exakter Name-Match in der bekannten Reihenfolge:
     *    `Sent`, `Gesendet`, `INBOX.Sent`, `Sent Items`.
     * 3. Fallback: literal `"Sent"` (die meisten Server akzeptieren das als Alias,
     *    oder der refresh() schlägt eben für den Sent-Tab fehl — Inbox bleibt
     *    davon unberührt).
     */
    suspend fun discoverSentFolder(
        host: String = DEFAULT_IMAP_HOST,
        port: Int = DEFAULT_IMAP_PORT
    ): String? = withContext(io) {
        val (user, password) = requireCredentials()
        val session = Session.getInstance(imapsProps(host, port))
        val store = session.getStore("imaps")
        store.connect(host, port, user, password)
        val name = try {
            val folders = store.defaultFolder.list("*")
            Timber.d("IMAP discoverSentFolder scanned ${folders.size} folders")
            // 1) SPECIAL-USE \Sent
            val viaFlag = folders.firstOrNull { f ->
                val attrs = (f as? IMAPFolder)?.attributes.orEmpty()
                attrs.any { it.equals("\\Sent", ignoreCase = true) }
            }
            if (viaFlag != null) {
                Timber.i("IMAP discoverSentFolder via SPECIAL-USE: ${viaFlag.fullName}")
                viaFlag.fullName
            } else {
                // 2) Name-Match in fester Reihenfolge
                val preferred = listOf("Sent", "Gesendet", "INBOX.Sent", "Sent Items")
                val namedMatch = preferred.firstNotNullOfOrNull { candidate ->
                    folders.firstOrNull { it.fullName.equals(candidate, ignoreCase = true) }
                }
                if (namedMatch != null) {
                    Timber.i("IMAP discoverSentFolder via name-match: ${namedMatch.fullName}")
                    namedMatch.fullName
                } else {
                    Timber.w("IMAP discoverSentFolder kein Sent-Folder entdeckt, falle auf 'Sent' zurück")
                    "Sent"
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "IMAP discoverSentFolder fehlgeschlagen")
            null
        } finally {
            runCatching { store.close() }
        }
        name
    }

    /**
     * Findet den Server-spezifischen Archive-Folder-Namen.
     *
     * Priorisierung:
     * 1. SPECIAL-USE Flag `\Archive` (RFC 6154).
     * 2. Exakter Name-Match in der Reihenfolge:
     *    `Archive`, `Archiv`, `INBOX.Archive`, `INBOX.Archiv`.
     * 3. Returnt `null` wenn nichts gefunden wurde — der Caller entscheidet,
     *    ob er das als Fehler an die UI hochreicht (z.B. "Kein Archiv-Ordner
     *    auf Server") oder einen anderen Pfad nimmt.
     */
    suspend fun discoverArchiveFolder(
        host: String = DEFAULT_IMAP_HOST,
        port: Int = DEFAULT_IMAP_PORT
    ): String? = withContext(io) {
        val (user, password) = requireCredentials()
        val session = Session.getInstance(imapsProps(host, port))
        val store = session.getStore("imaps")
        store.connect(host, port, user, password)
        val name = try {
            val folders = store.defaultFolder.list("*")
            Timber.d("IMAP discoverArchiveFolder scanned ${folders.size} folders")
            // 1) SPECIAL-USE \Archive
            val viaFlag = folders.firstOrNull { f ->
                val attrs = (f as? IMAPFolder)?.attributes.orEmpty()
                attrs.any { it.equals("\\Archive", ignoreCase = true) }
            }
            if (viaFlag != null) {
                Timber.i("IMAP discoverArchiveFolder via SPECIAL-USE: ${viaFlag.fullName}")
                viaFlag.fullName
            } else {
                // 2) Name-Match in fester Reihenfolge
                val preferred = listOf("Archive", "Archiv", "INBOX.Archive", "INBOX.Archiv")
                val namedMatch = preferred.firstNotNullOfOrNull { candidate ->
                    folders.firstOrNull { it.fullName.equals(candidate, ignoreCase = true) }
                }
                if (namedMatch != null) {
                    Timber.i("IMAP discoverArchiveFolder via name-match: ${namedMatch.fullName}")
                    namedMatch.fullName
                } else {
                    Timber.w("IMAP discoverArchiveFolder kein Archive-Folder entdeckt")
                    null
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "IMAP discoverArchiveFolder fehlgeschlagen")
            null
        } finally {
            runCatching { store.close() }
        }
        name
    }

    /**
     * Markiert die Mail mit gegebener UID in [folderName] als `\Deleted` und
     * führt EXPUNGE aus, sodass die Nachricht hart vom Server entfernt ist.
     * Wirft, wenn die Mail nicht (mehr) auf dem Server liegt — der Repository-
     * Layer fängt das als `AppResult.Failure`.
     */
    suspend fun deleteByUid(
        uid: Long,
        folderName: String = "INBOX",
        host: String = DEFAULT_IMAP_HOST,
        port: Int = DEFAULT_IMAP_PORT
    ) = withContext(io) {
        val (user, password) = requireCredentials()
        Timber.i("IMAP deleteByUid uid=$uid folder=$folderName")
        val session = Session.getInstance(imapsProps(host, port))
        val store = session.getStore("imaps")
        store.connect(host, port, user, password)
        try {
            val folder = store.getFolder(folderName) as IMAPFolder
            folder.open(Folder.READ_WRITE)
            try {
                val msg = folder.getMessageByUID(uid)
                    ?: error("Mail $uid in $folderName nicht (mehr) auf Server")
                msg.setFlag(jakarta.mail.Flags.Flag.DELETED, true)
                // EXPUNGE = endgültiges Entfernen. Ohne expunge bleibt die Mail
                // mit \Deleted-Flag liegen und taucht beim nächsten refresh()
                // wieder als "fast gelöscht" auf — das wollen wir nicht.
                folder.expunge()
                Timber.i("IMAP deleteByUid uid=$uid expunged")
            } finally {
                folder.close(false)
            }
        } finally {
            store.close()
        }
    }

    /**
     * Verschiebt die Mail mit gegebener UID von [fromFolder] nach [toFolder].
     * Bevorzugt die IMAP-MOVE-Extension (RFC 6851) via `IMAPFolder.moveMessages`,
     * fällt auf COPY + `\Deleted` + EXPUNGE zurück, wenn MOVE nicht verfügbar
     * ist oder der Server den Befehl ablehnt.
     */
    suspend fun moveByUid(
        uid: Long,
        fromFolder: String = "INBOX",
        toFolder: String,
        host: String = DEFAULT_IMAP_HOST,
        port: Int = DEFAULT_IMAP_PORT
    ) = withContext(io) {
        val (user, password) = requireCredentials()
        Timber.i("IMAP moveByUid uid=$uid from=$fromFolder to=$toFolder")
        val session = Session.getInstance(imapsProps(host, port))
        val store = session.getStore("imaps")
        store.connect(host, port, user, password)
        try {
            val src = store.getFolder(fromFolder) as IMAPFolder
            val dst = store.getFolder(toFolder) as IMAPFolder
            src.open(Folder.READ_WRITE)
            try {
                val msg = src.getMessageByUID(uid)
                    ?: error("Mail $uid in $fromFolder nicht (mehr) auf Server")
                val messages = arrayOf<Message>(msg)
                val moved = runCatching { src.moveMessages(messages, dst) }
                    .onFailure {
                        Timber.w(it, "IMAP MOVE für uid=$uid fehlgeschlagen — Fallback auf COPY+DELETE")
                    }
                    .isSuccess
                if (!moved) {
                    // Fallback für Server ohne MOVE-Extension (RFC 6851).
                    src.copyMessages(messages, dst)
                    msg.setFlag(jakarta.mail.Flags.Flag.DELETED, true)
                    src.expunge()
                    Timber.i("IMAP moveByUid uid=$uid via COPY+DELETE fallback")
                } else {
                    Timber.i("IMAP moveByUid uid=$uid via MOVE")
                }
            } finally {
                src.close(false)
            }
        } finally {
            store.close()
        }
    }

    suspend fun fetchHeaders(
        host: String = DEFAULT_IMAP_HOST,
        port: Int = DEFAULT_IMAP_PORT,
        limit: Int = 50,
        folderName: String = "INBOX"
    ): List<ImapHeaders> = withContext(io) {
        val (user, password) = requireCredentials()
        Timber.i("IMAP fetchHeaders user=$user host=$host:$port folder=$folderName limit=$limit")
        val session = Session.getInstance(imapsProps(host, port))
        val store = session.getStore("imaps")
        store.connect(host, port, user, password)
        Timber.d("IMAP connected for fetchHeaders")
        val out = try {
            val folder = store.getFolder(folderName) as IMAPFolder
            folder.open(Folder.READ_ONLY)
            try {
                val count = folder.messageCount
                if (count == 0) emptyList()
                else {
                    val start = (count - limit + 1).coerceAtLeast(1)
                    val messages = folder.getMessages(start, count)
                    val profile = FetchProfile().apply {
                        add(FetchProfile.Item.ENVELOPE)
                        add(FetchProfile.Item.FLAGS)
                        add(FetchProfile.Item.CONTENT_INFO) // BODYSTRUCTURE für Attachment-Detection
                        add(UIDFolder.FetchProfileItem.UID)
                        // Explizit für Reply-Threading: Message-ID ist zwar Teil des Envelopes,
                        // References ist es nicht. Ohne Pre-Fetch würde ein späteres
                        // getHeader("References") pro Mail einen Extra-Roundtrip auslösen.
                        add("Message-ID")
                        add("References")
                    }
                    folder.fetch(messages, profile)
                    val headers = messages.reversed().map { it.toHeaders(folder) }
                    Timber.i("IMAP fetchHeaders returned ${headers.size} mails (folderTotal=$count)")
                    headers
                }
            } finally {
                folder.close(false)
            }
        } finally {
            store.close()
        }
        out
    }

    suspend fun fetchBody(
        uid: Long,
        host: String = DEFAULT_IMAP_HOST,
        port: Int = DEFAULT_IMAP_PORT,
        folderName: String = "INBOX"
    ): ImapBody = withContext(io) {
        val (user, password) = requireCredentials()
        Timber.i("IMAP fetchBody uid=$uid")
        val session = Session.getInstance(imapsProps(host, port))
        val store = session.getStore("imaps")
        store.connect(host, port, user, password)
        val result = try {
            val folder = store.getFolder(folderName) as IMAPFolder
            folder.open(Folder.READ_ONLY)
            try {
                val msg = folder.getMessageByUID(uid)
                if (msg == null) {
                    Timber.w("IMAP fetchBody uid=$uid not found on server")
                    ImapBody(uid, "", null, emptyList())
                } else {
                    val extracted = ExtractedContent()
                    extractInto(msg, extracted)
                    Timber.i(
                        "IMAP fetchBody uid=$uid extracted plain=${extracted.plain.length}ch " +
                            "html=${extracted.html?.length ?: 0}ch attachments=${extracted.attachments.size}"
                    )
                    extracted.attachments.forEach {
                        Timber.d("  Attachment idx=${it.partIndex} name=${it.filename} mime=${it.mimeType} size=${it.sizeBytes}")
                    }
                    ImapBody(
                        uid = uid,
                        plain = extracted.plain.toString().trim(),
                        html = extracted.html?.toString()?.trim()?.takeIf { it.isNotBlank() },
                        attachments = extracted.attachments
                    )
                }
            } finally {
                folder.close(false)
            }
        } finally {
            store.close()
        }
        result
    }

    /**
     * Lädt mehrere Bodies in EINER IMAP-Session — wichtig für Background-Prefetch nach refresh().
     * Sequenziell pro UID, aber dieselbe Verbindung weiterverwendet.
     */
    suspend fun fetchBodiesBatch(
        uids: List<Long>,
        host: String = DEFAULT_IMAP_HOST,
        port: Int = DEFAULT_IMAP_PORT,
        folderName: String = "INBOX"
    ): List<ImapBody> = withContext(io) {
        if (uids.isEmpty()) return@withContext emptyList()
        val (user, password) = requireCredentials()
        Timber.i("IMAP fetchBodiesBatch n=${uids.size}")
        val session = Session.getInstance(imapsProps(host, port))
        val store = session.getStore("imaps")
        store.connect(host, port, user, password)
        val results = mutableListOf<ImapBody>()
        try {
            val folder = store.getFolder(folderName) as IMAPFolder
            folder.open(Folder.READ_ONLY)
            try {
                for (uid in uids) {
                    val msg = folder.getMessageByUID(uid)
                    if (msg == null) {
                        Timber.w("Batch fetchBody uid=$uid not found")
                        continue
                    }
                    runCatching {
                        val extracted = ExtractedContent()
                        extractInto(msg, extracted)
                        results += ImapBody(
                            uid = uid,
                            plain = extracted.plain.toString().trim(),
                            html = extracted.html?.toString()?.trim()?.takeIf { it.isNotBlank() },
                            attachments = extracted.attachments
                        )
                    }.onFailure { Timber.w(it, "Batch fetchBody uid=$uid failed") }
                }
            } finally {
                folder.close(false)
            }
        } finally {
            store.close()
        }
        Timber.i("IMAP fetchBodiesBatch returned ${results.size} bodies")
        results
    }

    suspend fun downloadAttachment(
        uid: Long,
        partIndex: Int,
        host: String = DEFAULT_IMAP_HOST,
        port: Int = DEFAULT_IMAP_PORT,
        folderName: String = "INBOX"
    ): ByteArray = withContext(io) {
        val (user, password) = requireCredentials()
        val session = Session.getInstance(imapsProps(host, port))
        val store = session.getStore("imaps")
        store.connect(host, port, user, password)
        val bytes = try {
            val folder = store.getFolder(folderName) as IMAPFolder
            folder.open(Folder.READ_ONLY)
            try {
                val msg = folder.getMessageByUID(uid) ?: error("Mail $uid nicht mehr da")
                val parts = mutableListOf<Part>()
                collectAttachmentParts(msg, parts)
                val part = parts.getOrNull(partIndex)
                    ?: error("Anhang #$partIndex nicht gefunden")
                val baos = ByteArrayOutputStream()
                val input = part.inputStream
                try {
                    input.copyTo(baos)
                } finally {
                    input.close()
                }
                baos.toByteArray()
            } finally {
                folder.close(false)
            }
        } finally {
            store.close()
        }
        bytes
    }

    private fun requireCredentials(): Pair<String, String> {
        val user = credentials.getUsername()
            ?: throw IllegalStateException("Kein IMAP-Username hinterlegt")
        val password = credentials.getPassword()
            ?: throw IllegalStateException("Kein IMAP-Passwort hinterlegt")
        return user to password
    }

    private fun imapsProps(host: String, port: Int): Properties = Properties().apply {
        put("mail.store.protocol", "imaps")
        put("mail.imaps.host", host)
        put("mail.imaps.port", port.toString())
        put("mail.imaps.ssl.enable", "true")
        put("mail.imaps.ssl.checkserveridentity", "true")
        put("mail.imaps.connectiontimeout", "15000")
        put("mail.imaps.timeout", "20000")
        put("mail.mime.charset", "UTF-8")
    }

    private fun Message.toHeaders(folder: IMAPFolder): ImapHeaders {
        val from = (from?.firstOrNull() as? InternetAddress)
        val flags = flags
        val uid = folder.getUID(this)
        // Snippet aus Text-Teilen + Attachment-Indikatoren via BODYSTRUCTURE-Walk.
        // Wir ziehen das in einem Pass damit wir die Multipart-Struktur nur einmal anfassen.
        var hasAttachments = false
        var hasCalendarInvite = false
        val snippet = try {
            val extracted = ExtractedContent()
            extractInto(this, extracted)
            hasAttachments = extracted.attachments.isNotEmpty()
            hasCalendarInvite = extracted.attachments.any {
                it.mimeType.contains("calendar", ignoreCase = true) ||
                    it.filename.endsWith(".ics", ignoreCase = true)
            }
            (extracted.plain.toString().ifBlank { extracted.html?.let { Jsoup.parse(it.toString()).text() }.orEmpty() })
                .take(180).replace('\n', ' ').trim()
        } catch (t: Throwable) {
            Timber.w(t, "Snippet extraction failed for uid=$uid")
            ""
        }
        // RFC 5322 Message-ID & References lesen — wir brauchen die für Reply-Threading.
        // getHeader() liefert ein Array (mehrere Header-Vorkommen sind theoretisch möglich,
        // praktisch ist Message-ID immer single-valued). Whitespace zwischen den IDs im
        // References-Header bleibt erhalten — die Mail-Clients erwarten genau diesen
        // Wortlaut wieder zu sehen.
        val messageIdHeader = runCatching {
            getHeader("Message-ID")?.firstOrNull()?.takeIf { it.isNotBlank() }?.trim()
        }.getOrNull()
        val referencesHeader = runCatching {
            getHeader("References")?.joinToString(" ")?.takeIf { it.isNotBlank() }?.trim()
        }.getOrNull()
        return ImapHeaders(
            uid = uid,
            fromAddress = from?.address.orEmpty(),
            fromName = from?.personal,
            subject = subject.orEmpty(),
            snippet = snippet,
            receivedAt = (receivedDate ?: sentDate ?: java.util.Date()).toInstant(),
            isRead = flags.contains(jakarta.mail.Flags.Flag.SEEN),
            isStarred = flags.contains(jakarta.mail.Flags.Flag.FLAGGED),
            toAddresses = readRecipients(Message.RecipientType.TO),
            ccAddresses = readRecipients(Message.RecipientType.CC),
            bccAddresses = readRecipients(Message.RecipientType.BCC),
            hasAttachments = hasAttachments,
            hasCalendarInvite = hasCalendarInvite,
            messageId = messageIdHeader,
            referencesHeader = referencesHeader
        )
    }

    private fun Message.readRecipients(type: Message.RecipientType): List<String> = try {
        val raw = getRecipients(type)
        val list = raw.orEmpty()
            .mapNotNull { it as? InternetAddress }
            .map { it.personal?.takeIf { p -> p.isNotBlank() }?.let { p -> "$p <${it.address}>" } ?: it.address }
            .filter { it.isNotBlank() }
        Timber.d("readRecipients type=$type rawCount=${raw?.size ?: 0} parsedCount=${list.size}: $list")
        list
    } catch (t: Throwable) {
        Timber.w(t, "readRecipients type=$type failed")
        emptyList()
    }

    private class ExtractedContent {
        val plain = StringBuilder()
        var html: StringBuilder? = null
        val attachments = mutableListOf<EmailAttachment>()
        var attachmentCounter = 0
    }

    private fun extractInto(part: Part, target: ExtractedContent) {
        val contentType = part.contentType?.lowercase().orEmpty()
        val mimeType = contentType.substringBefore(';').trim()
        val disposition = part.disposition?.lowercase()
        val isCalendar = mimeType == "text/calendar" || mimeType == "application/ics"
        val hasFilename = !part.fileName.isNullOrBlank()
        val isAttachment = disposition == Part.ATTACHMENT.lowercase() ||
            isCalendar || // ICS-Invites immer als Attachment, auch wenn Content-Type text/* ist
            (disposition == Part.INLINE.lowercase() && hasFilename && !mimeType.startsWith("text/"))

        Timber.d("Mail-Part dispo=$disposition mime=$mimeType filename=${part.fileName} isAttachment=$isAttachment")

        if (isAttachment) {
            val rawName = part.fileName
            val name = runCatching { rawName?.let { MimeUtility.decodeText(it) } }.getOrNull()
                ?: rawName
                ?: if (isCalendar) "einladung-${target.attachmentCounter}.ics"
                else "anhang-${target.attachmentCounter}"
            target.attachments += EmailAttachment(
                partIndex = target.attachmentCounter,
                filename = name,
                mimeType = mimeType.ifBlank { "application/octet-stream" },
                sizeBytes = part.size.toLong().coerceAtLeast(0L)
            )
            target.attachmentCounter += 1
            return
        }

        when {
            mimeType == "text/plain" -> {
                target.plain.append(part.content?.toString().orEmpty()).append('\n')
            }
            mimeType == "text/html" -> {
                val builder = target.html ?: StringBuilder().also { target.html = it }
                builder.append(part.content?.toString().orEmpty())
            }
            contentType.startsWith("multipart/") -> {
                val mp = part.content as? Multipart ?: return
                for (i in 0 until mp.count) {
                    extractInto(mp.getBodyPart(i), target)
                }
            }
        }
    }

    private fun collectAttachmentParts(part: Part, out: MutableList<Part>) {
        val contentType = part.contentType?.lowercase().orEmpty()
        val mimeType = contentType.substringBefore(';').trim()
        val disposition = part.disposition?.lowercase()
        val isCalendar = mimeType == "text/calendar" || mimeType == "application/ics"
        val hasFilename = !part.fileName.isNullOrBlank()
        // WICHTIG: Muss EXAKT die gleiche isAttachment-Logik wie extractInto haben,
        // sonst stimmt der partIndex zwischen Header-Fetch und Download nicht überein.
        val isAttachment = disposition == Part.ATTACHMENT.lowercase() ||
            isCalendar ||
            (disposition == Part.INLINE.lowercase() && hasFilename && !mimeType.startsWith("text/"))
        if (isAttachment) {
            out += part
            return
        }
        if (contentType.startsWith("multipart/")) {
            val mp = part.content as? Multipart ?: return
            for (i in 0 until mp.count) {
                collectAttachmentParts(mp.getBodyPart(i), out)
            }
        }
    }

    companion object {
        const val DEFAULT_IMAP_HOST = "mail.uni-hildesheim.de"
        const val DEFAULT_IMAP_PORT = 993
    }
}
