package de.transio.hiuni.core.search

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchSelectionHolderTest {

    private val holder = SearchSelectionHolder()

    @Test
    fun `consume liefert die Auswahl genau einmal`() {
        val selection = PendingSearchSelection.Exam(SearchFixtures.exam())
        holder.stage(selection)

        assertTrue(holder.consume(selection))
        assertFalse(holder.consume(selection))
        assertNull(holder.pending.value)
    }

    @Test
    fun `collectPending reagiert nur auf den eigenen Typ`() = runTest(UnconfinedTestDispatcher()) {
        val received = mutableListOf<PendingSearchSelection.MensaMeal>()
        val job = launch {
            holder.collectPending<PendingSearchSelection.MensaMeal> { received += it }
        }

        val foreign = PendingSearchSelection.Exam(SearchFixtures.exam())
        holder.stage(foreign)
        // Fremder Typ bleibt liegen, damit der zuständige Screen ihn noch abholen kann.
        assertTrue(received.isEmpty())
        assertEquals(foreign, holder.pending.value)

        val meal = PendingSearchSelection.MensaMeal(SearchFixtures.meal())
        holder.stage(meal)
        assertEquals(listOf(meal), received)
        assertNull(holder.pending.value)

        job.cancel()
    }

    @Test
    fun `zwei Sammler desselben Typs konsumieren die Auswahl nur einmal`() =
        runTest(UnconfinedTestDispatcher()) {
            var count = 0
            val first = launch { holder.collectPending<PendingSearchSelection.Course> { count++ } }
            val second = launch { holder.collectPending<PendingSearchSelection.Course> { count++ } }

            holder.stage(PendingSearchSelection.Course(SearchFixtures.course()))

            assertEquals(1, count)
            first.cancel()
            second.cancel()
        }
}
