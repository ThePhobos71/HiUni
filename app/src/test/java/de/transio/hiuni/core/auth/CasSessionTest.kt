package de.transio.hiuni.core.auth

import de.transio.hiuni.core.security.CredentialsManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method
import java.time.Instant

/**
 * Unit-Tests für die rein testbaren Teile von [CasSession]:
 *  - HTML-Parsing der CAS-Attribute-Page ([CasSession.parseUserAttributes])
 *  - Initial-State-Berechnung aus dem CookieStore ([CasSession.computeInitialState])
 *  - Ticket-Extraktion aus dem 302-Location-Header (wie in fetchServiceTicket)
 *
 * Keine echten Netzwerk-Calls — MockWebServer ist keine Test-Dependency. Die
 * Netzwerk-Pfade (getServiceTicket/silentRenew) sind hier bewusst ausgeklammert.
 */
class CasSessionTest {

    private val cookieStore = mockk<CasCookieStore>(relaxed = true)
    private val credentialsManager = mockk<CredentialsManager>(relaxed = true)
    private val httpClient = mockk<OkHttpClient>(relaxed = true)

    @Before
    fun setUp() {
        // Sane Defaults für den Feld-Initializer (computeInitialState()/profile()
        // laufen im CasSession-Konstruktor). Einzelne Tests überschreiben diese
        // Stubs VOR dem Bau der Session — newSession() darf sie daher NICHT mehr
        // zurücksetzen, sonst würden genau diese Overrides wieder verworfen.
        every { cookieStore.hasSession() } returns false
        every { cookieStore.profile() } returns UserProfile.EMPTY
    }

    private fun newSession(): CasSession =
        CasSession(
            cookieStore = cookieStore,
            credentialsManager = credentialsManager,
            httpClient = httpClient,
            io = Dispatchers.Unconfined,
            appScope = CoroutineScope(Dispatchers.Unconfined)
        )

    private val parseUserAttributes: Method =
        CasSession::class.java.getDeclaredMethod("parseUserAttributes", String::class.java)
            .apply { isAccessible = true }

    private fun parse(html: String): UserProfile =
        parseUserAttributes.invoke(newSession(), html) as UserProfile

    private val computeInitialState: Method =
        CasSession::class.java.getDeclaredMethod("computeInitialState")
            .apply { isAccessible = true }

    private fun initialState(): CasState =
        computeInitialState.invoke(newSession()) as CasState

    // ── parseUserAttributes ─────────────────────────────────────────────

    /** Attribute-Page-HTML wie von CAS geliefert (vereinfacht, echte Struktur). */
    private fun attributePage(rows: String): String =
        """
        <html><body>
          <div id="divPrincipalAttributes">
            <table><tbody>
              $rows
            </tbody></table>
          </div>
        </body></html>
        """.trimIndent()

    @Test
    fun `parseUserAttributes extrahiert alle Identity-Felder`() {
        val html = attributePage(
            """
            <tr><td>uid</td><td>[karstens]</td></tr>
            <tr><td>Vorname</td><td>[Kjell Heinrich]</td></tr>
            <tr><td>Nachname</td><td>[Karstens]</td></tr>
            <tr><td>Name</td><td>[Kjell Heinrich Karstens]</td></tr>
            <tr><td>Mail</td><td>[karstens@uni-hildesheim.de]</td></tr>
            <tr><td>mtknr</td><td>[00403556]</td></tr>
            """.trimIndent()
        )

        val p = parse(html)
        assertEquals("karstens", p.uid)
        assertEquals("Kjell Heinrich", p.vorname)
        assertEquals("Karstens", p.nachname)
        assertEquals("Kjell Heinrich Karstens", p.fullName)
        assertEquals("karstens@uni-hildesheim.de", p.mail)
        assertEquals("00403556", p.matrikel)
        // firstName-Ableitung im DTO: erster Vorname vor dem Leerzeichen.
        assertEquals("Kjell", p.firstName)
    }

    @Test
    fun `parseUserAttributes strippt eckige Klammern um den Wert`() {
        val html = attributePage("""<tr><td>Nachname</td><td>[Karstens]</td></tr>""")
        assertEquals("Karstens", parse(html).nachname)
    }

    @Test
    fun `parseUserAttributes akzeptiert Werte ohne eckige Klammern`() {
        val html = attributePage("""<tr><td>Nachname</td><td>Karstens</td></tr>""")
        assertEquals("Karstens", parse(html).nachname)
    }

