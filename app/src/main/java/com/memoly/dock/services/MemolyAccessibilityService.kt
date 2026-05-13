package com.memoly.dock.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.memoly.dock.ui.quickcapture.QuickCaptureActivity

/**
 * Accessibility service for gesture-triggered quick capture.
 *
 * Privacy:
 * - ONLY listens for the system accessibility shortcut
 * - Does NOT read screen content, window changes, or any user data
 * - Does NOT monitor any app activity
 * - Can be fully disabled from Settings → Quick Capture → Gesture Trigger
 *
 * How it works:
 * - User enables "Memoly Quick Capture" in system Accessibility settings
 * - On Android 12+, user assigns the Accessibility Shortcut gesture
 * - When triggered, the service launches QuickCaptureActivity
 *
 * The accessibility shortcut can be triggered by:
 * - Two-finger triple-tap
 * - Volume key shortcut (hold both volume keys)
 * - Navigation button long-press
 */
class MemolyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Launch quick capture when the accessibility shortcut is activated
        // (this is the onServiceConnected trigger for shortcut-only services)
        launchQuickCapture()
    }

    /**
     * We don't process any accessibility events — privacy first.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty — we do NOT read screen content
    }

    override fun onInterrupt() {
        // Nothing to clean up
    }

    private fun launchQuickCapture() {
        val intent = Intent(this, QuickCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }
}
