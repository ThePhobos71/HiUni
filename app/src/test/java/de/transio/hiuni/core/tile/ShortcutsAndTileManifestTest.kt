package de.transio.hiuni.core.tile

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prüft Manifest und shortcuts.xml als Quelltext (Gradle-Arbeitsverzeichnis ist app/). */
class ShortcutsAndTileManifestTest {

    private fun res(path: String) = File("src/main/$path").readText()

    @Test
    fun `every launcher alias points at the shortcuts resource`() {
        val manifest = res("AndroidManifest.xml")
        val aliases = Regex("<activity-alias").findAll(manifest).count()
        val metas = Regex("""android:name="android.app.shortcuts"\s+android:resource="@xml/shortcuts"""")
            .findAll(manifest).count()
        assertTrue(aliases >= 1)
        assertEquals(aliases, metas)
    }

    @Test
    fun `tile service is exported and protected by the QS permission`() {
        val manifest = res("AndroidManifest.xml")
        val block = Regex("<service[^>]*MensaCardTileService[\\s\\S]*?</service>").find(manifest)?.value
            ?: error("Tile-Service fehlt im Manifest")
        assertTrue(block.contains("""android:exported="true""""))
        assertTrue(block.contains("""android:permission="android.permission.BIND_QUICK_SETTINGS_TILE""""))
        assertTrue(block.contains("android.service.quicksettings.action.QS_TILE"))
        assertTrue(block.contains("@drawable/ic_tile_mensa_card"))
    }

    @Test
    fun `shortcuts xml has four shortcuts with expected actions`() {
        val xml = res("res/xml/shortcuts.xml")
        assertEquals(4, Regex("<shortcut\\b").findAll(xml).count())
        listOf("OPEN_MENSA", "OPEN_TODOS", "OPEN_BIB", "OPEN_CALENDAR").forEach {
            assertTrue("fehlt: $it", xml.contains("de.transio.hiuni.$it"))
        }
        assertTrue(xml.contains("""android:name="mode" android:value="new""""))
    }
}
