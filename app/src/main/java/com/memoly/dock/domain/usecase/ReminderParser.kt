package com.memoly.dock.domain.usecase

import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

/**
 * Parses the ?rem command from user input to extract reminder times.
 *
 * Supported formats:
 *  - ?rem 7pm / ?rem 7:30pm
 *  - ?rem tomorrow 9am
 *  - ?rem 25 dec 10am / ?rem dec 25 10am
 *  - ?rem 15/05 6pm / ?rem 15-05 6pm
 *  - ?rem in 2 hours / ?rem in 30 minutes
 */
data class ReminderParseResult(
    val cleanedText: String,
    val reminderTimeMillis: Long?
)

object ReminderParser {

    private val REMINDER_PATTERN = Pattern.compile(
        """\?rem\s+(.+)$""",
        Pattern.CASE_INSENSITIVE
    )

    private val TIME_PATTERN = Pattern.compile(
        """(\d{1,2})(?::(\d{2}))?\s*(am|pm)""",
        Pattern.CASE_INSENSITIVE
    )

    private val RELATIVE_PATTERN = Pattern.compile(
        """in\s+(\d+)\s+(hour|hours|minute|minutes|min|mins)""",
        Pattern.CASE_INSENSITIVE
    )

    private val TOMORROW_PATTERN = Pattern.compile(
        """tomorrow\s+(.+)""",
        Pattern.CASE_INSENSITIVE
    )

    // Patterns for dates like "25 dec" or "dec 25"
    private val DATE_MMM_PATTERN = Pattern.compile(
        """(\d{1,2})\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)""",
        Pattern.CASE_INSENSITIVE
    )
    private val MMM_DATE_PATTERN = Pattern.compile(
        """(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\s+(\d{1,2})""",
        Pattern.CASE_INSENSITIVE
    )
    
    // Pattern for numeric dates like "15/05" or "15-05"
    private val NUMERIC_DATE_PATTERN = Pattern.compile(
        """(\d{1,2})[/-](\d{1,2})""",
        Pattern.CASE_INSENSITIVE
    )

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /**
     * Parse the input text for a ?rem command.
     * Returns cleaned text and the computed reminder time if found.
     */
    fun parse(input: String): ReminderParseResult {
        val matcher = REMINDER_PATTERN.matcher(input)

        if (!matcher.find()) {
            return ReminderParseResult(cleanedText = input.trim(), reminderTimeMillis = null)
        }

        val commandText = matcher.group(1)?.trim() ?: return ReminderParseResult(
            cleanedText = input.trim(),
            reminderTimeMillis = null
        )

        // Remove the ?rem command from the original text
        val cleanedText = input.substring(0, matcher.start()).trim()

        val reminderTime = parseTimeExpression(commandText)

        return ReminderParseResult(
            cleanedText = cleanedText,
            reminderTimeMillis = reminderTime
        )
    }

    private fun parseTimeExpression(expression: String): Long? {
        // 1. Check for "in X hours/minutes" pattern
        val relativeMatcher = RELATIVE_PATTERN.matcher(expression)
        if (relativeMatcher.find()) {
            val amount = relativeMatcher.group(1)?.toIntOrNull() ?: return null
            val unit = relativeMatcher.group(2)?.lowercase(Locale.ROOT) ?: return null

            val cal = Calendar.getInstance()
            when {
                unit.startsWith("hour") -> cal.add(Calendar.HOUR_OF_DAY, amount)
                unit.startsWith("min") -> cal.add(Calendar.MINUTE, amount)
            }
            return cal.timeInMillis
        }

        // 2. Check for "tomorrow Xam/pm" pattern
        val tomorrowMatcher = TOMORROW_PATTERN.matcher(expression)
        if (tomorrowMatcher.find()) {
            val timeStr = tomorrowMatcher.group(1) ?: return null
            val time = parseAbsoluteTime(timeStr) ?: return null
            val cal = Calendar.getInstance()
            cal.timeInMillis = time
            cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }

        // 3. Check for specific dates
        var targetDay = -1
        var targetMonth = -1

        val mmmDateMatcher = MMM_DATE_PATTERN.matcher(expression)
        val dateMmmMatcher = DATE_MMM_PATTERN.matcher(expression)
        val numericDateMatcher = NUMERIC_DATE_PATTERN.matcher(expression)

        when {
            mmmDateMatcher.find() -> {
                targetMonth = MONTHS.indexOf(mmmDateMatcher.group(1)?.lowercase(Locale.ROOT))
                targetDay = mmmDateMatcher.group(2)?.toIntOrNull() ?: -1
            }
            dateMmmMatcher.find() -> {
                targetDay = dateMmmMatcher.group(1)?.toIntOrNull() ?: -1
                targetMonth = MONTHS.indexOf(dateMmmMatcher.group(2)?.lowercase(Locale.ROOT))
            }
            numericDateMatcher.find() -> {
                targetDay = numericDateMatcher.group(1)?.toIntOrNull() ?: -1
                targetMonth = (numericDateMatcher.group(2)?.toIntOrNull() ?: 0) - 1
            }
        }

        if (targetDay != -1 && targetMonth != -1) {
            val time = parseAbsoluteTime(expression) ?: return null
            val cal = Calendar.getInstance()
            cal.timeInMillis = time
            cal.set(Calendar.MONTH, targetMonth)
            cal.set(Calendar.DAY_OF_MONTH, targetDay)
            
            // If the date has already passed this year, set for next year
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.YEAR, 1)
            }
            return cal.timeInMillis
        }

        // 4. Direct time like "7pm" or "7:30pm"
        return parseAbsoluteTime(expression)
    }

    private fun parseAbsoluteTime(timeStr: String): Long? {
        val timeMatcher = TIME_PATTERN.matcher(timeStr)
        if (!timeMatcher.find()) return null

        var hour = timeMatcher.group(1)?.toIntOrNull() ?: return null
        val minute = timeMatcher.group(2)?.toIntOrNull() ?: 0
        val amPm = timeMatcher.group(3)?.lowercase(Locale.ROOT) ?: return null

        if (amPm == "pm" && hour != 12) hour += 12
        if (amPm == "am" && hour == 12) hour = 0

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If no date was specified and the time has already passed today, set for tomorrow
        // This only applies if we are not currently parsing a specific date (handled in parseTimeExpression)
        if (cal.timeInMillis <= System.currentTimeMillis() && 
            !DATE_MMM_PATTERN.matcher(timeStr).find() && 
            !MMM_DATE_PATTERN.matcher(timeStr).find() &&
            !NUMERIC_DATE_PATTERN.matcher(timeStr).find()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return cal.timeInMillis
    }

    /**
     * Quick check if text contains a reminder command.
     */
    fun containsReminder(input: String): Boolean {
        return REMINDER_PATTERN.matcher(input).find()
    }
}