    @Test
    fun `parseUserAttributes faellt fuer uid auf username-Attribut zurueck`() {
        val html = attributePage("""<tr><td>username</td><td>[karstens]</td></tr>""")
        assertEquals("karstens", parse(html).uid)
    }

    @Test
    fun `parseUserAttributes liefert EMPTY wenn Principal-Tabelle fehlt`() {
        val html = "<html><body><p>Sie sind angemeldet.</p></body></html>"
        assertEquals(UserProfile.EMPTY, parse(html))
    }

    @Test
    fun `parseUserAttributes ignoriert Zeilen mit weniger als zwei Zellen`() {
        val html = attributePage(
            """
            <tr><td>nur-eine-zelle</td></tr>
            <tr><td>Vorname</td><td>[Kjell]</td></tr>
            """.trimIndent()
        )
        val p = parse(html)
        assertEquals("Kjell", p.vorname)
        assertNull("uid nicht gesetzt, weil kein passendes Attribut", p.uid)
    }

    @Test
    fun `parseUserAttributes ueberspringt leere Werte`() {
        val html = attributePage(
            """
            <tr><td>Vorname</td><td>[]</td></tr>
            <tr><td>Nachname</td><td>[Karstens]</td></tr>
            """.trimIndent()
        )
        val p = parse(html)
        assertNull("leerer Vorname-Wert wird verworfen", p.vorname)
        assertEquals("Karstens", p.nachname)
    }

    @Test
    fun `parseUserAttributes liefert EMPTY bei komplettem Muell-Input`() {
        assertEquals(UserProfile.EMPTY, parse("not even html <<<"))
        assertEquals(UserProfile.EMPTY, parse(""))
    }

    // ── computeInitialState ─────────────────────────────────────────────

    @Test
    fun `computeInitialState liefert NeedsLogin ohne Session`() {
        every { cookieStore.hasSession() } returns false
        every { cookieStore.profile() } returns UserProfile.EMPTY
        assertTrue(initialState() is CasState.NeedsLogin)
    }

    @Test
    fun `computeInitialState liefert NeedsReauth wenn Session ohne Zeitstempel`() {
        every { cookieStore.hasSession() } returns true
        every { cookieStore.obtainedAt() } returns null
        every { cookieStore.profile() } returns UserProfile.EMPTY
        assertTrue(initialState() is CasState.NeedsReauth)
    }

    @Test
    fun `computeInitialState liefert Authenticated mit Username und Zeitstempel`() {
        val at = Instant.parse("2026-07-16T10:00:00Z")
        every { cookieStore.hasSession() } returns true
        every { cookieStore.obtainedAt() } returns at
        every { cookieStore.username() } returns "karstens"
        every { cookieStore.profile() } returns UserProfile.EMPTY
        val state = initialState()
        assertTrue(state is CasState.Authenticated)
        state as CasState.Authenticated
        assertEquals("karstens", state.username)
        assertEquals(at, state.obtainedAt)
    }

    // ── Ticket-Extraktion aus Redirect-Location ─────────────────────────
    // Spiegelt den Produktions-Ausdruck aus fetchServiceTicket():
    //   location.toHttpUrl().queryParameter("ticket")

    private fun ticketFromLocation(location: String): String? =
        location.toHttpUrl().queryParameter("ticket")

    @Test
    fun `Ticket-Extraktion zieht ST aus Service-Redirect-URL`() {
        val loc =
            "https://ubwww.uni-hildesheim.de/gruppenraumbuchung/index.php?login&ticket=ST-12345-abcdefcas-p01"
        assertEquals("ST-12345-abcdefcas-p01", ticketFromLocation(loc))
    }

    @Test
    fun `Ticket-Extraktion liefert null bei Redirect ohne ticket-Param`() {
        // 302 ohne ?ticket= = CAS hat das TGC verworfen (TGT abgelaufen).
        val loc = "https://www.uni-hildesheim.de/sso/login?service=whatever"
        assertNull(ticketFromLocation(loc))
    }

    @Test
    fun `Ticket-Extraktion behandelt LSF-Service-URL mit weiteren Query-Params`() {
        val loc =
            "https://lsf.uni-hildesheim.de/qisserver/rds?state=user&type=1&ticket=ST-9-xyzcas-p01"
        assertEquals("ST-9-xyzcas-p01", ticketFromLocation(loc))
    }
}
