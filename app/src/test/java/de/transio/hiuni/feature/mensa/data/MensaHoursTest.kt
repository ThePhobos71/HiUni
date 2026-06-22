package de.transio.hiuni.feature.mensa.data

import de.transio.hiuni.feature.mensa.Mealtime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class MensaHoursTest {

    private val monday = LocalDate.of(2026, 5, 25)
    private val saturday = LocalDate.of(2026, 5, 23)
    private val sunday = LocalDate.of(2026, 5, 24)

    @Test
    fun `isOpenNow returns false on weekend`() {
        assertFalse(MensaHours.isOpenNow(today = saturday, now = LocalTime.of(12, 0)))
        assertFalse(MensaHours.isOpenNow(today = sunday, now = LocalTime.of(12, 0)))
    }

    @Test
    fun `isOpenNow returns true during Mittag window on weekday`() {
        assertTrue(MensaHours.isOpenNow(today = monday, now = LocalTime.of(12, 30)))
    }

    @Test
    fun `isOpenNow returns true during Abend window on weekday`() {
        assertTrue(MensaHours.isOpenNow(today = monday, now = LocalTime.of(18, 0)))
    }

    @Test
    fun `isOpenNow returns false between Mittag and Abend`() {
        assertFalse(MensaHours.isOpenNow(today = monday, now = LocalTime.of(15, 30)))
    }

    @Test
    fun `isOpenNow returns false before Mittag`() {
        assertFalse(MensaHours.isOpenNow(today = monday, now = LocalTime.of(9, 0)))
    }

    @Test
    fun `statusFor non-today date returns Preview`() {
        val tomorrow = monday.plusDays(1)
        val status = MensaHours.statusFor(
            date = tomorrow,
            mealtime = Mealtime.MITTAG,
            today = monday,
            now = LocalTime.of(12, 0)
        )
        assertEquals(OpenStatus.Preview, status)
    }

    @Test
    fun `statusFor weekend date returns ClosedToday`() {
        val status = MensaHours.statusFor(
            date = sunday,
            mealtime = Mealtime.MITTAG,
            today = sunday,
            now = LocalTime.of(12, 0)
        )
        assertEquals(OpenStatus.ClosedToday, status)
    }

    @Test
    fun `statusFor before opening returns OpensLater`() {
        val status = MensaHours.statusFor(
            date = monday,
            mealtime = Mealtime.MITTAG,
            today = monday,
            now = LocalTime.of(9, 0)
        )
        assertTrue(status is OpenStatus.OpensLater)
        assertEquals(LocalTime.of(11, 30), (status as OpenStatus.OpensLater).time)
    }

    @Test
    fun `statusFor in window returns Open`() {
        val status = MensaHours.statusFor(
            date = monday,
            mealtime = Mealtime.MITTAG,
            today = monday,
            now = LocalTime.of(12, 0)
        )
        assertEquals(OpenStatus.Open, status)
    }

    @Test
    fun `statusFor near close returns ClosingSoon`() {
        val status = MensaHours.statusFor(
            date = monday,
            mealtime = Mealtime.MITTAG,
            today = monday,
            now = LocalTime.of(14, 10)
        )
        assertTrue(status is OpenStatus.ClosingSoon)
        assertEquals(20L, (status as OpenStatus.ClosingSoon).minutes)
    }

    @Test
    fun `statusFor after close returns ClosedToday`() {
        val status = MensaHours.statusFor(
            date = monday,
            mealtime = Mealtime.MITTAG,
            today = monday,
            now = LocalTime.of(15, 0)
        )
        assertEquals(OpenStatus.ClosedToday, status)
    }
}
