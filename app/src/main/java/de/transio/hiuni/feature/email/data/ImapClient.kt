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
    val isStarred: Boolean
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

    suspend fun fetchHeaders(
        host: String = DEFAULT_IMAP_HOST,
        port: Int = DEFAULT_IMAP_PORT,
        limit: Int = 50,
        folderName: String = "INBOX"
    ): List<ImapHeaders> = withContext(io) {
        val (user, password) = requireCredentials()
        val session = Session.getInstance(imapsProps(host, port))
        val store = session.getStore("imaps")
        store.connect(host, port, user, password)
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
                        add(UIDFolder.FetchProfileItem.UID)
                    }
                    folder.fetch(messages, profile)
                    messages.reversed().map { it.toHeaders(folder) }
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
        val session = Session.getInstance(imapsProps(host, port))
        val store = session.getStore("imaps")
        store.connect(host, port, user, password)
        val result = try {
            val folder = store.getFolder(folderName) as IMAPFolder
            folder.open(Folder.READ_ONLY)
            try {
                val msg = folder.getMessageByUID(uid)
                if (msg == null) {
                    ImapBody(uid, "", null, emptyList())
                } else {
                    val extracted = ExtractedContent()
                    extractInto(msg, extracted)
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
        val snippet = try {
            val extracted = ExtractedContent()
            extractInto(this, extracted)
            (extracted.plain.toString().ifBlank { extracted.html?.let { Jsoup.parse(it.toString()).text() }.orEmpty() })
                .take(180).replace('\n', ' ').trim()
        } catch (t: Throwable) {
            Timber.w(t, "Snippet extraction failed for uid=$uid")
            ""
        }
        return ImapHeaders(
            uid = uid,
            fromAddress = from?.address.orEmpty(),
            fromName = from?.personal,
            subject = subject.orEmpty(),
            snippet = snippet,
            receivedAt = (receivedDate ?: sentDate ?: java.util.Date()).toInstant(),
            isRead = flags.contains(jakarta.mail.Flags.Flag.SEEN),
            isStarred = flags.contains(jakarta.mail.Flags.Flag.FLAGGED)
        )
    }

    private class ExtractedContent {
        val plain = StringBuilder()
        var html: StringBuilder? = null
        val attachments = mutableListOf<EmailAttachment>()
        var attachmentCounter = 0
    }

    private fun extractInto(part: Part, target: ExtractedContent) {
        val contentType = part.contentType?.lowercase().orEmpty()
        val disposition = part.disposition?.lowercase()
        val isAttachment = disposition == Part.ATTACHMENT.lowercase() ||
            (disposition == Part.INLINE.lowercase() && !part.fileName.isNullOrBlank() &&
                !contentType.startsWith("text/"))

        if (isAttachment) {
            val rawName = part.fileName
            val name = runCatching { rawName?.let { MimeUtility.decodeText(it) } }.getOrNull()
                ?: rawName ?: "anhang-${target.attachmentCounter}"
            target.attachments += EmailAttachment(
                partIndex = target.attachmentCounter,
                filename = name,
                mimeType = contentType.substringBefore(';').trim().ifBlank { "application/octet-stream" },
                sizeBytes = part.size.toLong().coerceAtLeast(0L)
            )
            target.attachmentCounter += 1
            return
        }

        when {
            contentType.startsWith("text/plain") -> {
                target.plain.append(part.content?.toString().orEmpty()).append('\n')
            }
            contentType.startsWith("text/html") -> {
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
        val disposition = part.disposition?.lowercase()
        val isAttachment = disposition == Part.ATTACHMENT.lowercase() ||
            (disposition == Part.INLINE.lowercase() && !part.fileName.isNullOrBlank() &&
                !contentType.startsWith("text/"))
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
