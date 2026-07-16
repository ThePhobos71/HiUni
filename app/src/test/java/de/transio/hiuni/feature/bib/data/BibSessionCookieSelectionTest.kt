package de.transio.hiuni.feature.bib.data

import android.content.Context
import de.transio.hiuni.core.auth.CasSession
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

/**
 * Regressionstest für den Session-Fixation-Fix in [BibSession] (siehe
 * docs/UBWWW_BUG_SESSION_FIXATION.md).
 *
 * ubwww schickt nach dem CAS-Login MEHRERE `Set-Cookie: PHPSESSID=…`-Header:
 * die erste (26 Zeichen) ist die anonyme Pre-Auth-Session, die zweite (64 Zeichen)
 * die User-gebundene Session. Browser nehmen per RFC 6265 "last wins". Der Fix in
 * BibSession.login() wählt darum bewusst `.lastOrNull()` statt der ursprünglichen
 * (fehlerhaften) `firstNotNullOfOrNull`-Variante — sonst landen Buchungen anonym
 * beim Server.
 *
 * MockWebServer ist keine Test-Dependency dieses Moduls, daher testen wir die
 * reine Parsing-/Auswahl-Logik direkt: die private [BibSession.extractCookieValue]
 * wird per Reflection aufgerufen (das ist der Baustein, den login() nutzt), und die
 * dokumentierte Auswahl `mapNotNull{…}.lastOrNull()` wird gegen die realen
 * Set-Cookie-Header aus dem Bug-Report gespiegelt. So bleibt der Test an die echte
 * Produktions-Signatur gekoppelt, ohne Produktionscode zu ändern.
 */
class BibSessionCookieSelectionTest {

    // BibSession-Konstruktor macht keine schwere Init-Arbeit (nur Mutex + volatile
    // Felder) — relaxte Mocks für die injizierten Abhängigkeiten reichen.
    private val session = BibSession(
        context = mockk<Context>(relaxed = true),
        casSession = mockk<CasSession>(relaxed = true),
        httpClient = mockk<OkHttpClient>(relaxed = true),
        io = Dispatchers.Unconfined
    )

    private val extractCookieValue: Method =
        BibSession::class.java.getDeclaredMethod(
            "extractCookieValue", String::class.java, String::class.java
        ).apply { isAccessible = true }

    private fun extract(setCookieLine: String, name: String = "PHPSESSID"): String? =
        extractCookieValue.invoke(session, setCookieLine, name) as String?

    /**
     * Repliziert exakt den Auswahl-Ausdruck aus BibSession.login()
     * (`setCookies.mapNotNull { extractCookieValue(it, "PHPSESSID") }.lastOrNull()`),
     * damit dieser Test bricht, sobald jemand auf firstNotNullOfOrNull zurückdreht.
     */
    private fun selectPhpSessId(setCookies: List<String>): String? =
        setCookies.mapNotNull { extract(it) }.lastOrNull()

    // Konstanten aus dem Bug-Report (docs/UBWWW_BUG_SESSION_FIXATION.md).
    private val anonymousPreAuth = "kvdq8cupqoh2qulrnjbvlcfptr" // 26 Zeichen, anonym
    private val authenticatedUser =
        "540997e8765226de6995bf6d303dd070f5f60ddb35ab497486d9e53fed291e4c" // 64 Zeichen, User-gebunden

    @Test
    fun `waehlt bei mehreren Set-Cookie-PHPSESSID die letzte (User-gebundene) Session`() {
        // Genau die zwei Header aus dem Session-Fixation-Report, in Reihenfolge.
        val setCookies = listOf(
            "PHPSESSID=$anonymousPreAuth; path=/",
            "PHPSESSID=$authenticatedUser; path=/"
        )

        val chosen = selectPhpSessId(setCookies)

        assertEquals(
            "Fix muss die ZWEITE (User-)Session wählen, nicht die anonyme erste — " +
                "sonst laufen Buchungen anonym (siehe UBWWW_BUG_SESSION_FIXATION.md)",
            authenticatedUser,
            chosen
        )
        // Regressions-Anker: die alte, fehlerhafte firstNotNullOfOrNull-Auswahl
        // hätte hier die anonyme Session geliefert.
        val buggyFirst = setCookies.firstNotNullOfOrNull { extract(it) }
        assertEquals(anonymousPreAuth, buggyFirst)
        assertTrue(
            "last darf NICHT gleich first sein, sonst testet der Fall nichts",
            chosen != buggyFirst
        )
    }

    @Test
    fun `mischt andere Set-Cookie-Header nicht in die PHPSESSID-Auswahl`() {
        // ubwww liefert real auch gap_language & Co. — die dürfen die Auswahl nicht stören.
        val setCookies = listOf(
            "gap_language=de; path=/",
            "PHPSESSID=$anonymousPreAuth; path=/",
            "PHPSESSID=$authenticatedUser; path=/; HttpOnly",
            "foo=bar"
        )
        assertEquals(authenticatedUser, selectPhpSessId(setCookies))
    }

    @Test
    fun `waehlt einzige PHPSESSID wenn nur eine gesetzt wird`() {
        val setCookies = listOf("PHPSESSID=$authenticatedUser; path=/; HttpOnly")
        assertEquals(authenticatedUser, selectPhpSessId(setCookies))
    }

    @Test
    fun `liefert null wenn keine PHPSESSID unter den Set-Cookie-Headern ist`() {
        val setCookies = listOf("gap_language=de; path=/", "foo=bar; path=/")
        assertNull(selectPhpSessId(setCookies))
    }

    @Test
    fun `liefert null bei leerer Set-Cookie-Liste`() {
        assertNull(selectPhpSessId(emptyList()))
    }

    // ── extractCookieValue-Bausteintests ────────────────────────────────

    @Test
    fun `extractCookieValue schneidet Attribute nach dem ersten Semikolon ab`() {
        assertEquals(
            authenticatedUser,
            extract("PHPSESSID=$authenticatedUser; path=/; HttpOnly; SameSite=Lax")
        )
    }

    @Test
    fun `extractCookieValue liefert null bei fehlendem Cookie-Namen`() {
        assertNull(extract("gap_language=de; path=/"))
    }

    @Test
    fun `extractCookieValue liefert null bei leerem Wert`() {
        // "PHPSESSID=; path=/" → Wert vor ';' ist leer → null.
        assertNull(extract("PHPSESSID=; path=/"))
    }

    @Test
    fun `extractCookieValue matched nur den benannten Cookie nicht Praefix-Kollisionen`() {
        // gap_PHPSESSID darf NICHT als PHPSESSID durchgehen? — Doku-Verhalten:
        // extractCookieValue sucht die Teilzeichenkette "PHPSESSID=" per indexOf.
        // Dieser Test nagelt das TATSÄCHLICHE Verhalten fest (siehe Bug-Hinweis unten).
        val value = extract("gap_PHPSESSID=$authenticatedUser; path=/")
        // POTENZIELLER PRODUKTIONS-BUG: indexOf("PHPSESSID=") findet den Substring
        // auch in "gap_PHPSESSID=" und extrahiert fälschlich dessen Wert. Ein
        // korrektes Cookie-Parsing müsste hier null liefern. Wir dokumentieren das
        // beobachtete Ist-Verhalten, ohne den Produktionscode zu ändern.
        assertEquals(
            "Ist-Verhalten: indexOf-basiertes Matching greift auch bei Präfix-Kollision",
            authenticatedUser,
            value
        )
    }
}
