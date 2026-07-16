package de.transio.hiuni.feature.mensacard.data

import de.transio.hiuni.core.database.AppDatabase
import de.transio.hiuni.core.database.newInMemoryDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DAO-Tests für [MensaCardTransactionDao] — die Scan-Historie der eigenen Karte.
 * Schwerpunkt: pro-UID-Isolation, DESC-Sortierung nach scannedAt, das LIMIT von
 * [MensaCardTransactionDao.observeLatest] und die [MensaCardTransactionDao.updateScannedAt]-
 * Semantik (bestehenden Eintrag "nach vorn" schieben statt duplizieren).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MensaCardTransactionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MensaCardTransactionDao

    private fun tx(
        uid: String,
        balance: Int,
        delta: Int,
        scannedAt: Long
    ) = MensaCardTransactionEntity(
        id = 0L,
        uid = uid,
        balanceMilliEuro = balance,
        deltaMilliEuro = delta,
        scannedAt = scannedAt
    )

    @Before
    fun setUp() {
        db = newInMemoryDatabase()
        dao = db.mensaCardTransactionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `observeLatest liefert Verlauf einer Karte neueste zuerst`() = runTest {
        dao.insert(tx("A", balance = 5000, delta = 0, scannedAt = 100))
        dao.insert(tx("A", balance = 4700, delta = -300, scannedAt = 200))
        dao.insert(tx("A", balance = 4400, delta = -300, scannedAt = 300))

        val balances = dao.observeLatest("A").first().map { it.balanceMilliEuro }

        assertEquals(listOf(4400, 4700, 5000), balances)
    }

    @Test
    fun `observeLatest isoliert nach uid`() = runTest {
        dao.insert(tx("A", balance = 5000, delta = 0, scannedAt = 100))
        dao.insert(tx("B", balance = 9000, delta = 0, scannedAt = 150))

        val onlyA = dao.observeLatest("A").first()

        assertEquals(1, onlyA.size)
        assertEquals("A", onlyA.single().uid)
    }

    @Test
    fun `observeLatest respektiert das Limit und nimmt die jüngsten`() = runTest {
        repeat(5) { i ->
            dao.insert(tx("A", balance = 5000 - i * 100, delta = -100, scannedAt = (i + 1).toLong()))
        }

        val limited = dao.observeLatest("A", limit = 2).first()

        // limit 2 -> die zwei jüngsten Scans (scannedAt 5 und 4).
        assertEquals(listOf(5L, 4L), limited.map { it.scannedAt })
    }

    @Test
    fun `latestFor liefert den jüngsten Scan der Karte`() = runTest {
        dao.insert(tx("A", balance = 5000, delta = 0, scannedAt = 100))
        dao.insert(tx("A", balance = 4700, delta = -300, scannedAt = 300))
        dao.insert(tx("A", balance = 4800, delta = 100, scannedAt = 200))

        val latest = dao.latestFor("A")

        assertEquals(300L, latest?.scannedAt)
        assertEquals(4700, latest?.balanceMilliEuro)
    }

    @Test
    fun `latestFor liefert null für unbekannte uid`() = runTest {
        dao.insert(tx("A", balance = 5000, delta = 0, scannedAt = 100))

        assertNull(dao.latestFor("unbekannt"))
    }

    @Test
    fun `updateScannedAt schiebt bestehenden Eintrag nach vorn statt zu duplizieren`() = runTest {
        val id = dao.insert(tx("A", balance = 5000, delta = 0, scannedAt = 100))

        dao.updateScannedAt(id, scannedAt = 999)

        val all = dao.observeLatest("A").first()
        assertEquals(1, all.size)
        assertEquals(999L, all.single().scannedAt)
    }

    @Test
    fun `deleteAllFor löscht nur die Zeilen der Karte`() = runTest {
        dao.insert(tx("A", balance = 5000, delta = 0, scannedAt = 100))
        dao.insert(tx("A", balance = 4700, delta = -300, scannedAt = 200))
        dao.insert(tx("B", balance = 9000, delta = 0, scannedAt = 150))

        dao.deleteAllFor("A")

        assertEquals(emptyList<Long>(), dao.observeLatest("A").first().map { it.scannedAt })
        assertEquals(1, dao.observeLatest("B").first().size)
    }
}
