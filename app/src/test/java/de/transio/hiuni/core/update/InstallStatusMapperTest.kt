package de.transio.hiuni.core.update

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallStatusMapperTest {

    @Test
    fun `Bestaetigung noetig wird erkannt`() {
        assertEquals(InstallOutcome.PendingUserAction, InstallStatusMapper.map(PackageInstaller.STATUS_PENDING_USER_ACTION, null))
    }

    @Test
    fun `Erfolg und Abbruch`() {
        assertEquals(InstallOutcome.Success, InstallStatusMapper.map(PackageInstaller.STATUS_SUCCESS, null))
        assertEquals(InstallOutcome.Aborted, InstallStatusMapper.map(PackageInstaller.STATUS_FAILURE_ABORTED, "x"))
    }

    @Test
    fun `Fehler bekommen deutsche Meldungen`() {
        val storage = InstallStatusMapper.map(PackageInstaller.STATUS_FAILURE_STORAGE, null)
        assertEquals(InstallOutcome.Failure("Zu wenig Speicher für das Update"), storage)
        val conflict = InstallStatusMapper.map(PackageInstaller.STATUS_FAILURE_CONFLICT, null)
        assertTrue(conflict is InstallOutcome.Failure)
        val other = InstallStatusMapper.map(PackageInstaller.STATUS_FAILURE, "INSTALL_FAILED_X")
        assertEquals(InstallOutcome.Failure("Installation fehlgeschlagen (INSTALL_FAILED_X)"), other)
    }
}
