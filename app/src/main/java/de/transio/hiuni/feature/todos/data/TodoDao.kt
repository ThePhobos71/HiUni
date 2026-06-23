package de.transio.hiuni.feature.todos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    /**
     * Alle Aufgaben sortiert: offene zuerst, dann nach Fälligkeit (NULL ans Ende),
     * dann nach manuellem sortIndex, dann nach Erstellzeit (älteste zuerst innerhalb der Gruppe).
     */
    @Query(
        "SELECT * FROM todos " +
            "ORDER BY isDone ASC, " +
            "CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END ASC, " +
            "dueDate ASC, sortIndex ASC, createdAt ASC"
    )
    fun observeAll(): Flow<List<TodoEntity>>

    /**
     * Nur offene Aufgaben, limitiert für die Home-Vorschau.
     */
    @Query(
        "SELECT * FROM todos WHERE isDone = 0 " +
            "ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END ASC, " +
            "dueDate ASC, sortIndex ASC, createdAt ASC " +
            "LIMIT :limit"
    )
    fun observeOpen(limit: Int): Flow<List<TodoEntity>>

    @Query("SELECT COUNT(*) FROM todos WHERE isDone = 0")
    fun observeOpenCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE todos SET isDone = :done, completedAt = :completedAtMillis WHERE id = :id")
    suspend fun setDoneInternal(id: Long, done: Boolean, completedAtMillis: Long?)

    @Query("SELECT * FROM todos WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): TodoEntity?
}
