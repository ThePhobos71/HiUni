package de.transio.hiuni.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `Tag mit v-Praefix wird gelesen`() {
        assertEquals(AppVersion(1, 4, 2), AppVersion.parse("v1.4.2"))
        assertEquals(AppVersion(1, 4, 2), AppVersion.parse("V1.4.2"))
    }

    @Test
    fun `Versionsname ohne Praefix und mit Build-Suffix wird gelesen`() {
        assertEquals(AppVersion(1, 4, 2), AppVersion.parse("1.4.2"))
        assertEquals(AppVersion(1, 4, 2), AppVersion.parse("1.4.2-debug"))
        assertEquals(AppVersion(2, 0, 10), AppVersion.parse(" v2.0.10 "))
    }

    @Test
    fun `ungueltige Tags ergeben null`() {
        assertNull(AppVersion.parse("HiUni 1.4.2"))
        assertNull(AppVersion.parse("v1.4"))
        assertNull(AppVersion.parse("1.4.2.1"))
        assertNull(AppVersion.parse("v1.100.0"))
        assertNull(AppVersion.parse("v1.4.100"))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
    }

    @Test
    fun `versionCode folgt major mal 10000 plus minor mal 100 plus patch`() {
        assertEquals(10402, AppVersion(1, 4, 2).code)
        assertEquals(20010, AppVersion(2, 0, 10).code)
        assertEquals(9999, AppVersion(0, 99, 99).code)
    }

    @Test
    fun `Name ist ohne Praefix`() {
        assertEquals("1.4.2", AppVersion(1, 4, 2).name)
    }

    @Test
    fun `Vergleich folgt dem versionCode`() {
        assertTrue(AppVersion(1, 4, 10) > AppVersion(1, 4, 9))
        assertTrue(AppVersion(1, 10, 0) > AppVersion(1, 9, 99))
        assertTrue(AppVersion(2, 0, 0) > AppVersion(1, 99, 99))
        assertEquals(0, AppVersion(1, 4, 2).compareTo(AppVersion(1, 4, 2)))
    }
}
