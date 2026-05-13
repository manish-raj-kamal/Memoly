package com.memoly.dock.ui.quickcapture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.memoly.dock.ui.theme.MemolyTheme

/**
 * Transparent overlay activity for quick memory capture.
 *
 * Launched from:
 * - Quick Settings tile
 * - Accessibility gesture (optional)
 *
 * WindowCompat.setDecorFitsSystemWindows = false so that
 * imePadding() works correctly and the command bar sits above the keyboard.
 */
class QuickCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Allow content to draw behind system bars / keyboard
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MemolyTheme {
                QuickCaptureScreen(
                    onSaved = { finish() },
                    onDismiss = { finish() }
                )
            }
        }
    }
}
