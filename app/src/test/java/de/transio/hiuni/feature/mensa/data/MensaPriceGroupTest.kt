package de.transio.hiuni.feature.mensa.data

import de.transio.hiuni.core.auth.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Preisgruppen-Logik: Ableitung aus dem Uni-Login, Override aus den Einstellungen
 * und Preisauswahl pro Gericht mit Rückfall auf den Studierendenpreis.
 */
class MensaPriceGroupTest {

    private fun profile(
        uid: String? = "karstens",
        matrikel: String? = null,
        affiliations: List<String> = emptyList()
    ) = UserProfile(
        uid = uid,
        vorname = "Kjell",
        nachname = "Karstens",
        fullName = "Kjell Karstens",
        mail = "karstens@uni-hildesheim.de",
        matrikel = matrikel,
        affiliations = affiliations
    )

    // ── Ableitung aus dem Uni-Login ─────────────────────────────────────

    @Test
    fun `ohne Uni-Login gilt Studierende als Standard`() {
        val detection = PriceGroupRules.detect(UserProfile.EMPTY, hasGrades = false)
        assertEquals(PriceGroup.STUDENT, detection.group)
        assertEquals(PriceGroupSource.DEFAULT, detection.source)
    }

    @Test
    fun `Matrikelnummer im Profil ergibt Studierende`() {
        val detection = PriceGroupRules.detect(profile(matrikel = "00403556"), hasGrades = false)
        assertEquals(PriceGroup.STUDENT, detection.group)
        assertEquals(PriceGroupSource.MATRIKEL, detection.source)
    }

    @Test
    fun `Notenspiegel aus dem LSF ergibt Studierende auch ohne Matrikelnummer`() {
        val detection = PriceGroupRules.detect(profile(matrikel = null), hasGrades = true)
        assertEquals(PriceGroup.STUDENT, detection.group)
        assertEquals(PriceGroupSource.GRADES, detection.source)
    }

    @Test
    fun `Affiliation student schlaegt alles andere`() {
        val detection = PriceGroupRules.detect(
            profile(affiliations = listOf("employee", "student", "member")),
            hasGrades = false
        )
        assertEquals(PriceGroup.STUDENT, detection.group)
        assertEquals(PriceGroupSource.AFFILIATION, detection.source)
    }

    @Test
    fun `Affiliation mit Scope wird normalisiert`() {
        val detection = PriceGroupRules.detect(
            profile(affiliations = listOf("Student@uni-hildesheim.de")),
            hasGrades = false
        )
        assertEquals(PriceGroup.STUDENT, detection.group)
        assertEquals(PriceGroupSource.AFFILIATION, detection.source)
    }

    @Test
    fun `Affiliation employee staff oder faculty ohne student ergibt Bedienstete`() {
        for (affiliation in listOf("employee", "staff", "faculty")) {
            val detection = PriceGroupRules.detect(
                profile(affiliations = listOf(affiliation, "member")),
                hasGrades = false
            )
            assertEquals(affiliation, PriceGroup.EMPLOYEE, detection.group)
            assertEquals(affiliation, PriceGroupSource.AFFILIATION, detection.source)
        }
    }

    @Test
    fun `Affiliation employee verliert gegen Matrikelnummer`() {
        // Studentische Hilfskräfte sind beides, zahlen aber den Studierendenpreis.
        val detection = PriceGroupRules.detect(
            profile(matrikel = "00403556", affiliations = listOf("employee")),
            hasGrades = false
        )
        assertEquals(PriceGroup.STUDENT, detection.group)
    }

    @Test
    fun `Affiliation affiliate ergibt Gaeste`() {
        val detection = PriceGroupRules.detect(
            profile(affiliations = listOf("affiliate")),
            hasGrades = false
        )
        assertEquals(PriceGroup.GUEST, detection.group)
        assertEquals(PriceGroupSource.AFFILIATION, detection.source)
    }

    @Test
    fun `Uni-Login ohne Matrikelnummer und ohne Noten ergibt Bedienstete`() {
        val detection = PriceGroupRules.detect(profile(matrikel = null), hasGrades = false)
        assertEquals(PriceGroup.EMPLOYEE, detection.group)
        assertEquals(PriceGroupSource.LOGIN_WITHOUT_MATRIKEL, detection.source)
    }

    @Test
    fun `leere Matrikelnummer zaehlt als fehlend`() {
        val detection = PriceGroupRules.detect(profile(matrikel = "  "), hasGrades = false)
        assertEquals(PriceGroup.EMPLOYEE, detection.group)
    }

    @Test
    fun `Noten ohne Profil ergeben Studierende`() {
        val detection = PriceGroupRules.detect(UserProfile.EMPTY, hasGrades = true)
        assertEquals(PriceGroup.STUDENT, detection.group)
        assertEquals(PriceGroupSource.GRADES, detection.source)
    }

    // ── Effektive Gruppe: Override vs. Automatik ────────────────────────

