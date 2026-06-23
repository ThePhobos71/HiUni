package de.transio.hiuni.feature.todos.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Persistente User-Aufgabe. Optionales Fälligkeitsdatum (LocalDate, ohne Uhrzeit),
 * Erstellungs- und Abschlusszeitpunkt als Instant. `sortIndex` erlaubt späteres
 * manuelles Umsortieren — die Home-Vorschau und der Vollscreen sortieren primär
 * nach (isDone, dueDate, sortIndex).
 */
@Entity(
    tableName = "todos",
    indices = [
        Index(value = ["isDone", "dueDate"]),
        Index(value = ["dueDate"]),
        Index(value = ["courseId"])
    ]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueDate: LocalDate? = null,
    val isDone: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
    val sortIndex: Int = 0,
    /**
     * Optional zugeordneter Kurs (`CourseEntity.id`). Wird nicht als Foreign-Key
     * deklariert, damit gelöschte Kurse (z.B. nach LSF-Reimport) die Aufgabe nicht
     * mitreißen — beim Lesen wird die Verknüpfung als "Kurs entfernt" behandelt.
     */
    val courseId: String? = null
)
