package com.memoly.dock.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memoly.dock.settings.AppPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen.
 * Reads and writes user preferences via DataStore.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)

    val screenshotDetection: StateFlow<Boolean> = prefs.screenshotDetection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val clipboardMonitoring: StateFlow<Boolean> = prefs.clipboardMonitoring
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val reminderParsing: StateFlow<Boolean> = prefs.reminderParsing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val linkMetadata: StateFlow<Boolean> = prefs.linkMetadata
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoTagging: StateFlow<Boolean> = prefs.autoTagging
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val notificationReminders: StateFlow<Boolean> = prefs.notificationReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val backgroundObservers: StateFlow<Boolean> = prefs.backgroundObservers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoStartup: StateFlow<Boolean> = prefs.autoStartup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Quick Capture settings
    val quickTileEnabled: StateFlow<Boolean> = prefs.quickTileEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val gestureTrigger: StateFlow<Boolean> = prefs.gestureTrigger
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val commandBar: StateFlow<Boolean> = prefs.commandBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setScreenshotDetection(enabled: Boolean) {
        viewModelScope.launch { prefs.setScreenshotDetection(enabled) }
    }

    fun setClipboardMonitoring(enabled: Boolean) {
        viewModelScope.launch { prefs.setClipboardMonitoring(enabled) }
    }

    fun setReminderParsing(enabled: Boolean) {
        viewModelScope.launch { prefs.setReminderParsing(enabled) }
    }

    fun setLinkMetadata(enabled: Boolean) {
        viewModelScope.launch { prefs.setLinkMetadata(enabled) }
    }

    fun setAutoTagging(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoTagging(enabled) }
    }

    fun setNotificationReminders(enabled: Boolean) {
        viewModelScope.launch { prefs.setNotificationReminders(enabled) }
    }

    fun setBackgroundObservers(enabled: Boolean) {
        viewModelScope.launch { prefs.setBackgroundObservers(enabled) }
    }

    fun setAutoStartup(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoStartup(enabled) }
    }

    fun setQuickTileEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setQuickTileEnabled(enabled) }
    }

    fun setGestureTrigger(enabled: Boolean) {
        viewModelScope.launch { prefs.setGestureTrigger(enabled) }
    }

    fun setCommandBar(enabled: Boolean) {
        viewModelScope.launch { prefs.setCommandBar(enabled) }
    }
}
