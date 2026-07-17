package de.transio.hiuni.feature.widgets.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Absicherung der reinen Kappungs-Logik hinter der harten Glance-Grenze von
 * 10 direkten Kindern pro Container ([GLANCE_MAX_CONTAINER_CHILDREN]).
 */
class WidgetLimitsTest {

    @Test
    fun `Grenze ist 10`() {
        assertEquals(10, GLANCE_MAX_CONTAINER_CHILDREN)
    }

    @Test
    fun `ohne feste Geschwister passen genau 10 Eintraege`() {
        assertEquals(10, capForContainer())
    }

    @Test
    fun `feste Geschwister reduzieren das Budget`() {
        assertEquals(7, capForContainer(fixedSiblings = 3))
        assertEquals(9, capForContainer(fixedSiblings = 1))
    }

    @Test
    fun `Overflow-Hinweis belegt einen eigenen Slot`() {
        // 10 - 0 fixed - 1 Overflow-Slot = 9 Listen-Eintraege
        assertEquals(9, capForContainer(reserveForOverflow = true))
        // 10 - 2 fixed - 1 Overflow-Slot = 7 Listen-Eintraege
        assertEquals(7, capForContainer(fixedSiblings = 2, reserveForOverflow = true))
    }

    @Test
    fun `Budget faellt nie unter Null`() {
        assertEquals(0, capForContainer(fixedSiblings = 12))
        assertEquals(0, capForContainer(fixedSiblings = 10, reserveForOverflow = true))
    }
}
