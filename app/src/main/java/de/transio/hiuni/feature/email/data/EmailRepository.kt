package de.transio.hiuni.feature.email.data

import android.content.Context
import androidx.core.content.FileProvider
import androidx.sqlite.db.SimpleSQLiteQuery
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
import de.transio.hiuni.feature.email.EmailFolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

/** Eintrag im Compose-Autocomplete: bekannte Adresse mit optionalem Anzeige-Namen. */
data class EmailContact(val address: String, val name: String?)

interface EmailRepository {
    fun observeInbox(): Flow<List<EmailEntity>>
    fun observeSent(): Flow<List<EmailEntity>>
    fun observeArchived(): Flow<List<EmailEntity>>
    fun observeStarred(): Flow<List<EmailEntity>>
    /**
     * Volltext-Suche scoped auf den jeweiligen Folder (Markiert: isStarred=1 statt
     * folder-Filter). Bei leerem/blank Query delegiert die Funktion an
     * `observeInbox/observeSent/observeStarred` — die UI muss nicht selbst switchen.
     */
    fun observeSearch(folder: EmailFolder, query: String): Flow<List<EmailEntity>>
    /** Alle bekannten Kontakte aus From/To/Cc der letzten 500 Mails — dedupliziert. */
    fun observeKnownContacts(): Flow<List<EmailContact>>
    suspend fun loadBody(rowId: Long): EmailBodyResult?
    suspend fun loadIcsInvite(email: EmailEntity, attachment: EmailAttachment): IcsInvite?
    suspend fun markRead(rowId: Long, read: Boolean = true)
    suspend fun toggleStar(email: EmailEntity)
    suspend fun downloadAttachment(email: EmailEntity, attachment: EmailAttachment): File
    suspend fun shareableUri(file: File): android.net.Uri
    suspend fun refresh(force: Boolean = false): AppResult<Unit>
    suspend fun sendMail(
        to: List<String>,
        cc: List<String> = emptyList(),
        bcc: List<String> = emptyList(),
        subject: String,
        body: String,
        /**
         * RFC 5322 Message-ID der Mail, auf die geantwortet wird (inkl. spitzer Klammern).
         * Default null → kein Reply-Header. Bei Forward bewusst NICHT setzen, sonst
         * landet die weitergeleitete Mail im Original-Thread.
         */
        inReplyTo: String? = null,
        /**
         * RFC 5322 References-Header: Whitespace-separierte Message-ID-Liste, üblicherweise
         * `originalReferences + " " + originalMessageId`. Damit kann Mail-Client
         * server-seitig den Thread korrekt aufbauen.
         */
        references: String? = null
    ): AppResult<Unit>

    /**
     * Löscht die Mail hart: erst Server (`\Deleted` + EXPUNGE), dann lokal. Wenn der
     * Server-Call scheitert, bleibt die lokale Zeile bewusst stehen — sonst würde der
     * nächste Sync sie eh wiederherstellen.
     */
    suspend fun deleteEmail(email: EmailEntity): AppResult<Unit>

    /**
     * Verschiebt die Mail in den Archive-Folder. Wenn der Server keinen Archive-Folder
     * hat (Discovery returnt null), liefert die Funktion `AppResult.Failure` — die UI
     * zeigt dann eine Snackbar wie "Kein Archiv-Ordner auf Server".
     */
    suspend fun archiveEmail(email: EmailEntity): AppResult<Unit>
}

