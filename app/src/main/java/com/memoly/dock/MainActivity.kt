package com.memoly.dock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.memoly.dock.settings.AppPreferences
import com.memoly.dock.services.ScreenshotObserverService
import com.memoly.dock.ui.navigation.MemolyNavigation
import com.memoly.dock.ui.navigation.MemolyRoutes
import com.memoly.dock.ui.theme.MemolyTheme
import com.memoly.dock.workers.ReminderWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Main entry point for Memoly.
 * Handles onboarding state, runtime permissions, deep linking from notifications,
 * and lifecycle management for the screenshot observer.
 */
class MainActivity : ComponentActivity() {

    private var screenshotObserver: ScreenshotObserverService? = null
    private lateinit var prefs: AppPreferences

    /** Mutable state so notification deep links work even when activity is already running */
    private val deepLinkId = MutableStateFlow<Long?>(null)

    // Runtime permission launchers
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Granted or denied — notification code already checks permission before posting */ }

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            lifecycleScope.launch {
                val screenshotEnabled = prefs.screenshotDetection.first()
                if (screenshotEnabled) {
                    startScreenshotObserver()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefs = AppPreferences(this)

        // Create notification channel
        ReminderWorker.createNotificationChannel(this)

        // Request notification permission (Android 13+)
        requestNotificationPermission()

        // Handle deep link from notification tap
        handleDeepLink(intent)

        lifecycleScope.launch {
            val onboardingComplete = prefs.onboardingComplete.first()
            val startDestination = if (onboardingComplete) {
                MemolyRoutes.TIMELINE
            } else {
                MemolyRoutes.ONBOARDING
            }

            // Start screenshot observer if enabled + permitted
            val screenshotEnabled = prefs.screenshotDetection.first()
            if (screenshotEnabled) {
                requestMediaPermissionAndStartObserver()
            }

            // Observe screenshot setting changes
            launch {
                prefs.screenshotDetection.collect { enabled ->
                    if (enabled) {
                        requestMediaPermissionAndStartObserver()
                    } else {
                        stopScreenshotObserver()
                    }
                }
            }

            setContent {
                val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
                val pendingDeepLink by deepLinkId.collectAsState()
                MemolyTheme {
                    MemolyNavigation(
                        windowSizeClass = windowSizeClass,
                        startDestination = startDestination,
                        onOnboardingComplete = {
                            lifecycleScope.launch {
                                prefs.setOnboardingComplete(true)
                            }
                        },
                        deepLinkMemoryId = pendingDeepLink,
                        onDeepLinkConsumed = { deepLinkId.value = null }
                    )
                }
            }
        }
    }

    /**
     * Called when the activity is already running and a new intent arrives
     * (e.g. tapping a reminder notification while the app is open).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val memoryId = intent?.getLongExtra("memory_id", -1L)?.takeIf { it != -1L }
        if (memoryId != null) {
            deepLinkId.value = memoryId
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestMediaPermissionAndStartObserver() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            startScreenshotObserver()
        } else {
            mediaPermissionLauncher.launch(permission)
        }
    }

    private fun startScreenshotObserver() {
        if (screenshotObserver == null) {
            screenshotObserver = ScreenshotObserverService(this)
        }
        screenshotObserver?.startObserving()
    }

    private fun stopScreenshotObserver() {
        screenshotObserver?.stopObserving()
    }

    override fun onResume() {
        super.onResume()
        // Sync any screenshots taken while the app was backgrounded
        screenshotObserver?.syncMissedScreenshots()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenshotObserver()
    }
}