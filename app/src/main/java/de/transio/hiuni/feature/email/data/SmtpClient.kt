package de.transio.hiuni.feature.email.data

import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.di.IoDispatcher
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Date
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMTP-Client für outbound Mail-Versand über Uni-Hildesheim Submission.
 *
 * Pendant zu [ImapClient]: nutzt dieselben RZ-Credentials aus dem [CredentialsManager],
 * dispatched IO über @IoDispatcher und wrappt jakarta.mail. Bewusst KEIN gemeinsamer
 * Session-Cache mit ImapClient — Submission und IMAP-Read sind unterschiedliche
 * Protokolle, das spätere Wiederverwenden würde nur fragile Konfig-Kopplung schaffen.
 *
 * V1: Plain-Text only, kein Attachment-Support, kein Reply-Threading.
 */
@Singleton
class SmtpClient @Inject constructor(
    private val credentials: CredentialsManager,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    sealed class SendResult {
        data object Success : SendResult()
        data class Failure(val error: Throwable) : SendResult()
    }

    suspend fun send(
        to: List<String>,
        cc: List<String> = emptyList(),
        bcc: List<String> = emptyList(),
        subject: String,
        bodyPlain: String,
        fromDisplayName: String? = null,
        host: String = DEFAULT_SMTP_HOST,
        port: Int = DEFAULT_SMTP_PORT
    ): SendResult = withContext(io) {
        val sanitizedTo = to.map { it.trim() }.filter { it.isNotBlank() }
        if (sanitizedTo.isEmpty()) {
            return@withContext SendResult.Failure(
                IllegalArgumentException("Mindestens ein Empfänger nötig")
            )
        }
        val sanitizedCc = cc.map { it.trim() }.filter { it.isNotBlank() }
        val sanitizedBcc = bcc.map { it.trim() }.filter { it.isNotBlank() }
        val effectiveSubject = subject.ifBlank { "(Kein Betreff)" }

        val (user, password) = try {
            requireCredentials()
        } catch (t: Throwable) {
            return@withContext SendResult.Failure(t)
        }

        val fromAddr = "$user@$MAIL_DOMAIN"
        Timber.i(
            "SMTP send from=$fromAddr to=${sanitizedTo.size} cc=${sanitizedCc.size} " +
                "bcc=${sanitizedBcc.size} subjLen=${effectiveSubject.length} bodyLen=${bodyPlain.length}"
        )

        try {
            val props = Properties().apply {
                put("mail.smtp.host", host)
                put("mail.smtp.port", port.toString())
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
                put("mail.smtp.ssl.trust", host)
                put("mail.smtp.ssl.checkserveridentity", "true")
                put("mail.smtp.connectiontimeout", "15000")
                put("mail.smtp.timeout", "20000")
                put("mail.smtp.writetimeout", "20000")
                put("mail.mime.charset", "UTF-8")
            }
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(user, password)
            })
            val msg = MimeMessage(session).apply {
                setFrom(InternetAddress(fromAddr, fromDisplayName, "UTF-8"))
                setRecipients(
                    Message.RecipientType.TO,
                    sanitizedTo.map { InternetAddress(it) }.toTypedArray()
                )
                if (sanitizedCc.isNotEmpty()) {
                    setRecipients(
                        Message.RecipientType.CC,
                        sanitizedCc.map { InternetAddress(it) }.toTypedArray()
                    )
                }
                if (sanitizedBcc.isNotEmpty()) {
                    setRecipients(
                        Message.RecipientType.BCC,
                        sanitizedBcc.map { InternetAddress(it) }.toTypedArray()
                    )
                }
                setSubject(effectiveSubject, "UTF-8")
                setText(bodyPlain, "UTF-8")
                sentDate = Date()
            }
            Transport.send(msg)
            Timber.i("SMTP send OK to=${sanitizedTo.first()} totalRcpts=${sanitizedTo.size + sanitizedCc.size + sanitizedBcc.size}")
            SendResult.Success
        } catch (t: Throwable) {
            // Bewusst kein Passwort/Body in der Fehlermeldung — Timber-Logs landen
            // potenziell in adb/crash-Reports.
            Timber.w(t, "SMTP send failed host=$host:$port user=$user")
            SendResult.Failure(t)
        }
    }

    private fun requireCredentials(): Pair<String, String> {
        val user = credentials.getUsername()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Keine Zugangsdaten — bitte in Settings einloggen")
        val password = credentials.getPassword()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Keine Zugangsdaten — bitte in Settings einloggen")
        return user to password
    }

    companion object {
        const val DEFAULT_SMTP_HOST = "mail.uni-hildesheim.de"
        const val DEFAULT_SMTP_PORT = 587
        const val MAIL_DOMAIN = "uni-hildesheim.de"
    }
}
