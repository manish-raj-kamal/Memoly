package com.memoly.dock.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "memoly_settings")

/**
 * App-wide preferences using DataStore.
 * Every automated feature has a toggle — no hidden behavior.
 */
class AppPreferences(private val context: Context) {

    companion object {
        // Feature toggles
        val KEY_SCREENSHOT_DETECTION = booleanPreferencesKey("screenshot_detection")
        val KEY_CLIPBOARD_MONITORING = booleanPreferencesKey("clipboard_monitoring")
        val KEY_REMINDER_PARSING = booleanPreferencesKey("reminder_parsing")
        val KEY_LINK_METADATA = booleanPreferencesKey("link_metadata")
        val KEY_AUTO_TAGGING = booleanPreferencesKey("auto_tagging")
        val KEY_NOTIFICATION_REMINDERS = booleanPreferencesKey("notification_reminders")
        val KEY_BACKGROUND_OBSERVERS = booleanPreferencesKey("background_observers")
        val KEY_AUTO_STARTUP = booleanPreferencesKey("auto_startup")

        // Sync state
        val KEY_LAST_SCREENSHOT_ID = longPreferencesKey("last_screenshot_id")

        // Quick Capture toggles
        val KEY_QUICK_TILE_ENABLED = booleanPreferencesKey("quick_tile_enabled")
        val KEY_GESTURE_TRIGGER = booleanPreferencesKey("gesture_trigger")
        val KEY_COMMAND_BAR = booleanPreferencesKey("command_bar")

        // App state
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val KEY_DARK_MODE = stringPreferencesKey("dark_mode") // "system", "dark", "light"
    }

    // --- Feature toggle getters (all default to safe/disabled) ---

    val screenshotDetection: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_SCREENSHOT_DETECTION] ?: false }

    val clipboardMonitoring: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_CLIPBOARD_MONITORING] ?: false }

    val reminderParsing: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_REMINDER_PARSING] ?: true }

    val linkMetadata: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_LINK_METADATA] ?: false }

    val autoTagging: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_AUTO_TAGGING] ?: false }

    val notificationReminders: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_NOTIFICATION_REMINDERS] ?: true }

    val backgroundObservers: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_BACKGROUND_OBSERVERS] ?: false }

    val autoStartup: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_AUTO_STARTUP] ?: false }

    val lastScreenshotId: Flow<Long> = context.dataStore.data
        .map { it[KEY_LAST_SCREENSHOT_ID] ?: -1L }

    // Quick Capture getters
    val quickTileEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_QUICK_TILE_ENABLED] ?: true }

    val gestureTrigger: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_GESTURE_TRIGGER] ?: false }

    val commandBar: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_COMMAND_BAR] ?: true }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_ONBOARDING_COMPLETE] ?: false }

    val darkMode: Flow<String> = context.dataStore.data
        .map { it[KEY_DARK_MODE] ?: "system" }

    // --- Setters ---

    suspend fun setScreenshotDetection(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SCREENSHOT_DETECTION] = enabled }
    }

    suspend fun setClipboardMonitoring(enabled: Boolean) {
        context.dataStore.edit { it[KEY_CLIPBOARD_MONITORING] = enabled }
    }

    suspend fun setReminderParsing(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REMINDER_PARSING] = enabled }
    }

    suspend fun setLinkMetadata(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LINK_METADATA] = enabled }
    }

    suspend fun setAutoTagging(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_TAGGING] = enabled }
    }

    suspend fun setNotificationReminders(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATION_REMINDERS] = enabled }
    }

    suspend fun setBackgroundObservers(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BACKGROUND_OBSERVERS] = enabled }
    }

    suspend fun setAutoStartup(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_STARTUP] = enabled }
    }

    suspend fun setLastScreenshotId(id: Long) {
        context.dataStore.edit { it[KEY_LAST_SCREENSHOT_ID] = id }
    }

    suspend fun setQuickTileEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_QUICK_TILE_ENABLED] = enabled }
    }

    suspend fun setGestureTrigger(enabled: Boolean) {
        context.dataStore.edit { it[KEY_GESTURE_TRIGGER] = enabled }
    }

    suspend fun setCommandBar(enabled: Boolean) {
        context.dataStore.edit { it[KEY_COMMAND_BAR] = enabled }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { it[KEY_DARK_MODE] = mode }
    }
}
