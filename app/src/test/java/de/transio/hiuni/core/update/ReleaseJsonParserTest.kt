package de.transio.hiuni.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseJsonParserTest {

    private fun fixture(): String =
        requireNotNull(javaClass.getResourceAsStream("/update/release_latest.json")) {
            "Fixture fehlt"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `liest Tag, Titel, Changelog, Link und APK-Asset`() {
        val release = requireNotNull(ReleaseJsonParser.parse(fixture()))
        assertEquals("v1.4.2", release.tag)
        assertEquals(AppVersion(1, 4, 2), release.version)
        assertEquals("HiUni 1.4.2", release.title)
        assertEquals(
            "## Neu\n- Self-Update aus GitHub Releases\n- Mensa-Preisgruppe\n\n## Behoben\n- Offline-Banner",
            release.changelog
        )
        assertEquals("https://github.com/ThePhobos71/HiUni/releases/tag/v1.4.2", release.htmlUrl)
        val apk = requireNotNull(release.apk)
        assertEquals("HiUni-1.4.2.apk", apk.name)
        assertEquals(18734512L, apk.sizeBytes)
        assertEquals(
            "https://github.com/ThePhobos71/HiUni/releases/download/v1.4.2/HiUni-1.4.2.apk",
            apk.downloadUrl
        )
    }

    @Test
    fun `Release ohne APK hat kein apk-Asset`() {
        val json = """{"tag_name":"v1.0.0","name":"x","body":"","html_url":"u",
            |"assets":[{"name":"notes.txt","browser_download_url":"d","size":1}]}""".trimMargin()
        val release = requireNotNull(ReleaseJsonParser.parse(json))
        assertNull(release.apk)
    }

    @Test
    fun `passend benanntes APK hat Vorrang vor anderen APKs`() {
        val json = """{"tag_name":"v1.0.1","name":null,"body":null,"html_url":"u","assets":[
            |{"name":"other.apk","browser_download_url":"a","size":1},
            |{"name":"HiUni-1.0.1.apk","browser_download_url":"b","size":2}]}""".trimMargin()
        val release = requireNotNull(ReleaseJsonParser.parse(json))
        assertEquals("HiUni-1.0.1.apk", release.apk?.name)
        // Fehlender Titel faellt auf "HiUni <Version>" zurueck, fehlender Body auf leer.
        assertEquals("HiUni 1.0.1", release.title)
        assertEquals("", release.changelog)
    }

    @Test
    fun `Tag ohne Versionsschema ergibt null`() {
        val json = """{"tag_name":"nightly","name":"x","body":"","html_url":"u","assets":[]}"""
        assertNull(ReleaseJsonParser.parse(json))
    }

    @Test
    fun `Entwurf und Vorabversion werden ignoriert`() {
        val draft = """{"tag_name":"v1.0.0","draft":true,"html_url":"u","assets":[]}"""
        val pre = """{"tag_name":"v1.0.0","prerelease":true,"html_url":"u","assets":[]}"""
        assertNull(ReleaseJsonParser.parse(draft))
        assertNull(ReleaseJsonParser.parse(pre))
    }

    @Test(expected = UpdateException::class)
    fun `kaputtes JSON wirft UpdateException`() {
        ReleaseJsonParser.parse("<html>rate limited</html>")
    }
}
