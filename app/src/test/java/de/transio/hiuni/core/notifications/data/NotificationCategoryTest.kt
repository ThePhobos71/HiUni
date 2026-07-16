package de.transio.hiuni.core.notifications.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine Mapping-Tests für [NotificationCategory]. Kein Android-Runtime — deshalb
 * lebt die Kind→Kategorie→Channel-Zuordnung Android-frei in dieser Enum.
 */
class NotificationCategoryTest {

    @Test
    fun `jedes NotificationKind hat eine Kategorie`() {
        // of() ist exhaustive über when — dieser Test schlägt (zur Compile-Zeit
        // via when-Exhaustiveness, zur Laufzeit hier) fehl, falls ein Kind fehlt.
        for (kind in NotificationKind.entries) {
            val category = NotificationCategory.of(kind)
            assertTrue(
                "Kind $kind muss eine nicht-leere Channel-ID haben",
                category.channelId.isNotBlank()
            )
        }
    }

    @Test
    fun `feature-Kinds mappen auf ihre eigene Kategorie`() {
        assertEquals(NotificationCategory.EVENTS, NotificationCategory.of(NotificationKind.EVENT))
        assertEquals(NotificationCategory.EXAMS, NotificationCategory.of(NotificationKind.EXAM))
        assertEquals(NotificationCategory.GRADES, NotificationCategory.of(NotificationKind.GRADE))
        assertEquals(NotificationCategory.COURSES, NotificationCategory.of(NotificationKind.COURSE))
        assertEquals(NotificationCategory.LEARNWEB, NotificationCategory.of(NotificationKind.LEARNWEB))
        assertEquals(NotificationCategory.MAIL, NotificationCategory.of(NotificationKind.MAIL))
    }

    @Test
    fun `Mensa Kino Sport Bib und System teilen sich den Sammelkanal`() {
        val system = NotificationCategory.SYSTEM
        assertEquals(system, NotificationCategory.of(NotificationKind.MENSA))
        assertEquals(system, NotificationCategory.of(NotificationKind.MOVIE))
        assertEquals(system, NotificationCategory.of(NotificationKind.SPORT))
        assertEquals(system, NotificationCategory.of(NotificationKind.BIB))
        assertEquals(system, NotificationCategory.of(NotificationKind.SYSTEM))
    }

    @Test
    fun `EVENTS-Channel-ID bleibt kompatibel zum alten Konstanten-Wert`() {
        // Darf sich nie ändern, sonst verliert der Nutzer seine Channel-Einstellung.
        assertEquals("hiuni_event_reminders", NotificationCategory.EVENTS.channelId)
    }

    @Test
    fun `Channel-IDs sind eindeutig`() {
        val ids = NotificationCategory.entries.map { it.channelId }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `channelIdFor ist Alias fuer of-channelId`() {
        for (kind in NotificationKind.entries) {
            assertEquals(
                NotificationCategory.of(kind).channelId,
                NotificationCategory.channelIdFor(kind)
            )
        }
    }
}
