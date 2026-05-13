package com.memoly.dock.utils

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.DateUtils
import android.util.Patterns
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility extension functions used across the app.
 */

/** Format epoch millis to a human-readable relative time string */
fun Long.toRelativeTimeString(): String {
    return DateUtils.getRelativeTimeSpanString(
        this,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}

/** Format epoch millis to a readable date string */
fun Long.toDateString(): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(this))
}

/** Format epoch millis to a readable time string (e.g., 09:41) */
fun Long.toTimeString(): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(this))
}

/** Format epoch millis to HH:mm (24h) for timeline display */
fun Long.toTimelineTimeString(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(this))
}

/** Format epoch millis to a full date-time string */
fun Long.toDateTimeString(): String {
    val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    return sdf.format(Date(this))
}

/** Format epoch millis to "Reminder • h:mm a" */
fun Long.toReminderDisplayString(): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return "Reminder • ${sdf.format(Date(this))}"
}

/** Format epoch millis to a date group header (e.g., "Today", "Yesterday", "May 10") */
fun Long.toGroupDateString(): String {
    val cal = Calendar.getInstance()
    val today = Calendar.getInstance()

    cal.timeInMillis = this

    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"

        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 -> "Yesterday"

        else -> {
            val sdf = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
            sdf.format(Date(this))
        }
    }
}

/**
 * Check if a string contains or is a URL.
 * Uses Android's Patterns.WEB_URL for comprehensive matching
 * (handles .com, .org, .io, etc. even without http://).
 */
fun String.isUrl(): Boolean {
    val trimmed = this.trim()
    if (trimmed.contains(" ") && !trimmed.contains("http")) return false
    return Patterns.WEB_URL.matcher(trimmed).matches() ||
           trimmed.startsWith("http://") ||
           trimmed.startsWith("https://")
}

/**
 * Check if a string contains a URL anywhere in it.
 */
fun String.containsUrl(): Boolean {
    return Patterns.WEB_URL.matcher(this).find()
}

/** Get app name from package name */
fun Context.getAppNameFromPackage(packageName: String): String? {
    return try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

/** Truncate string with ellipsis */
fun String.truncate(maxLength: Int = 150): String {
    return if (this.length > maxLength) {
        this.substring(0, maxLength) + "…"
    } else {
        this
    }
}

/** Extract domain from URL */
fun String.extractDomain(): String? {
    return try {
        val text = if (!this.startsWith("http")) "https://$this" else this
        val uri = Uri.parse(text)
        uri.host?.removePrefix("www.")
    } catch (e: Exception) {
        null
    }
}
