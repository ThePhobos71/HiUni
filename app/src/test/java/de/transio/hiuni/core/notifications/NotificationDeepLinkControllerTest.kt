package de.transio.hiuni.core.notifications

import de.transio.hiuni.feature.widgets.WidgetDeepLinkController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Kaltstart-Szenario: MainActivity signalisiert das Sprungziel in onCreate, BEVOR
 * der AppNavGraph komponiert ist und zuhört. Das Signal muss trotzdem ankommen,
 * und zwar genau einmal (kein erneuter Sprung nach Rotation/Recomposition).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationDeepLinkControllerTest {

    @Test
    fun `Signal vor dem Zuhoeren geht nicht verloren`() = runTest {
        val controller = NotificationDeepLinkController()
        controller.signalOpenCenter()
        assertNotNull(withTimeoutOrNull(1_000) { controller.openCenter.first() })
    }

    @Test
    fun `Signal wird genau einmal ausgeliefert`() = runTest {
        val controller = NotificationDeepLinkController()
        controller.signalOpenTodos()
        controller.openTodos.first()
        // Zweiter Collector (z.B. nach Rotation) darf denselben Sprung nicht nochmal sehen.
        assertNull(withTimeoutOrNull(1_000) { controller.openTodos.first() })
    }

    @Test
    fun `Noten-Signal vor dem Zuhoeren kommt an`() = runTest {
        val controller = NotificationDeepLinkController()
        controller.signalOpenGrades()
        assertNotNull(withTimeoutOrNull(1_000) { controller.openGrades.first() })
    }

    @Test
    fun `Widget-Signal vor dem Zuhoeren geht nicht verloren`() = runTest {
        val controller = WidgetDeepLinkController()
        controller.handleIntent(android.content.Intent(WidgetDeepLinkController.ACTION_OPEN_MENSA))
        assertNotNull(withTimeoutOrNull(1_000) { controller.openMensa.first() })
    }
}
