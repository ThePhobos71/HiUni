package de.transio.hiuni.feature.email.data

import jakarta.mail.Flags
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Serverfreie Teile des [ImapClient]: Session-Properties und Flag-Abbildung.
 */
class ImapClientConfigTest {

    @Test
    fun `Body-Abrufe setzen nie implizit Gelesen`() {
        // BODY.PEEK statt BODY: auch wenn ein Ordner einmal READ_WRITE geöffnet ist,
        // darf das Laden des Inhalts (Öffnen, Prefetch, Anhang) kein \Seen setzen.
        // Gelesen wird nur bewusst über setFlag/setSeen markiert.
        val props = ImapClient.imapsProps("mail.example.org", 993)
        assertEquals("true", props.getProperty("mail.imaps.peek"))
        assertEquals("mail.example.org", props.getProperty("mail.imaps.host"))
        assertEquals("993", props.getProperty("mail.imaps.port"))
    }

    @Test
    fun `MailFlag bildet auf die IMAP-Systemflags ab`() {
        assertEquals(Flags.Flag.SEEN, MailFlag.SEEN.imapFlag)
        assertEquals(Flags.Flag.FLAGGED, MailFlag.FLAGGED.imapFlag)
    }
}
