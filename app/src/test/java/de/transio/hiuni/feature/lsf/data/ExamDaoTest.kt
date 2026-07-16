package de.transio.hiuni.feature.lsf.data

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
import java.time.LocalDate

/**
 * DAO-Tests für [ExamDao]. Schwerpunkt: die Prune-Semantik pro Semester
 * ([ExamDao.pruneSemester] mit NOT IN keep), die NULLS-LAST-Sortierung von
 * [ExamDao.observeUpcoming]/[ExamDao.observeAll] und die `>=`-Grenze bei
 * anstehenden Klausuren.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ExamDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ExamDao

    private fun exam(
        number: String,
        semesterCode: String = "20261",
        examDate: LocalDate? = null,
        courseId: String? = null,
        source: String = ExamEntity.SOURCE_LSF
    ) = ExamEntity(
        rowId = 0L,
        veranstaltungsNumber = number,
        pruefungstext = "Prüfung $number",
        moduleName = "Modul $number",
        parentModule = null,
        examDate = examDate,
        examTime = null,
        rooms = emptyList(),
        semester = "SoSe 26",
        semesterCode = semesterCode,
        registrationDate = null,
        cancellationDeadline = null,
        pruefer = null,
        courseId = courseId,
        source = source
    )

    private fun epochDay(date: LocalDate): Long = date.toEpochDay()

    @Before
    fun setUp() {
        db = newInMemoryDatabase()
        dao = db.examDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- pruneSemester ---------------------------------------------------------

    @Test
    fun `pruneSemester löscht nur Einträge des Semesters die nicht in keep sind`() = runTest {
        dao.upsert(exam("1001", semesterCode = "20261"))
        dao.upsert(exam("1002", semesterCode = "20261"))
        dao.upsert(exam("1003", semesterCode = "20261"))
        // Anderes Semester bleibt unberührt, auch wenn seine Nummer nicht in keep steckt.
        dao.upsert(exam("1002", semesterCode = "20252"))

        val deleted = dao.pruneSemester("20261", keep = listOf("1001", "1003"))

        assertEquals(1, deleted)
        val remaining20261 = dao.findAllBySemester("20261").map { it.veranstaltungsNumber }.toSet()
        assertEquals(setOf("1001", "1003"), remaining20261)
        assertEquals(1, dao.findAllBySemester("20252").size)
    }

    @Test
    fun `pruneSemester laesst manuelle Eintraege im selben Semester unberuehrt`() = runTest {
        dao.upsert(exam("1001", semesterCode = "20261", source = ExamEntity.SOURCE_LSF))
        dao.upsert(exam("1002", semesterCode = "20261", source = ExamEntity.SOURCE_LSF))
        // Manueller Eintrag im selben Semester, NICHT in keep — darf NICHT gelöscht werden.
        dao.upsert(exam("man-abc", semesterCode = "20261", source = ExamEntity.SOURCE_MANUAL))

        // LSF-Sync sieht nur 1001 → prune soll 1002 löschen, den manuellen aber behalten.
        val deleted = dao.pruneSemester("20261", keep = listOf("1001"))

        assertEquals(1, deleted)
        val remaining = dao.findAllBySemester("20261").map { it.veranstaltungsNumber }.toSet()
        assertEquals(setOf("1001", "man-abc"), remaining)
    }

    @Test
    fun `deleteSemester loescht nur LSF-Eintraege und behaelt manuelle`() = runTest {
        dao.upsert(exam("1001", semesterCode = "20261", source = ExamEntity.SOURCE_LSF))
        dao.upsert(exam("man-abc", semesterCode = "20261", source = ExamEntity.SOURCE_MANUAL))

        val deleted = dao.deleteSemester("20261")

        assertEquals(1, deleted)
        assertEquals(
            setOf("man-abc"),
            dao.findAllBySemester("20261").map { it.veranstaltungsNumber }.toSet()
        )
    }

    @Test
    fun `deleteByRowId entfernt genau einen Eintrag`() = runTest {
        dao.upsert(exam("man-abc", semesterCode = "MANUAL", source = ExamEntity.SOURCE_MANUAL))
        val stored = dao.findByNumberAndSemester("man-abc", "MANUAL")!!

        val deleted = dao.deleteByRowId(stored.rowId)

        assertEquals(1, deleted)
        assertNull(dao.findByRowId(stored.rowId))
    }

    @Test
    fun `pruneSemester mit keep-Liste ohne Treffer löscht das ganze Semester`() = runTest {
        dao.upsert(exam("1001", semesterCode = "20261"))
        dao.upsert(exam("1002", semesterCode = "20261"))
        dao.upsert(exam("2001", semesterCode = "20252"))

        // Sentinel-keep statt emptyList(): Room rendert `NOT IN ()` als ungültiges SQLite.
        // pruneSemester wird produktiv nie mit leerer keep-Liste gerufen (LsfExamsRepository
        // übergibt die geernteten Nummern). Der Sentinel testet dieselbe "prune alles"-Semantik.
        val deleted = dao.pruneSemester("20261", keep = listOf("__none__"))

        assertEquals(2, deleted)
        assertEquals(emptyList<String>(), dao.findAllBySemester("20261").map { it.veranstaltungsNumber })
        assertEquals(1, dao.findAllBySemester("20252").size)
    }

    // --- upsert / Unique-Index -------------------------------------------------

    @Test
    fun `upsert dedupliziert über veranstaltungsNumber plus semesterCode`() = runTest {
        dao.upsert(exam("1001", semesterCode = "20261"))
        dao.upsert(exam("1001", semesterCode = "20261").copy(pruefungstext = "aktualisiert"))
        // Gleiche Nummer, anderes Semester -> eigener Eintrag.
        dao.upsert(exam("1001", semesterCode = "20252"))

        assertEquals(1, dao.findAllBySemester("20261").size)
        assertEquals("aktualisiert", dao.findByNumberAndSemester("1001", "20261")?.pruefungstext)
        assertEquals(1, dao.findAllBySemester("20252").size)
    }

    // --- observeUpcoming: Grenze + NULLS LAST ----------------------------------

    @Test
    fun `observeUpcoming schließt Klausur am today-Tag ein und filtert vergangene aus`() = runTest {
        val today = LocalDate.of(2026, 7, 16)
        dao.upsert(exam("gestern", examDate = today.minusDays(1)))
        dao.upsert(exam("heute", examDate = today))
        dao.upsert(exam("morgen", examDate = today.plusDays(1)))

        val numbers = dao.observeUpcoming(epochDay(today), limit = 10).first()
            .map { it.veranstaltungsNumber }

        // `examDate >= today` -> heute ist inklusive, gestern raus.
        assertEquals(listOf("heute", "morgen"), numbers)
    }

    @Test
    fun `observeUpcoming ordnet Klausuren ohne Datum ans Ende (NULLS LAST)`() = runTest {
        val today = LocalDate.of(2026, 7, 16)
        dao.upsert(exam("offen", examDate = null))
        dao.upsert(exam("bald", examDate = today.plusDays(2)))
        dao.upsert(exam("später", examDate = today.plusDays(10)))

        val numbers = dao.observeUpcoming(epochDay(today), limit = 10).first()
            .map { it.veranstaltungsNumber }

        // Datierte zuerst (ASC), undatierte ("Termin noch offen") ganz hinten.
        assertEquals(listOf("bald", "später", "offen"), numbers)
    }

    @Test
    fun `observeUpcoming respektiert das Limit`() = runTest {
        val today = LocalDate.of(2026, 7, 16)
        dao.upsert(exam("a", examDate = today.plusDays(1)))
        dao.upsert(exam("b", examDate = today.plusDays(2)))
        dao.upsert(exam("c", examDate = today.plusDays(3)))

        val numbers = dao.observeUpcoming(epochDay(today), limit = 2).first()
            .map { it.veranstaltungsNumber }

        assertEquals(listOf("a", "b"), numbers)
    }

    @Test
    fun `observeUpcomingCount zählt undatierte mit`() = runTest {
        val today = LocalDate.of(2026, 7, 16)
        dao.upsert(exam("gestern", examDate = today.minusDays(1)))
        dao.upsert(exam("heute", examDate = today))
        dao.upsert(exam("offen", examDate = null))

        assertEquals(2, dao.observeUpcomingCount(epochDay(today)).first())
    }

    @Test
    fun `observeUpcomingForCourse filtert nach courseId und Datum`() = runTest {
        val today = LocalDate.of(2026, 7, 16)
        dao.upsert(exam("k1", examDate = today.plusDays(1), courseId = "C1"))
        dao.upsert(exam("k2", examDate = today.plusDays(2), courseId = "C2"))
        dao.upsert(exam("k3-alt", examDate = today.minusDays(1), courseId = "C1"))

        val numbers = dao.observeUpcomingForCourse("C1", epochDay(today)).first()
            .map { it.veranstaltungsNumber }

        assertEquals(listOf("k1"), numbers)
    }

    // --- observeAll ------------------------------------------------------------

    @Test
    fun `observeAll enthält auch vergangene und undatierte mit NULLS LAST`() = runTest {
        val today = LocalDate.of(2026, 7, 16)
        dao.upsert(exam("alt", examDate = today.minusDays(30)))
        dao.upsert(exam("neu", examDate = today.plusDays(5)))
        dao.upsert(exam("offen", examDate = null))

        val numbers = dao.observeAll().first().map { it.veranstaltungsNumber }

        assertEquals(listOf("alt", "neu", "offen"), numbers)
    }

    @Test
    fun `deleteSemester entfernt alle Einträge des Semesters`() = runTest {
        dao.upsert(exam("1001", semesterCode = "20261"))
        dao.upsert(exam("1002", semesterCode = "20261"))
        dao.upsert(exam("2001", semesterCode = "20252"))

        val deleted = dao.deleteSemester("20261")

        assertEquals(2, deleted)
        assertNull(dao.findByNumberAndSemester("1001", "20261"))
        assertEquals(1, dao.findAllBySemester("20252").size)
    }
}
