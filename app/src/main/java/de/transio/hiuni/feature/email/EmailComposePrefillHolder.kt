package de.transio.hiuni.feature.email

import de.transio.hiuni.feature.email.data.EmailEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val REPLY_DATE_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d. MMM yyyy, HH:mm", Locale.GERMAN)

/**
 * Dedupliziert ein `Re: ` (oder `RE:` / `re:`) am Anfang. Vermeidet `Re: Re: Re: …`-Ketten.
 */
internal fun ensureReplyPrefix(subject: String): String {
    val trimmed = subject.trim()
    if (trimmed.isEmpty()) return "Re: (Kein Betreff)"
    return if (trimmed.regionMatches(0, "Re:", 0, 3, ignoreCase = true)) trimmed
    else "Re: $trimmed"
}

/**
 * Dedupliziert ein `Fwd: ` (oder `FW:`, `Wg:`) am Anfang. Wir normalisieren auf `Fwd:`.
 */
internal fun ensureForwardPrefix(subject: String): String {
    val trimmed = subject.trim()
    if (trimmed.isEmpty()) return "Fwd: (Kein Betreff)"
    val prefixes = listOf("Fwd:", "Fw:", "Wg:")
    return if (prefixes.any { trimmed.regionMatches(0, it, 0, it.length, ignoreCase = true) }) trimmed
    else "Fwd: $trimmed"
}

/** Präfixt jede Zeile mit `> ` (Standard-Mail-Quote-Format). */
internal fun quoteBody(body: String): String =
    body.lineSequence().joinToString(separator = "\n") { "> $it" }

internal fun formatSentDate(instant: Instant): String =
    instant.atZone(ZoneId.systemDefault()).format(REPLY_DATE_FMT)

/**
 * Baut einen `EmailComposePrefill` für eine Reply.
 *
 * - `to` = nur die Original-From-Adresse
 * - Subject: `Re: …` (dedupliziert)
 * - Body: 3 Leerzeilen + Header-Block (`Am … schrieb …:`) + zitierter Original-Body
 * - In-Reply-To/References: die Original-Message-ID, References = vorhandene
 *   References-Chain + " " + Message-ID
 */
fun buildReplyPrefill(original: EmailEntity, bodyPlain: String?): EmailComposePrefill {
    val originalText = bodyPlain ?: ""
    val sender = original.fromName?.takeIf { it.isNotBlank() } ?: original.fromAddress
    val headerLine = "Am ${formatSentDate(original.receivedAt)} schrieb $sender:"
    val quoted = quoteBody(originalText)
    // Drei Leerzeilen am Anfang lassen den User Platz zum Tippen, bevor das Zitat
    // beginnt — Standard-Verhalten von Gmail/Apple Mail.
    val composedBody = "\n\n\n$headerLine\n$quoted"
    val newReferences = buildString {
        val prev = original.referencesHeader?.trim().orEmpty()
        if (prev.isNotEmpty()) {
            append(prev)
            if (!original.messageId.isNullOrBlank()) append(' ')
        }
        original.messageId?.takeIf { it.isNotBlank() }?.let { append(it) }
    }.trim().takeIf { it.isNotBlank() }
    return EmailComposePrefill(
        to = listOf(original.fromAddress).filter { it.isNotBlank() },
        subject = ensureReplyPrefix(original.subject),
        body = composedBody,
        inReplyTo = original.messageId?.takeIf { it.isNotBlank() },
        references = newReferences
    )
}

/**
 * Baut einen `EmailComposePrefill` für ein Forward.
 *
 * - `to` = leer (User muss Adressat eintragen)
 * - Subject: `Fwd: …` (dedupliziert)
 * - Body: Header-Block "---------- Weitergeleitete Nachricht ----------" mit
 *   Von/Datum/Betreff/An, dann unveränderter Original-Body (KEIN `> ` Zitatzeichen).
 * - In-Reply-To/References: bewusst null — Forward ist ein eigener Thread.
 */
fun buildForwardPrefill(original: EmailEntity, bodyPlain: String?): EmailComposePrefill {
    val originalText = bodyPlain ?: ""
    val sender = original.fromName?.takeIf { it.isNotBlank() }
        ?.let { "$it <${original.fromAddress}>" }
        ?: original.fromAddress
    val headerBlock = buildString {
        append("\n\n---------- Weitergeleitete Nachricht ----------\n")
        append("Von: ").append(sender).append('\n')
        append("Datum: ").append(formatSentDate(original.receivedAt)).append('\n')
        append("Betreff: ").append(original.subject.ifBlank { "(Kein Betreff)" }).append('\n')
        append("An: ").append(original.toAddresses.orEmpty()).append('\n')
        append('\n')
    }
    return EmailComposePrefill(
        to = emptyList(),
        subject = ensureForwardPrefix(original.subject),
        body = headerBlock + originalText,
        inReplyTo = null,
        references = null
    )
}

/**
 * Initialwerte für den `EmailComposeScreen`, wenn er nicht "leere neue Mail" sondern
 * "Reply" oder "Forward" einer bestehenden Mail ist.
 *
 * Beim Reply sind `inReplyTo` + `references` gesetzt, damit der SMTP-Versand
 * die RFC-5322-Threading-Header schreibt. Beim Forward bleiben beide null —
 * Forward ist ein eigener Thread.
 *
 * `body`-Text enthält bei Reply den zitierten Original-Body (jede Zeile mit `> `
 * präfixiert + Header-Block). Bei Forward einen Header-Block mit "Weitergeleitete
 * Nachricht …" plus den unveränderten Original-Body — ohne Zitatzeichen.
 */
data class EmailComposePrefill(
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val body: String,
    val inReplyTo: String? = null,
    val references: String? = null
)

/**
 * In-Memory-Übergabekanal vom `EmailScreen` (Detail-Ansicht) zum `EmailComposeViewModel`.
 *
 * Warum kein Nav-Args für die Übergabe? Reply-Bodies können sehr lang werden
 * (zitierter Original-Text + Header). Nav-Args werden in einem Bundle übergeben
 * und sind in der Größe limitiert (1MB harter Cap, in der Praxis deutlich kleiner
 * weil das gesamte Process-Bundle drüber muss). Ein @Singleton-Holder umgeht das
 * sauber.
 *
 * Wichtig — Durability: Der Holder ist NUR der Übergabekanal (config-change-sicher).
 * Prozess-Tod übersteht er NICHT (bloßer `@Volatile`-Wert). Deshalb drained das
 * `EmailComposeViewModel` den Prefill bei Erst-Erzeugung genau einmal in seinen
 * `SavedStateHandle`; ab da ist der Handle die Quelle der Wahrheit und übersteht
 * auch den Prozess-Tod.
 *
 * Lifecycle: `consume()` ist destructive — ein Reply-Prefill wird genau einmal
 * konsumiert, danach ist der nächste „Verfassen“-Tap wieder eine leere Mail.
 * Threadsafety: schlichter Volatile-Read — alle Writes laufen sequentiell aus
 * dem ViewModel-MainThread, kein Race-Risiko in der Praxis.
 */
@Singleton
class EmailComposePrefillHolder @Inject constructor() {
    @Volatile
    private var pending: EmailComposePrefill? = null

    fun set(prefill: EmailComposePrefill) {
        pending = prefill
    }

    fun consume(): EmailComposePrefill? {
        val current = pending
        pending = null
        return current
    }
}