    @Test
    fun `Automatik uebernimmt die erkannte Gruppe`() {
        assertEquals(
            PriceGroup.EMPLOYEE,
            PriceGroupRules.effective(PriceGroupPreference.AUTO, PriceGroup.EMPLOYEE)
        )
    }

    @Test
    fun `fester Override schlaegt die erkannte Gruppe`() {
        assertEquals(
            PriceGroup.GUEST,
            PriceGroupRules.effective(PriceGroupPreference.GUEST, PriceGroup.STUDENT)
        )
        assertEquals(
            PriceGroup.STUDENT,
            PriceGroupRules.effective(PriceGroupPreference.STUDENT, PriceGroup.EMPLOYEE)
        )
        assertEquals(
            PriceGroup.EMPLOYEE,
            PriceGroupRules.effective(PriceGroupPreference.EMPLOYEE, PriceGroup.GUEST)
        )
    }

    @Test
    fun `Preference-Key round-trippt und unbekannte Keys fallen auf Automatik`() {
        PriceGroupPreference.entries.forEach {
            assertEquals(it, PriceGroupPreference.fromKey(it.storageKey))
        }
        assertEquals(PriceGroupPreference.AUTO, PriceGroupPreference.fromKey(""))
        assertEquals(PriceGroupPreference.AUTO, PriceGroupPreference.fromKey("quatsch"))
    }

    // ── Preisauswahl pro Gericht ────────────────────────────────────────

    private fun meal(student: Int?, employee: Int?, guest: Int?) = MealEntity(
        sourceId = "1",
        locationId = 150,
        date = LocalDate.of(2026, 10, 26),
        category = "Essen 1",
        name = "Bohnenragout",
        description = null,
        priceStudentCents = student,
        priceEmployeeCents = employee,
        priceGuestCents = guest,
        tags = ""
    )

    @Test
    fun `waehlt den Preis der jeweiligen Gruppe`() {
        val m = meal(250, 685, 785)
        assertEquals(MealPrice(250, PriceGroup.STUDENT), m.priceFor(PriceGroup.STUDENT))
        assertEquals(MealPrice(685, PriceGroup.EMPLOYEE), m.priceFor(PriceGroup.EMPLOYEE))
        assertEquals(MealPrice(785, PriceGroup.GUEST), m.priceFor(PriceGroup.GUEST))
    }

    @Test
    fun `fehlender Gruppenpreis faellt auf den Studierendenpreis zurueck`() {
        val m = meal(250, null, null)
        assertEquals(MealPrice(250, PriceGroup.STUDENT), m.priceFor(PriceGroup.EMPLOYEE))
        assertEquals(MealPrice(250, PriceGroup.STUDENT), m.priceFor(PriceGroup.GUEST))
    }

    @Test
    fun `ohne jeden passenden Preis gibt es keinen Preis`() {
        assertNull(meal(null, null, null).priceFor(PriceGroup.GUEST))
        // Studierende bekommen keinen Gästepreis untergeschoben.
        assertNull(meal(null, 685, 785).priceFor(PriceGroup.STUDENT))
    }

    @Test
    fun `priceLabelFor formatiert den Gruppenpreis und ist leer ohne Preis`() {
        val label = meal(250, 685, 785).priceLabelFor(PriceGroup.EMPLOYEE)
        assertTrue("Label war: $label", label.contains("6") && label.contains("85") && label.endsWith("€"))
        assertEquals("", meal(null, null, null).priceLabelFor(PriceGroup.STUDENT))
    }

    @Test
    fun `otherPrices listet die uebrigen Gruppen mit eigenem Preis`() {
        val others = meal(250, 685, null).otherPrices(PriceGroup.EMPLOYEE)
        assertEquals(listOf(MealPrice(250, PriceGroup.STUDENT)), others)
    }

    // ── Beschriftung der Automatik-Option ───────────────────────────────

    @Test
    fun `Automatik-Label nennt die erkannte Gruppe`() {
        val detection = PriceGroupDetection(PriceGroup.EMPLOYEE, PriceGroupSource.LOGIN_WITHOUT_MATRIKEL)
        assertEquals("Automatisch (aus Uni-Login: Bedienstete)", PriceGroupRules.autoLabel(detection))
    }

    @Test
    fun `Automatik-Label ohne Login nennt den Standard`() {
        val detection = PriceGroupDetection(PriceGroup.STUDENT, PriceGroupSource.DEFAULT)
        assertEquals("Automatisch (kein Uni-Login: Studierende)", PriceGroupRules.autoLabel(detection))
    }

    @Test
    fun `Labels und Begruendungen enthalten keine Gedankenstriche`() {
        val texts = PriceGroup.entries.flatMap { listOf(it.label, it.priceCaption) } +
            PriceGroupSource.entries.map { it.reason }
        texts.forEach { assertTrue("Gedankenstrich in: $it", '\u2014' !in it && '\u2013' !in it) }
    }
}
