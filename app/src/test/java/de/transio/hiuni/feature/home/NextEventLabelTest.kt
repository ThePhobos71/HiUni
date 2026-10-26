package de.transio.hiuni.feature.home

import de.transio.hiuni.feature.calendar.data.CustomEventEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class NextEventLabelTest {

    private fun event(source: String) = CustomEventEntity(
        title = "x",
        startTime = Instant.EPOCH,
        endTime = Instant.EPOCH,
        sourceKind = source
    )

    @Test
    fun `Label richtet sich nach der Quelle`() {
        assertEquals("NÄCHSTE VORLESUNG", nextEventLabel(event(CustomEventEntity.SOURCE_LSF_STUNDENPLAN)))
        assertEquals("NÄCHSTER FILM", nextEventLabel(event(CustomEventEntity.SOURCE_MOVIE_PIN)))
        assertEquals("NÄCHSTER SPORTKURS", nextEventLabel(event(CustomEventEntity.SOURCE_SPORT_PIN)))
        assertEquals("NÄCHSTE RAUMBUCHUNG", nextEventLabel(event(CustomEventEntity.SOURCE_BIB_BOOKING)))
        assertEquals("NÄCHSTE ABGABE", nextEventLabel(event(CustomEventEntity.SOURCE_LEARNWEB_ICAL)))
        assertEquals("NÄCHSTE ABGABE", nextEventLabel(event(CustomEventEntity.SOURCE_LEARNWEB_ASSIGNMENT)))
    }

    @Test
    fun `eigene Termine und kein Termin heissen neutral Naechster Termin`() {
        assertEquals("NÄCHSTER TERMIN", nextEventLabel(event(CustomEventEntity.SOURCE_USER)))
        assertEquals("NÄCHSTER TERMIN", nextEventLabel(null))
    }
}
