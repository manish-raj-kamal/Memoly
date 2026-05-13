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
        // Check for "in X hours/minutes" pattern
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

        // Check for "tomorrow Xam/pm" pattern
        val tomorrowMatcher = TOMORROW_PATTERN.matcher(expression)
        if (tomorrowMatcher.find()) {
            val timeStr = tomorrowMatcher.group(1) ?: return null
            val time = parseAbsoluteTime(timeStr) ?: return null
            val cal = Calendar.getInstance()
            cal.timeInMillis = time
            cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }

        // Direct time like "7pm" or "7:30pm"
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

        // If the time has already passed today, set for tomorrow
        if (cal.timeInMillis <= System.currentTimeMillis()) {
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
