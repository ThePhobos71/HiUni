package de.transio.hiuni.feature.search

import de.transio.hiuni.core.search.GlobalSearchRepository
import de.transio.hiuni.core.search.PendingSearchSelection
import de.transio.hiuni.core.search.SearchFixtures
import de.transio.hiuni.core.search.SearchSelectionHolder
import de.transio.hiuni.navigation.Destination
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Mapping „angetippter Spotlight-Treffer → Navigationsziel + vorgemerkte Auswahl".
 * Die Auswahl selbst konsumieren die Ziel-ViewModels (siehe deren Tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSearchViewModelTest {

    private val repository = mockk<GlobalSearchRepository>(relaxed = true)
    private val holder = SearchSelectionHolder()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm() = GlobalSearchViewModel(repository, holder)

    @Test
    fun `Mail-Treffer oeffnet den Mail-Tab und merkt die Mail vor`() {
        val email = SearchFixtures.email()
        val target = newVm().open(SearchHit.Email(email))

        assertEquals(SearchNavTarget.Tab(Destination.Email), target)
        assertEquals(PendingSearchSelection.Email(email), holder.pending.value)
    }

    @Test
    fun `Termin-Treffer oeffnet den Kalender und merkt den Termin vor`() {
        val event = SearchFixtures.event()
        val target = newVm().open(SearchHit.Event(event))

        assertEquals(SearchNavTarget.Tab(Destination.Calendar), target)
        assertEquals(PendingSearchSelection.CalendarEvent(event), holder.pending.value)
    }

    @Test
    fun `Kurs-Treffer oeffnet den Kurse-Tab und merkt den Kurs vor`() {
        val course = SearchFixtures.course()
        val target = newVm().open(SearchHit.Course(course))

        assertEquals(SearchNavTarget.Tab(Destination.Courses), target)
        assertEquals(PendingSearchSelection.Course(course), holder.pending.value)
    }

    @Test
    fun `Klausur-Treffer oeffnet den Klausurplan und merkt die Klausur vor`() {
        val exam = SearchFixtures.exam()
        val target = newVm().open(SearchHit.Exam(exam))

        assertEquals(SearchNavTarget.Tab(Destination.Exams), target)
        assertEquals(PendingSearchSelection.Exam(exam), holder.pending.value)
    }

    @Test
    fun `Mensa-Treffer oeffnet die Mensa und merkt das Gericht vor`() {
        val meal = SearchFixtures.meal()
        val target = newVm().open(SearchHit.Meal(meal))

        assertEquals(SearchNavTarget.Tab(Destination.Mensa), target)
        assertEquals(PendingSearchSelection.MensaMeal(meal), holder.pending.value)
    }

    @Test
    fun `Sport-Treffer geht direkt auf die Detail-Route ohne Vormerkung`() {
        val target = newVm().open(SearchHit.Sport(SearchFixtures.sport(slotId = 99L)))

        assertEquals(SearchNavTarget.SportDetail(99L), target)
        assertNull(holder.pending.value)
    }

    @Test
    fun `Learnweb-Kurs oeffnet die Kursseite im Browser`() {
        val course = SearchFixtures.learnwebCourse()
        val target = newVm().open(SearchHit.LearnwebCourse(course))

        assertEquals(SearchNavTarget.ExternalUrl(course.url), target)
        assertNull(holder.pending.value)
    }

    @Test
    fun `Learnweb-Abgabe oeffnet die Abgabe im Browser`() {
        val assignment = SearchFixtures.learnwebAssignment()
        val target = newVm().open(SearchHit.LearnwebAssignment(assignment))

        assertEquals(SearchNavTarget.ExternalUrl(assignment.url), target)
        assertNull(holder.pending.value)
    }

    @Test
    fun `neuer Treffer ersetzt eine noch nicht abgeholte Vormerkung`() {
        val vm = newVm()
        vm.open(SearchHit.Exam(SearchFixtures.exam()))
        val meal = SearchFixtures.meal()
        vm.open(SearchHit.Meal(meal))

        assertEquals(PendingSearchSelection.MensaMeal(meal), holder.pending.value)
    }

    @Test
    fun `Browser-Treffer verwirft eine liegengebliebene Vormerkung`() {
        val vm = newVm()
        vm.open(SearchHit.Exam(SearchFixtures.exam()))
        vm.open(SearchHit.LearnwebCourse(SearchFixtures.learnwebCourse()))

        assertNull(holder.pending.value)
    }
}