@Singleton
class EmailRepositoryImpl @Inject constructor(
    private val dao: EmailDao,
    private val imap: ImapClient,
    private val smtp: SmtpClient,
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
        const val SENT_FETCH_LIMIT = 100
    }

    /**
     * In-Memory-Cache des Server-spezifischen Sent-Folder-Namens (z.B. "Sent",
     * "Gesendet", "INBOX.Sent"). Wir resolven einmal pro Process-Lifetime —
     * bei Auth-Change wird der Process eh recreated, ein expliziter Invalidate
     * ist also nicht nötig.
     */
    @Volatile private var sentServerFolder: String? = null

    /**
     * In-Memory-Cache des Server-spezifischen Archive-Folder-Namens. Im Gegensatz
     * zu Sent kann das Archive-Discovery legitim `null` ergeben (Server hat keinen
     * Archive-Folder konfiguriert) — der Cache merkt sich diesen Zustand NICHT,
     * damit wir bei erneutem Versuch (z.B. nach Mailbox-Konfiguration im Webmail)
     * wieder neu fragen können. Erfolgreiche Discovery wird gecached.
     */
    @Volatile private var archiveServerFolder: String? = null

    private suspend fun resolveServerFolder(logicalFolder: String): String = when (logicalFolder) {
        EmailEntity.FOLDER_INBOX -> "INBOX"
        EmailEntity.FOLDER_SENT -> sentServerFolder ?: run {
            val resolved = imap.discoverSentFolder() ?: "Sent"
            sentServerFolder = resolved
            resolved
        }
        EmailEntity.FOLDER_ARCHIVE -> archiveServerFolder ?: run {
            val resolved = imap.discoverArchiveFolder()
                ?: error("Kein Archive-Folder auf Server verfügbar")
            archiveServerFolder = resolved
            resolved
        }
        else -> logicalFolder
    }

    /**
     * Resolver speziell fürs Archivieren: returnt den Server-Folder-Namen oder `null`,
     * wenn der Server kein Archive bietet. Im Erfolgsfall wird der Name gecached.
     * Anders als [resolveServerFolder] wirft das hier NICHT, weil archiveEmail() den
     * Failure-Fall sauber als `AppResult.Failure` propagieren muss.
     */
    private suspend fun resolveArchiveServerFolder(): String? {
        archiveServerFolder?.let { return it }
        val discovered = imap.discoverArchiveFolder() ?: return null
        archiveServerFolder = discovered
        return discovered
    }

    override fun observeInbox(): Flow<List<EmailEntity>> =
        dao.observeFolder(EmailEntity.FOLDER_INBOX)

    override fun observeSent(): Flow<List<EmailEntity>> =
        dao.observeFolder(EmailEntity.FOLDER_SENT)

    override fun observeArchived(): Flow<List<EmailEntity>> =
        dao.observeFolder(EmailEntity.FOLDER_ARCHIVE)

    override fun observeStarred(): Flow<List<EmailEntity>> = dao.observeStarred()

    override fun observeSearch(folder: EmailFolder, query: String): Flow<List<EmailEntity>> {
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            return when (folder) {
                EmailFolder.INBOX -> observeInbox()
                EmailFolder.SENT -> observeSent()
                EmailFolder.ARCHIVE -> observeArchived()
                EmailFolder.STARRED -> observeStarred()
            }
        }
        // Wir nutzen `@RawQuery` + `SimpleSQLiteQuery`, weil die Anzahl der Tokens zur
        // Compile-Zeit unbekannt ist. SQL-Struktur ist statisch (LIKE-Klauseln werden
        // programmatisch zusammengesetzt), Tokenwerte fließen ausschließlich als
        // gebundene `?`-Args — damit ist SQL-Injection ausgeschlossen.
        val args = mutableListOf<Any>()
        val scopeClause = when (folder) {
            EmailFolder.INBOX -> { args.add(EmailEntity.FOLDER_INBOX); "folder = ?" }
            EmailFolder.SENT -> { args.add(EmailEntity.FOLDER_SENT); "folder = ?" }
            EmailFolder.ARCHIVE -> { args.add(EmailEntity.FOLDER_ARCHIVE); "folder = ?" }
            EmailFolder.STARRED -> "isStarred = 1"
        }
        val tokenClauses = tokens.joinToString(separator = " AND ") { token ->
            val pattern = "%$token%"
            // 4 Spalten → 4× das gleiche Pattern als Arg
            repeat(4) { args.add(pattern) }
            "(subject LIKE ? COLLATE NOCASE " +
                "OR fromName LIKE ? COLLATE NOCASE " +
                "OR fromAddress LIKE ? COLLATE NOCASE " +
                "OR bodyPlain LIKE ? COLLATE NOCASE)"
        }
        val sql = "SELECT * FROM emails WHERE $scopeClause AND isHiddenLocally = 0 " +
            "AND $tokenClauses ORDER BY receivedAt DESC LIMIT 200"
        return dao.searchRaw(SimpleSQLiteQuery(sql, args.toTypedArray()))
    }

    override fun observeKnownContacts(): Flow<List<EmailContact>> =
        dao.observeKnownAddressRows().map { rows ->
            // Reihenfolge: jüngste Mail zuerst (vom Query) → erste Bekanntschaft mit
            // einer Adresse bleibt durch distinctBy erhalten. Der erste Treffer hat
            // i.d.R. auch den freundlichsten From-Namen.
            buildList {
                rows.forEach { row ->
                    add(EmailContact(row.fromAddress, row.fromName))
                    row.toAddresses?.let { joined ->
                        joined.splitAddressesForAutocomplete().forEach { addr ->
                            add(EmailContact(addr, null))
                        }
                    }
                    row.ccAddresses?.let { joined ->
                        joined.splitAddressesForAutocomplete().forEach { addr ->
                            add(EmailContact(addr, null))
                        }
                    }
                }
            }
                .asSequence()
                .map { it.copy(address = it.address.trim()) }
                .filter { it.address.contains('@') && it.address.length > 3 }
                .distinctBy { it.address.lowercase() }
                .toList()
        }

    /** Splits "Max <max@x>, max2@x" → ["max@x", "max2@x"]. Display-Name-Brackets werden gestrippt. */
    private fun String.splitAddressesForAutocomplete(): List<String> =
        this.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { raw ->
                val angleStart = raw.indexOf('<')
                val angleEnd = raw.indexOf('>')
                if (angleStart >= 0 && angleEnd > angleStart) {
                    raw.substring(angleStart + 1, angleEnd).trim()
                } else raw
            }

    override suspend fun loadBody(rowId: Long): EmailBodyResult? {
        val entity = dao.findByRowId(rowId) ?: return null
        if (!entity.bodyPlain.isNullOrBlank() || !entity.bodyHtml.isNullOrBlank()) {
            return EmailBodyResult(
                plain = entity.bodyPlain,
                html = entity.bodyHtml,
                attachments = EmailAttachments.decode(entity.attachmentsJson)
            )
        }
        // WICHTIG: Sent-Mails leben nicht in INBOX. Server-Folder resolven, sonst
        // schlägt fetchBody mit "Mail nicht gefunden" fehl.
        val serverFolder = resolveServerFolder(entity.folder)
        val body = imap.fetchBody(entity.uid, folderName = serverFolder)
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
        val serverFolder = resolveServerFolder(email.folder)
        val bytes = imap.downloadAttachment(email.uid, attachment.partIndex, folderName = serverFolder)
        val text = bytes.toString(Charsets.UTF_8)
        val invite = IcsParser.parse(text)
        Timber.i("loadIcsInvite parsed: $invite")
        invite
    }

    override suspend fun downloadAttachment(email: EmailEntity, attachment: EmailAttachment): File = withContext(io) {
        val serverFolder = resolveServerFolder(email.folder)
        val bytes = imap.downloadAttachment(email.uid, attachment.partIndex, folderName = serverFolder)
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
        val mapped = headers.map { h -> h.toEntity(EmailEntity.FOLDER_INBOX) }
        val toInsert = mapped.filter { it.uid !in existingByUid }
        if (toInsert.isNotEmpty()) dao.upsert(toInsert)
        val serverUids = mapped.map { it.uid }
        if (serverUids.isNotEmpty()) dao.pruneNotIn(EmailEntity.FOLDER_INBOX, serverUids)
        // Sent-Folder sync — Best-Effort: Failure hier soll den Inbox-Sync nicht
        // entwerten (Inbox-Headers/Prune sind oben schon committed).
        runCatching { syncSent() }.onFailure { Timber.w(it, "Sent-Sync fehlgeschlagen") }
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

    override suspend fun sendMail(
        to: List<String>,
        cc: List<String>,
        bcc: List<String>,
        subject: String,
        body: String,
        inReplyTo: String?,
        references: String?
    ): AppResult<Unit> {
        // SmtpClient liefert eine sealed SendResult — wir mappen auf AppResult, damit
        // die Aufrufer (ViewModel) das gleiche Pattern wie bei refresh() nutzen können.
        // Kein DB-Write in einen lokalen "sent"-Folder: der Submission-Server kopiert
        // die Nachricht typischerweise via Auto-BCC in den IMAP-Sent-Folder, und falls
        // nicht, taucht sie beim nächsten Inbox-Refresh ohnehin nicht auf (anderes
        // Folder). Wir vermeiden so doppelte Quellen-of-truth in v1.
        return when (val result = smtp.send(
            to = to,
            cc = cc,
            bcc = bcc,
            subject = subject,
            bodyPlain = body,
            inReplyTo = inReplyTo,
            references = references
        )) {
            is SmtpClient.SendResult.Success -> {
                val totalRcpts = to.size + cc.size + bcc.size
                Timber.i("Mail gesendet an ${to.firstOrNull().orEmpty()}, $totalRcpts Empfänger")
                // Post-Send-Refresh fire-and-forget: damit die gerade gesendete Mail
                // sofort im Sent-Tab auftaucht statt erst beim nächsten Periodic-Sync.
                // force=true umgeht den 5-Minuten-Throttle.
                appScope.launch {
                    runCatching { refresh(force = true) }
                        .onFailure { Timber.w(it, "Post-Send-Refresh fehlgeschlagen") }
                }
                AppResult.Success(Unit)
            }
            is SmtpClient.SendResult.Failure -> AppResult.Failure(result.error)
        }
    }

    /**
     * Sent-Folder-Sync. Discovery → Fetch → Upsert mit logischem Folder-Wert
     * `FOLDER_SENT` (NICHT der Server-Name — sonst kann die UI nicht einheitlich
     * filtern, wenn der Server-Name z.B. "Gesendet" ist). Prune analog zur Inbox.
     */
    private suspend fun syncSent() {
        val serverFolder = resolveServerFolder(EmailEntity.FOLDER_SENT)
        val headers = imap.fetchHeaders(folderName = serverFolder, limit = SENT_FETCH_LIMIT)
        val existingByUid = dao.knownUids(EmailEntity.FOLDER_SENT).toSet()
        val mapped = headers.map { h -> h.toEntity(EmailEntity.FOLDER_SENT) }
        val toInsert = mapped.filter { it.uid !in existingByUid }
        if (toInsert.isNotEmpty()) dao.upsert(toInsert)
        val serverUids = mapped.map { it.uid }
        if (serverUids.isNotEmpty()) dao.pruneNotIn(EmailEntity.FOLDER_SENT, serverUids)
        Timber.i("Sent-Sync persisted: total=${mapped.size} new=${toInsert.size} server=$serverFolder")
    }

    private fun ImapHeaders.toEntity(folder: String): EmailEntity = EmailEntity(
        rowId = 0L,
        uid = uid,
        folder = folder,
        fromAddress = fromAddress,
        fromName = fromName,
        subject = subject,
        snippet = snippet,
        bodyPlain = null,
        bodyHtml = null,
        attachmentsJson = null,
        toAddresses = toAddresses.takeIf { it.isNotEmpty() }?.joinToString(", "),
        ccAddresses = ccAddresses.takeIf { it.isNotEmpty() }?.joinToString(", "),
        bccAddresses = bccAddresses.takeIf { it.isNotEmpty() }?.joinToString(", "),
        hasAttachments = hasAttachments,
        hasCalendarInvite = hasCalendarInvite,
        receivedAt = receivedAt,
        isRead = isRead,
        isStarred = isStarred,
        messageId = messageId,
        referencesHeader = referencesHeader
    )

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

    override suspend fun deleteEmail(email: EmailEntity): AppResult<Unit> = runCatchingApp {
        // „Nur lokal löschen"-Modus: User möchte die Mail aus der App entfernen,
        // sie soll aber auf dem IMAP-Server liegen bleiben (z.B. für Web-Mail
        // oder andere Geräte). Wir setzen isHiddenLocally statt zu deleten —
        // sonst würde der nächste IMAP-Sync die Mail wieder reinpullen.
        val localOnly = settings.mailDeleteLocalOnly.first()
        if (localOnly) {
            dao.setHiddenLocally(email.rowId, true)
            Timber.i("deleteEmail (local-only) rowId=${email.rowId} uid=${email.uid} folder=${email.folder}")
            return@runCatchingApp
        }
        // Reihenfolge ist wichtig: erst Server, dann lokal. Wenn der Server-Call
        // wirft, bleibt die lokale Zeile bestehen — sonst würde die Mail beim
        // nächsten refresh() vom Server wieder synced werden und der User sähe
        // sie "geistermäßig" wiederkehren.
        val serverFolder = resolveServerFolder(email.folder)
        imap.deleteByUid(uid = email.uid, folderName = serverFolder)
        dao.deleteByRowId(email.rowId)
        Timber.i("deleteEmail rowId=${email.rowId} uid=${email.uid} folder=${email.folder} done")
    }

    override suspend fun archiveEmail(email: EmailEntity): AppResult<Unit> = runCatchingApp {
        val archive = resolveArchiveServerFolder()
            ?: throw IllegalStateException("Kein Archiv-Ordner auf Server")
        val source = resolveServerFolder(email.folder)
        if (source.equals(archive, ignoreCase = true)) {
            // Schon im Archiv — no-op statt sinnlosem MOVE auf sich selbst, was
            // bei manchen Servern fehlschlagen kann.
            Timber.i("archiveEmail rowId=${email.rowId} bereits im Archiv ($archive) — skip")
            return@runCatchingApp
        }
        imap.moveByUid(uid = email.uid, fromFolder = source, toFolder = archive)
        // Lokal in den logischen Archive-Folder umetikettieren. Der refresh()
        // synced aktuell nur INBOX + SENT — bis das Archive separat synced wird,
        // ist der lokale Marker die einzige Quelle für "diese Mail ist archiviert".
        dao.markFolderByRowId(email.rowId, EmailEntity.FOLDER_ARCHIVE)
        Timber.i("archiveEmail rowId=${email.rowId} uid=${email.uid} ${email.folder} -> $archive done")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EmailRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindEmailRepository(impl: EmailRepositoryImpl): EmailRepository
}
