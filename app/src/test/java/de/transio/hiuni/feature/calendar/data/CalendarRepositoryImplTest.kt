package de.transio.hiuni.feature.calendar.data

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarRepositoryImplTest {

    private val dao = mockk<CustomEventDao>(relaxed = true)
    private val repo = CalendarRepositoryImpl(dao)

    @Test
    fun `observeRange forwards window to DAO and emits events`() = runTest {
        val from = Instant.parse("2026-05-24T00:00:00Z")
        val to = Instant.parse("2026-05-31T00:00:00Z")
        val event = CustomEventEntity(
            id = 1L,
            title = "Lerntreff",
            description = null,
            location = "Bibliothek G3",
            startTime = Instant.parse("2026-05-25T10:00:00Z"),
            endTime = Instant.parse("2026-05-25T12:00:00Z"),
            sourceKind = CustomEventEntity.SOURCE_USER,
            reminderMinutesBefore = 15
        )
        every { dao.observeRange(from.toEpochMilli(), to.toEpochMilli()) } returns flowOf(listOf(event))
        // Repository kombiniert die Range-Query mit einer Recurring-Master-Query — wir
        // stubben hier explizit auf leer, damit `combine` einen ersten Wert sehen kann.
        every { dao.observeRecurringMastersUntil(to.toEpochMilli()) } returns flowOf(emptyList())

        repo.observeRange(from, to).test {
            assertEquals(listOf(event), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `upsert with id zero inserts new event`() = runTest {
        val newEvent = CustomEventEntity(
            id = 0L,
            title = "Neu",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(3600)
        )
        coEvery { dao.insert(newEvent) } returns 42L

        val result = repo.upsert(newEvent)

        assertEquals(42L, result)
        coVerify(exactly = 1) { dao.insert(newEvent) }
        coVerify(exactly = 0) { dao.update(any()) }
    }

    @Test
    fun `upsert with existing id updates and returns same id`() = runTest {
        val existing = CustomEventEntity(
            id = 7L,
            title = "Update",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(1800)
        )

        val result = repo.upsert(existing)

        assertEquals(7L, result)
        coVerify(exactly = 1) { dao.update(existing) }
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `delete delegates to DAO`() = runTest {
        val event = CustomEventEntity(
            id = 9L,
            title = "Delete me",
            startTime = Instant.now(),
            endTime = Instant.now().plusSeconds(60)
        )

        repo.delete(event)

        coVerify(exactly = 1) { dao.delete(event) }
    }
}
