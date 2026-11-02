package de.transio.hiuni.feature.mensa.data

import de.transio.hiuni.core.auth.CasSession
import de.transio.hiuni.core.auth.UserProfile
import de.transio.hiuni.core.datastore.SettingsDataStore
import de.transio.hiuni.feature.grades.data.GradeDao
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MensaPriceGroupResolverTest {

    private val preferenceKey = MutableStateFlow("")
    private val profile = MutableStateFlow(UserProfile.EMPTY)
    private val hasGrades = MutableStateFlow(false)

    private val settings = mockk<SettingsDataStore>(relaxed = true).also {
        every { it.mensaPriceGroup } returns preferenceKey
    }
    private val casSession = mockk<CasSession>(relaxed = true).also {
        every { it.profile } returns profile
    }
    private val gradeDao = mockk<GradeDao>(relaxed = true).also {
        every { it.observeHasAny() } returns hasGrades
    }

    private fun resolver() = MensaPriceGroupResolver(settings, casSession, gradeDao)

    private val employeeProfile = UserProfile(
        uid = "mueller", vorname = "Eva", nachname = "Müller", fullName = "Eva Müller",
        mail = "mueller@uni-hildesheim.de", matrikel = null,
        // Ohne Rollen-Attribut gilt im Zweifel Studierende, daher hier explizit.
        affiliations = listOf("employee")
    )

    @Test
    fun `ohne gespeicherte Wahl und ohne Login gilt Studierende`() = runTest {
        val r = resolver()
        assertEquals(PriceGroupPreference.AUTO, r.preference.first())
        assertEquals(PriceGroup.STUDENT, r.effectiveGroup.first())
    }

    @Test
    fun `Automatik folgt dem Uni-Login`() = runTest {
        profile.value = employeeProfile
        val r = resolver()
        assertEquals(PriceGroup.EMPLOYEE, r.detection.first().group)
        assertEquals(PriceGroup.EMPLOYEE, r.effectiveGroup.first())

        // Sobald ein Notenspiegel auftaucht, ist die Person Studi.
        hasGrades.value = true
        assertEquals(PriceGroup.STUDENT, r.effectiveGroup.first())
    }

    @Test
    fun `gespeicherter Override schlaegt die Erkennung`() = runTest {
        profile.value = employeeProfile
        preferenceKey.value = PriceGroupPreference.GUEST.storageKey
        val r = resolver()
        assertEquals(PriceGroupPreference.GUEST, r.preference.first())
        assertEquals(PriceGroup.GUEST, r.effectiveGroup.first())
        // Die Erkennung bleibt trotzdem sichtbar (für die Automatik-Zeile).
        assertEquals(PriceGroup.EMPLOYEE, r.detection.first().group)
    }

    @Test
    fun `setPreference schreibt den Storage-Key`() = runTest {
        resolver().setPreference(PriceGroupPreference.EMPLOYEE)
        coVerify { settings.setMensaPriceGroup("employee") }
    }
}
