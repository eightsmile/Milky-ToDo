package com.quicktodo

import com.quicktodo.util.mergeDateWithExistingTime
import com.quicktodo.util.parseChineseDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DateUtilsTest {
    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun parseChineseDate_plainWeekdayUsesNextFutureOccurrenceAtEndOfDay() {
        val base = millis(2026, 7, 15, 10, 0, 0) // Wednesday
        val expected = millis(2026, 7, 17, 23, 59, 59) // Friday
        assertEquals(expected, parseChineseDate("周五", base))
    }

    @Test
    fun parseChineseDate_nextWeekdaySkipsToFollowingWeek() {
        val base = millis(2026, 7, 15, 10, 0, 0) // Wednesday
        val expected = millis(2026, 7, 27, 23, 59, 59) // next Monday per current app semantics
        assertEquals(expected, parseChineseDate("下周一", base))
    }

    @Test
    fun parseChineseDate_unknownTextReturnsNull() {
        val base = millis(2026, 7, 15, 10, 0, 0)
        assertNull(parseChineseDate("某一天", base))
    }

    @Test
    fun mergeDateWithExistingTime_preservesCustomTime() {
        val selectedDate = millis(2026, 8, 1, 0, 0, 0)
        val existing = millis(2026, 7, 15, 14, 30, 0)
        val expected = millis(2026, 8, 1, 14, 30, 0)
        assertEquals(expected, mergeDateWithExistingTime(selectedDate, existing))
    }

    @Test
    fun mergeDateWithExistingTime_defaultsPlainDateToEndOfDay() {
        val selectedDate = millis(2026, 8, 1, 0, 0, 0)
        val expected = millis(2026, 8, 1, 23, 59, 59)
        assertEquals(expected, mergeDateWithExistingTime(selectedDate, null))
    }
}
