package com.memoly.dock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.memoly.dock.data.local.MemolyDatabase
import com.memoly.dock.data.model.MemoryItem
import com.memoly.dock.domain.model.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles PROCESS_TEXT intent.
 *
 * When users select text anywhere on Android and choose "Save to Memoly"
 * from the popup menu, this activity receives the selected text,
 * saves it immediately, and closes.
 *
 * Supported on Android 6.0+ (API 23+).
 */
class ProcessTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_PROCESS_TEXT) {
            val selectedText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            } else {
                null
            }

            if (!selectedText.isNullOrBlank()) {
                saveText(selectedText)
            } else {
                finish()
            }
        } else {
            finish()
        }
    }

    private fun saveText(text: String) {
        val db = MemolyDatabase.getDatabase(this)
        CoroutineScope(Dispatchers.IO).launch {
            db.memoryItemDao().insert(
                MemoryItem(
                    content = text,
                    contentType = ContentType.TEXT,
                    sourceApp = "Text Selection"
                )
            )

            runOnUiThread {
                Toast.makeText(
                    this@ProcessTextActivity,
                    "Saved to Memoly ✓",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
}
