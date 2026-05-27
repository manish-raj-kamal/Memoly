package com.memoly.dock.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Calendar

class ReminderParserTest {

    @Test
    fun parsesTomorrowPresetWithoutSkippingAnExtraDay() {
        val now = calendarMillis(2026, Calendar.MAY, 19, 15, 0)

        val result = ReminderParser.parse("?rem tomorrow 9am", now)

        assertEquals("", result.cleanedText)
        assertEquals(calendarMillis(2026, Calendar.MAY, 20, 9, 0), result.reminderTimeMillis)
    }

    @Test
    fun parsesDatePresetInTheCurrentYearWhenStillAhead() {
        val now = calendarMillis(2026, Calendar.MAY, 19, 9, 0)

        val result = ReminderParser.parse("?rem 20 May 10am", now)

        assertNotNull(result.reminderTimeMillis)
        assertEquals(calendarMillis(2026, Calendar.MAY, 20, 10, 0), result.reminderTimeMillis)
    }

    @Test
    fun rollsDatePresetToNextYearWhenDateHasAlreadyPassed() {
        val now = calendarMillis(2026, Calendar.MAY, 21, 9, 0)

        val result = ReminderParser.parse("?rem 20 May 10am", now)

        assertEquals(calendarMillis(2027, Calendar.MAY, 20, 10, 0), result.reminderTimeMillis)
    }

    private fun calendarMillis(
        year: Int,
        month: Int,
        dayOfMonth: Int,
        hourOfDay: Int,
        minute: Int
    ): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
