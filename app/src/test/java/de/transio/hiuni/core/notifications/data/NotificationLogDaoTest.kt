package de.transio.hiuni.core.notifications.data

import de.transio.hiuni.core.database.AppDatabase
import de.transio.hiuni.core.database.newInMemoryDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * DAO-Tests für [NotificationLogDao]. Schwerpunkt: das LIMIT- und Sortier-Verhalten
 * von [NotificationLogDao.observeRecent] (neueste zuerst, gekappt) sowie die
 * strikte "<"-Grenze von [NotificationLogDao.pruneOlderThan].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class NotificationLogDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NotificationLogDao

    private fun log(
        title: String,
        firedAt: Instant,
        isRead: Boolean = false,
        kind: NotificationKind = NotificationKind.SYSTEM
    ) = NotificationLogEntity(
        id = 0L,
        kind = kind,
        title = title,
        firedAt = firedAt,
        isRead = isRead
    )

    @Before
    fun setUp() {
        db = newInMemoryDatabase()
        dao = db.notificationLogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- observeRecent: LIMIT + Sortierung -------------------------------------

    @Test
    fun `observeRecent liefert neueste zuerst`() = runTest {
        val base = Instant.parse("2026-07-16T10:00:00Z")
        dao.insert(log("alt", base))
        dao.insert(log("mittel", base.plusSeconds(60)))
        dao.insert(log("neu", base.plusSeconds(120)))

        val titles = dao.observeRecent(limit = 10).first().map { it.title }

        assertEquals(listOf("neu", "mittel", "alt"), titles)
    }

    @Test
    fun `observeRecent kappt auf das Limit und behält die neuesten`() = runTest {
        val base = Instant.parse("2026-07-16T10:00:00Z")
        repeat(5) { i ->
            dao.insert(log("n$i", base.plusSeconds(i.toLong())))
        }

        val titles = dao.observeRecent(limit = 3).first().map { it.title }

        // Limit 3 -> die drei jüngsten (n4, n3, n2), absteigend.
        assertEquals(listOf("n4", "n3", "n2"), titles)
    }

    // --- observeUnreadCount ----------------------------------------------------

    @Test
    fun `observeUnreadCount zählt nur ungelesene`() = runTest {
        val base = Instant.parse("2026-07-16T10:00:00Z")
        dao.insert(log("u1", base, isRead = false))
        dao.insert(log("u2", base.plusSeconds(1), isRead = false))
        dao.insert(log("gelesen", base.plusSeconds(2), isRead = true))

        assertEquals(2, dao.observeUnreadCount().first())
    }

    @Test
    fun `markAllRead setzt alle ungelesenen auf gelesen`() = runTest {
        val base = Instant.parse("2026-07-16T10:00:00Z")
        dao.insert(log("a", base, isRead = false))
        dao.insert(log("b", base.plusSeconds(1), isRead = false))

        dao.markAllRead()

        assertEquals(0, dao.observeUnreadCount().first())
    }

    @Test
    fun `markRead markiert nur den adressierten Eintrag`() = runTest {
        val base = Instant.parse("2026-07-16T10:00:00Z")
        val id1 = dao.insert(log("a", base, isRead = false))
        dao.insert(log("b", base.plusSeconds(1), isRead = false))

        dao.markRead(id1)

        assertEquals(1, dao.observeUnreadCount().first())
    }

    // --- pruneOlderThan --------------------------------------------------------

    @Test
    fun `pruneOlderThan löscht strikt älter als cutoff und behält den Grenzwert`() = runTest {
        val cutoff = Instant.parse("2026-07-16T10:00:00Z")
        dao.insert(log("davor", cutoff.minusMillis(1)))
        dao.insert(log("exakt cutoff", cutoff))
        dao.insert(log("danach", cutoff.plusMillis(1)))

        dao.pruneOlderThan(cutoff.toEpochMilli())

        // `firedAt < cutoff` -> der Grenzwert (==cutoff) überlebt.
        val titles = dao.observeRecent().first().map { it.title }.toSet()
        assertEquals(setOf("exakt cutoff", "danach"), titles)
    }

    @Test
    fun `kind roundtrip über den TypeConverter`() = runTest {
        dao.insert(log("exam", Instant.parse("2026-07-16T10:00:00Z"), kind = NotificationKind.EXAM))

        val stored = dao.observeRecent().first().single()

        assertEquals(NotificationKind.EXAM, stored.kind)
    }
}
