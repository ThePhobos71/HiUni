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
import de.transio.hiuni.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : EmailRepository {

    private companion object {
        const val THROTTLE_MS = 5L * 60 * 1000 // 5 Minuten
        const val ATTACHMENT_DIR = "email_attachments"
        const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".provider"
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
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EmailRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindEmailRepository(impl: EmailRepositoryImpl): EmailRepository
}
