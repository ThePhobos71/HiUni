package de.transio.hiuni.core.notifications

import de.transio.hiuni.core.notifications.data.NotificationKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tap-Ziel der OS-Notification je [NotificationKind]. */
class NotificationNavTargetTest {

    @Test
    fun `Aufgaben-Erinnerung öffnet den Aufgaben-Screen`() {
        assertEquals(
            NotificationPresenter.NAV_TARGET_TODOS,
            NotificationPresenter.navTargetFor(NotificationKind.TODO)
        )
    }

    @Test
    fun `Neue-Note-Meldung öffnet den Noten-Screen`() {
        assertEquals(
            NotificationPresenter.NAV_TARGET_GRADES,
            NotificationPresenter.navTargetFor(NotificationKind.GRADE)
        )
    }

    @Test
    fun `Neue-Mail-Meldung öffnet das Postfach`() {
        assertEquals(
            NotificationPresenter.NAV_TARGET_EMAIL,
            NotificationPresenter.navTargetFor(NotificationKind.MAIL)
        )
    }

    @Test
    fun `Bib-Meldung öffnet den Bibliotheks-Screen`() {
        assertEquals(
            NotificationPresenter.NAV_TARGET_BIB,
            NotificationPresenter.navTargetFor(NotificationKind.BIB)
        )
    }

    @Test
    fun `alle anderen Kinds öffnen weiterhin das Push-Center`() {
        val ownTargets = setOf(
            NotificationKind.TODO,
            NotificationKind.GRADE,
            NotificationKind.MAIL,
            NotificationKind.BIB
        )
        for (kind in NotificationKind.entries - ownTargets) {
            assertEquals(
                NotificationPresenter.NAV_TARGET_NOTIFICATIONS_CENTER,
                NotificationPresenter.navTargetFor(kind)
            )
        }
    }
}
