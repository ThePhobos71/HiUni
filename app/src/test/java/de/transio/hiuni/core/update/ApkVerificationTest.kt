package de.transio.hiuni.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkVerificationTest {

    private val installed = InstalledFacts(
        packageName = "de.transio.hiuni",
        versionCode = 10401,
        signerDigests = setOf("aa11")
    )

    private fun apk(
        pkg: String = "de.transio.hiuni",
        code: Long = 10402,
        signers: Set<String> = setOf("aa11"),
        history: Set<String> = signers
    ) = ApkFacts(pkg, code, signers, history)

    @Test
    fun `passendes APK ist ok`() {
        assertEquals(ApkCheck.Ok, ApkVerification.verify(installed, apk(), expectedVersionCode = 10402))
    }

    @Test
    fun `nicht lesbares APK wird abgelehnt`() {
        assertEquals(ApkCheck.Unreadable, ApkVerification.verify(installed, null, 10402))
    }

    @Test
    fun `fremder Paketname wird abgelehnt`() {
        assertEquals(ApkCheck.WrongPackage, ApkVerification.verify(installed, apk(pkg = "com.evil"), 10402))
    }

    @Test
    fun `fremde Signatur wird abgelehnt`() {
        assertEquals(ApkCheck.SignatureMismatch, ApkVerification.verify(installed, apk(signers = setOf("bb22")), 10402))
    }

    @Test
    fun `fehlende Signaturdaten gelten als fremde Signatur`() {
        assertEquals(ApkCheck.SignatureMismatch, ApkVerification.verify(installed, apk(signers = emptySet()), 10402))
        val noInstalled = installed.copy(signerDigests = emptySet())
        assertEquals(ApkCheck.SignatureMismatch, ApkVerification.verify(noInstalled, apk(signers = emptySet()), 10402))
    }

    @Test
    fun `rotierter Schluessel mit installiertem Zertifikat in der Historie ist ok`() {
        val rotated = apk(signers = setOf("cc33"), history = setOf("aa11", "cc33"))
        assertEquals(ApkCheck.Ok, ApkVerification.verify(installed, rotated, 10402))
    }

    @Test
    fun `gleiche oder aeltere Version wird abgelehnt`() {
        assertEquals(ApkCheck.NotNewer, ApkVerification.verify(installed, apk(code = 10401), null))
        assertEquals(ApkCheck.NotNewer, ApkVerification.verify(installed, apk(code = 10300), null))
    }

    @Test
    fun `APK mit anderer Version als der Release-Tag wird abgelehnt`() {
        assertEquals(ApkCheck.VersionMismatch, ApkVerification.verify(installed, apk(code = 10403), expectedVersionCode = 10402))
    }

    @Test
    fun `Signatur wird vor der Version geprueft`() {
        val bad = apk(signers = setOf("bb22"), code = 10000)
        assertEquals(ApkCheck.SignatureMismatch, ApkVerification.verify(installed, bad, 10402))
    }

    @Test
    fun `Signatur-Fehlermeldung ist eindeutig`() {
        assertEquals("Signatur passt nicht, Update abgebrochen", ApkVerification.message(ApkCheck.SignatureMismatch))
        for (check in listOf(ApkCheck.Unreadable, ApkCheck.WrongPackage, ApkCheck.NotNewer, ApkCheck.VersionMismatch)) {
            assertTrue(ApkVerification.message(check).isNotBlank())
        }
    }

    @Test
    fun `sha256 liefert Hex in Kleinbuchstaben`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ApkVerification.sha256Hex("abc".toByteArray())
        )
    }

    @Test
    fun `Groessenpruefung`() {
        assertTrue(ApkVerification.sizeMatches(expected = 100, actual = 100))
        assertEquals(false, ApkVerification.sizeMatches(expected = 100, actual = 99))
    }
}
