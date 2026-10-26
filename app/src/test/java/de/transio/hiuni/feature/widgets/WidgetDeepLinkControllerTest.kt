package de.transio.hiuni.feature.widgets

import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Intent-Action zu Signal-Mapping für App-Shortcuts, Widgets und die QS-Kachel. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class WidgetDeepLinkControllerTest {

    private fun Flow<Unit>.fired(): Boolean = runBlocking {
        withTimeoutOrNull(200) { first() } != null
    }

    @Test
    fun `bib action fires bib signal only`() {
        val c = WidgetDeepLinkController()
        assertTrue(c.handleIntent(Intent(WidgetDeepLinkController.ACTION_OPEN_BIB)))
        assertTrue(c.openBib.fired())
        assertFalse(c.openTodos.fired())
    }

    @Test
    fun `mensa action fires mensa signal`() {
        val c = WidgetDeepLinkController()
        assertTrue(c.handleIntent(Intent(WidgetDeepLinkController.ACTION_OPEN_MENSA)))
        assertTrue(c.openMensa.fired())
        assertFalse(c.openMensaCard.fired())
    }

    @Test
    fun `calendar action fires calendar signal`() {
        val c = WidgetDeepLinkController()
        assertTrue(c.handleIntent(Intent(WidgetDeepLinkController.ACTION_OPEN_CALENDAR)))
        assertTrue(c.openCalendar.fired())
    }

    @Test
    fun `mensa card action fires mensa card signal`() {
        val c = WidgetDeepLinkController()
        assertTrue(c.handleIntent(Intent(WidgetDeepLinkController.ACTION_OPEN_MENSA_CARD)))
        assertTrue(c.openMensaCard.fired())
    }

    @Test
    fun `todos with mode new fires new todo signal too`() {
        val c = WidgetDeepLinkController()
        val intent = Intent(WidgetDeepLinkController.ACTION_OPEN_TODOS)
            .putExtra(WidgetDeepLinkController.EXTRA_MODE, WidgetDeepLinkController.MODE_NEW)
        assertTrue(c.handleIntent(intent))
        assertTrue(c.openTodos.fired())
        assertTrue(c.openNewTodo.fired())
    }

    @Test
    fun `todos without mode does not open add sheet`() {
        val c = WidgetDeepLinkController()
        assertTrue(c.handleIntent(Intent(WidgetDeepLinkController.ACTION_OPEN_TODOS)))
        assertTrue(c.openTodos.fired())
        assertFalse(c.openNewTodo.fired())
    }

    @Test
    fun `unknown or missing action is not handled`() {
        val c = WidgetDeepLinkController()
        assertFalse(c.handleIntent(Intent("irgendwas")))
        assertFalse(c.handleIntent(Intent()))
    }
}
