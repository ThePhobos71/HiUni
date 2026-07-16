package de.transio.hiuni.feature.lsf.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für die reine Kurs-Diff-Push-Logik. Kein HTTP, kein Room, kein Firebase —
 * genau deshalb liegt die Entscheidung in [CourseDiffNotifier].
 */
class CourseDiffNotifierTest {

    @Test
    fun `neuer Kurs bei bestehendem Sync erzeugt Einzel-Push`() {
        val pushes = CourseDiffNotifier.decide(
            isFirstSync = false,
            newCourses = listOf("5395" to "Diskrete Strukturen")
        )
        assertEquals(1, pushes.size)
        assertEquals("Neuer Kurs im LSF", pushes[0].title)
        assertEquals("Diskrete Strukturen", pushes[0].body)
        assertEquals("course:5395", pushes[0].refKey)
    }

    @Test
    fun `Erst-Sync erzeugt keine Pushes`() {
        val many = (1..5).map { "$it" to "Kurs $it" }
        val pushes = CourseDiffNotifier.decide(isFirstSync = true, newCourses = many)
        assertTrue(pushes.isEmpty())
    }

    @Test
    fun `unveraenderter Bestand erzeugt keine Pushes`() {
        val pushes = CourseDiffNotifier.decide(isFirstSync = false, newCourses = emptyList())
        assertTrue(pushes.isEmpty())
    }

    @Test
    fun `mehr als BULK_THRESHOLD neue Kurse ergeben eine Sammel-Meldung`() {
        val many = (1..(CourseDiffNotifier.BULK_THRESHOLD + 1)).map { "$it" to "Kurs $it" }
        val pushes = CourseDiffNotifier.decide(isFirstSync = false, newCourses = many)
        assertEquals(1, pushes.size)
        assertEquals("Neue Kurse im LSF", pushes[0].title)
        assertTrue(pushes[0].body.contains("${CourseDiffNotifier.BULK_THRESHOLD + 1}"))
        assertTrue(pushes[0].refKey.startsWith("courses:bulk:"))
    }

    @Test
    fun `genau BULK_THRESHOLD neue Kurse bleiben Einzel-Pushes`() {
        val exactly = (1..CourseDiffNotifier.BULK_THRESHOLD).map { "$it" to "Kurs $it" }
        val pushes = CourseDiffNotifier.decide(isFirstSync = false, newCourses = exactly)
        assertEquals(CourseDiffNotifier.BULK_THRESHOLD, pushes.size)
        assertTrue(pushes.all { it.title == "Neuer Kurs im LSF" })
        assertTrue(pushes.all { it.refKey.startsWith("course:") })
    }

    @Test
    fun `RefKey je Kurs ist stabil pro lsfId`() {
        val a = CourseDiffNotifier.decide(false, listOf("42" to "A"))
        val b = CourseDiffNotifier.decide(false, listOf("42" to "A (geänderter Titel)"))
        assertEquals(a[0].refKey, b[0].refKey)
    }
}
