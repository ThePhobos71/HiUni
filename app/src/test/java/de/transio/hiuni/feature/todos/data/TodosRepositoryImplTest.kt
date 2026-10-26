package de.transio.hiuni.feature.todos.data

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Prüft die Reminder-Kopplung im [TodosRepositoryImpl]: jeder Schreibpfad
 * (Screen, Home-Vorschau, Widget) zieht den Alarm automatisch mit.
 */
class TodosRepositoryImplTest {

    private val dao = mockk<TodoDao>(relaxed = true)
    private val reminders = mockk<TodoReminderScheduler>(relaxed = true)
    private val repo = TodosRepositoryImpl(dao, reminders)

    private val due = LocalDate.of(2026, 11, 3)

    @Test
    fun `Anlegen plant den Reminder mit der neuen ID`() = runBlocking {
        coEvery { dao.insert(any()) } returns 42L
        val todo = TodoEntity(title = "Blatt 4", dueDate = due)

        val id = repo.upsert(todo)

        assertEquals(42L, id)
        verify { reminders.sync(todo.copy(id = 42L)) }
    }

    @Test
    fun `Bearbeiten plant den Reminder neu`() = runBlocking {
        val edited = TodoEntity(id = 5L, title = "Blatt 4", dueDate = due.plusDays(1))

        repo.upsert(edited)

        verify { reminders.sync(edited) }
    }

    @Test
    fun `Löschen cancelt den Reminder vor dem DB-Delete`() = runBlocking {
        repo.delete(7L)

        coVerifyOrder {
            reminders.cancel(7L)
            dao.delete(7L)
        }
    }

    @Test
    fun `Abhaken und Wieder-Öffnen synchronisieren mit dem aktuellen DB-Stand`() = runBlocking {
        val doneRow = TodoEntity(id = 8L, title = "Lesen", dueDate = due, isDone = true)
        val openRow = doneRow.copy(isDone = false)
        coEvery { dao.findById(8L) } returnsMany listOf(doneRow, openRow)

        repo.setDone(8L, done = true, now = Instant.EPOCH)
        repo.setDone(8L, done = false, now = Instant.EPOCH)

        coVerifyOrder {
            dao.setDoneInternal(8L, true, 0L)
            reminders.sync(doneRow)
            dao.setDoneInternal(8L, false, null)
            reminders.sync(openRow)
        }
    }
}
