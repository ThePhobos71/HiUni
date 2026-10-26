package de.transio.hiuni.core.search

import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import de.transio.hiuni.feature.courses.data.CourseEntity
import de.transio.hiuni.feature.email.data.EmailEntity
import de.transio.hiuni.feature.learnweb.data.LearnwebAssignment
import de.transio.hiuni.feature.learnweb.data.LearnwebCourse
import de.transio.hiuni.feature.lsf.data.ExamEntity
import de.transio.hiuni.feature.mensa.data.MealEntity
import de.transio.hiuni.feature.sport.data.SportEventEntity
import java.time.Instant
import java.time.LocalDate

/** Gemeinsame Test-Entities für Spotlight-Treffer und die Deep-Link-Konsumenten. */
object SearchFixtures {

    fun email(rowId: Long = 42L, subject: String = "Klausurtermin") = EmailEntity(
        rowId = rowId,
        uid = rowId,
        folder = EmailEntity.FOLDER_INBOX,
        fromAddress = "pruefungsamt@uni.de",
        fromName = "Prüfungsamt",
        subject = subject,
        snippet = "",
        bodyPlain = "Hallo",
        receivedAt = Instant.parse("2026-03-01T10:00:00Z"),
        isRead = true
    )

    fun event(id: Long = 7L, start: Instant = Instant.parse("2026-11-12T09:00:00Z")) =
        CustomEventEntity(
            id = id,
            title = "Lerngruppe",
            startTime = start,
            endTime = start.plusSeconds(3600)
        )

    fun course(id: String = "c1", semester: String = "WiSe 25/26") = CourseEntity(
        id = id,
        name = "Logistik",
        professor = "Prof",
        credits = 5,
        semester = semester,
        source = CourseEntity.SOURCE_LSF,
        lsfId = "lsf-$id"
    )

    fun exam(rowId: Long = 11L, date: LocalDate? = LocalDate.of(2027, 2, 10)) = ExamEntity(
        rowId = rowId,
        veranstaltungsNumber = "3204",
        pruefungstext = "Klausur 3204",
        moduleName = "Modul 3204",
        parentModule = null,
        examDate = date,
        examTime = null,
        rooms = emptyList(),
        semester = "WiSe 25/26",
        semesterCode = "20252",
        registrationDate = null,
        cancellationDeadline = null,
        pruefer = null,
        courseId = null
    )

    fun meal(
        date: LocalDate = LocalDate.of(2026, 10, 28),
        category: String = "Hauptgericht"
    ) = MealEntity(
        sourceId = "m1",
        locationId = 150,
        date = date,
        category = category,
        name = "Spinatlasagne",
        description = null,
        priceStudentCents = 350,
        priceEmployeeCents = null,
        priceGuestCents = null,
        tags = ""
    )

    fun sport(slotId: Long = 99L) = SportEventEntity(
        supersaasSlotId = slotId,
        startTime = Instant.parse("2026-11-01T17:00:00Z"),
        endTime = Instant.parse("2026-11-01T18:30:00Z"),
        title = "Basketball",
        description = null,
        location = "Halle",
        capacity = 20,
        currentBookings = 3,
        waitlistCount = 0,
        isCancelled = false,
        isPaidOnly = false
    )

    fun learnwebCourse() = LearnwebCourse(
        courseId = 5L,
        name = "Statistik",
        url = "https://learnweb.example/course/view.php?id=5",
        syncedAt = 0L
    )

    fun learnwebAssignment() = LearnwebAssignment(
        eventId = 8L,
        title = "Übungsblatt 3",
        dueEpoch = 0L,
        url = "https://learnweb.example/mod/assign/view.php?id=8",
        syncedAt = 0L
    )
}
