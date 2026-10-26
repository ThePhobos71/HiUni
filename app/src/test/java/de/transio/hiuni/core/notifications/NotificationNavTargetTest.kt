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
    fun `alle anderen Kinds öffnen weiterhin das Push-Center`() {
        for (kind in NotificationKind.entries - NotificationKind.TODO - NotificationKind.GRADE - NotificationKind.MAIL) {
            assertEquals(
                NotificationPresenter.NAV_TARGET_NOTIFICATIONS_CENTER,
                NotificationPresenter.navTargetFor(kind)
            )
        }
    }
}
