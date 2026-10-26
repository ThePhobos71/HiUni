package de.transio.hiuni.feature.todos.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface TodosRepository {
    fun observeAll(): Flow<List<TodoEntity>>
    fun observeOpen(limit: Int): Flow<List<TodoEntity>>
    fun observeOpenCount(): Flow<Int>
    suspend fun upsert(todo: TodoEntity): Long
    suspend fun delete(id: Long)
    suspend fun setDone(id: Long, done: Boolean, now: Instant = Instant.now())
}

/**
 * Room-Implementierung. Hält zusätzlich die Aufgaben-Erinnerungen synchron
 * ([TodoReminderScheduler]): jeder Schreibpfad (Screen, Home-Vorschau, Widget)
 * läuft hier durch, deshalb sitzt die Reminder-Kopplung hier und nicht im ViewModel.
 */
@Singleton
class TodosRepositoryImpl @Inject constructor(
    private val dao: TodoDao,
    private val reminders: TodoReminderScheduler
) : TodosRepository {

    override fun observeAll(): Flow<List<TodoEntity>> = dao.observeAll()

    override fun observeOpen(limit: Int): Flow<List<TodoEntity>> = dao.observeOpen(limit)

    override fun observeOpenCount(): Flow<Int> = dao.observeOpenCount()

    override suspend fun upsert(todo: TodoEntity): Long {
        val finalId = if (todo.id == 0L) dao.insert(todo) else {
            dao.update(todo)
            todo.id
        }
        reminders.sync(todo.copy(id = finalId))
        return finalId
    }

    override suspend fun delete(id: Long) {
        reminders.cancel(id)
        dao.delete(id)
    }

    override suspend fun setDone(id: Long, done: Boolean, now: Instant) {
        dao.setDoneInternal(
            id = id,
            done = done,
            completedAtMillis = if (done) now.toEpochMilli() else null
        )
        // Abhaken cancelt den Reminder, Wieder-Öffnen plant ihn erneut ein.
        dao.findById(id)?.let { reminders.sync(it) }
    }
}
