package de.transio.hiuni.feature.email.data

import android.content.Context
import androidx.core.content.FileProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.common.runCatchingApp
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.core.notifications.data.NotificationKind
import de.transio.hiuni.core.notifications.data.NotificationLogRepository
import de.transio.hiuni.di.ApplicationScope
import de.transio.hiuni.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class EmailBodyResult(
    val plain: String?,
    val html: String?,
    val attachments: List<EmailAttachment>
)

interface EmailRepository {
    fun observeInbox(): Flow<List<EmailEntity>>
    fun observeStarred(): Flow<List<EmailEntity>>
    suspend fun loadBody(rowId: Long): EmailBodyResult?
    suspend fun loadIcsInvite(email: EmailEntity, attachment: EmailAttachment): IcsInvite?
    suspend fun markRead(rowId: Long, read: Boolean = true)
    suspend fun toggleStar(email: EmailEntity)
    suspend fun downloadAttachment(email: EmailEntity, attachment: EmailAttachment): File
    suspend fun shareableUri(file: File): android.net.Uri
    suspend fun refresh(force: Boolean = false): AppResult<Unit>
}

@Singleton
class EmailRepositoryImpl @Inject constructor(
    private val dao: EmailDao,
    private val imap: ImapClient,
    private val settings: SettingsDataStore,
    private val notificationLog: NotificationLogRepository,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope
) : EmailRepository {

    private companion object {
        const val THROTTLE_MS = 5L * 60 * 1000 // 5 Minuten
        const val ATTACHMENT_DIR = "email_attachments"
        const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".provider"
        const val PREFETCH_LIMIT = 20
    }

    override fun observeInbox(): Flow<List<EmailEntity>> =
        dao.observeFolder(EmailEntity.FOLDER_INBOX)

    override fun observeStarred(): Flow<List<EmailEntity>> = dao.observeStarred()

    override suspend fun loadBody(rowId: Long): EmailBodyResult? {
        val entity = dao.findByRowId(rowId) ?: return null
        if (!entity.bodyPlain.isNullOrBlank() || !entity.bodyHtml.isNullOrBlank()) {
            return EmailBodyResult(
                plain = entity.bodyPlain,
                html = entity.bodyHtml,
                attachments = EmailAttachments.decode(entity.attachmentsJson)
            )
        }
        val body = imap.fetchBody(entity.uid)
        val attachmentsJson = EmailAttachments.encode(body.attachments)
        dao.setBody(rowId, body.plain, body.html, attachmentsJson)
        return EmailBodyResult(plain = body.plain, html = body.html, attachments = body.attachments)
    }

    override suspend fun markRead(rowId: Long, read: Boolean) {
        dao.setRead(rowId, read)
    }

    override suspend fun toggleStar(email: EmailEntity) {
        dao.setStarred(email.rowId, !email.isStarred)
    }

    override suspend fun loadIcsInvite(email: EmailEntity, attachment: EmailAttachment): IcsInvite? = withContext(io) {
        if (!attachment.mimeType.contains("calendar", ignoreCase = true) &&
            !attachment.filename.endsWith(".ics", ignoreCase = true)
        ) {
            Timber.d("loadIcsInvite skipped — attachment ${attachment.filename} mime=${attachment.mimeType}")
            return@withContext null
        }
        Timber.i("loadIcsInvite downloading uid=${email.uid} part=${attachment.partIndex} ${attachment.filename}")
        val bytes = imap.downloadAttachment(email.uid, attachment.partIndex)
        val text = bytes.toString(Charsets.UTF_8)
        val invite = IcsParser.parse(text)
        Timber.i("loadIcsInvite parsed: $invite")
        invite
    }

    override suspend fun downloadAttachment(email: EmailEntity, attachment: EmailAttachment): File = withContext(io) {
        val bytes = imap.downloadAttachment(email.uid, attachment.partIndex)
        val dir = File(context.cacheDir, ATTACHMENT_DIR).apply { mkdirs() }
        val safeName = attachment.filename
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "attachment_${email.uid}_${attachment.partIndex}" }
        val target = File(dir, "${email.uid}-${attachment.partIndex}-$safeName")
        target.writeBytes(bytes)
        target
    }

    override suspend fun shareableUri(file: File): android.net.Uri =
        FileProvider.getUriForFile(
            context,
            context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
            file
        )

    override suspend fun refresh(force: Boolean): AppResult<Unit> = runCatchingApp {
        if (!force) {
            val lastSync = settings.lastEmailSyncEpoch.first()
            val age = System.currentTimeMillis() - lastSync
            if (lastSync > 0 && age < THROTTLE_MS) return@runCatchingApp
        }
        val headers = imap.fetchHeaders()
        val existingByUid = dao.knownUids(EmailEntity.FOLDER_INBOX).toSet()
        val mapped = headers.map { h ->
            EmailEntity(
                rowId = 0L,
                uid = h.uid,
                folder = EmailEntity.FOLDER_INBOX,
                fromAddress = h.fromAddress,
                fromName = h.fromName,
                subject = h.subject,
                snippet = h.snippet,
                bodyPlain = null,
                bodyHtml = null,
                attachmentsJson = null,
                toAddresses = h.toAddresses.takeIf { it.isNotEmpty() }?.joinToString(", "),
                ccAddresses = h.ccAddresses.takeIf { it.isNotEmpty() }?.joinToString(", "),
                bccAddresses = h.bccAddresses.takeIf { it.isNotEmpty() }?.joinToString(", "),
                hasAttachments = h.hasAttachments,
                hasCalendarInvite = h.hasCalendarInvite,
                receivedAt = h.receivedAt,
                isRead = h.isRead,
                isStarred = h.isStarred
            )
        }
        val toInsert = mapped.filter { it.uid !in existingByUid }
        if (toInsert.isNotEmpty()) dao.upsert(toInsert)
        val serverUids = mapped.map { it.uid }
        if (serverUids.isNotEmpty()) dao.pruneNotIn(EmailEntity.FOLDER_INBOX, serverUids)
        settings.setLastEmailSyncEpoch(System.currentTimeMillis())

        // Push-Center-Log nur ab dem zweiten Sync — der initiale Inbox-Pull nach
        // Install/Login ist kein "Neue Mail ist da"-Ereignis. Außerdem nur ungelesene
        // zählen: gelesene Server-Status (z.B. parallel im Webmail markiert) sollten
        // den User nicht ins Center spammen.
        val freshUnread = toInsert.count { !it.isRead }
        if (existingByUid.isNotEmpty() && freshUnread > 0) {
            val title = if (freshUnread == 1) "Neue E-Mail" else "$freshUnread neue E-Mails"
            val body = toInsert.firstOrNull { !it.isRead }?.let { mail ->
                val from = mail.fromName?.takeIf { it.isNotBlank() } ?: mail.fromAddress
                "$from · ${mail.subject}"
            }
            notificationLog.log(
                kind = NotificationKind.MAIL,
                title = title,
                body = body,
                refKey = "email_inbox_sync"
            )
        }

        // Background-Prefetch: lade Bodies der Top-N pending Mails in einer einzigen
        // IMAP-Session. Damit ist beim Tap meist schon alles im Cache.
        appScope.launch { prefetchBodies(PREFETCH_LIMIT) }
    }

    private suspend fun prefetchBodies(limit: Int) {
        val pending = dao.pendingBodies(EmailEntity.FOLDER_INBOX, limit)
        if (pending.isEmpty()) return
        Timber.i("Body-Prefetch starting for ${pending.size} mails")
        val bodies = runCatching { imap.fetchBodiesBatch(pending.map { it.uid }) }
            .onFailure { Timber.w(it, "Body-Prefetch IMAP-Fetch failed") }
            .getOrNull() ?: return
        for (body in bodies) {
            val entity = dao.findByUid(EmailEntity.FOLDER_INBOX, body.uid) ?: continue
            dao.setBody(
                entity.rowId,
                body.plain,
                body.html,
                EmailAttachments.encode(body.attachments)
            )
        }
        Timber.i("Body-Prefetch persisted ${bodies.size} bodies")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EmailRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindEmailRepository(impl: EmailRepositoryImpl): EmailRepository
}
