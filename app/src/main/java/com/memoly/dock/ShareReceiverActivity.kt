package com.memoly.dock

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.memoly.dock.data.local.MemolyDatabase
import com.memoly.dock.data.model.MemoryItem
import com.memoly.dock.domain.model.ContentType
import com.memoly.dock.utils.isUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives shared content from other apps (ACTION_SEND / ACTION_SEND_MULTIPLE).
 *
 * Handles:
 * - Text content (notes, URLs)
 * - Image URIs
 * - Multiple images
 *
 * Flow: receive → save silently → show toast → close
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            Intent.ACTION_SEND -> handleSingleShare(intent)
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleShare(intent)
        }
    }

    private fun handleSingleShare(intent: Intent) {
        val type = intent.type ?: return finish()
        val sourceApp = intent.getStringExtra(Intent.EXTRA_REFERRER)
            ?: callingPackage
            ?: "Unknown App"

        when {
            type.startsWith("text/") -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return finish()
                saveMemory(
                    content = text,
                    contentType = if (text.isUrl()) ContentType.LINK else ContentType.TEXT,
                    sourceApp = sourceApp
                )
            }
            type.startsWith("image/") -> {
                val imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (imageUri != null) {
                    saveMemory(
                        content = "Shared image",
                        contentType = ContentType.IMAGE,
                        imagePath = imageUri.toString(),
                        sourceApp = sourceApp
                    )
                } else {
                    finish()
                }
            }
            else -> finish()
        }
    }

    private fun handleMultipleShare(intent: Intent) {
        val imageUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        val sourceApp = callingPackage ?: "Unknown App"

        if (imageUris.isNullOrEmpty()) {
            finish()
            return
        }

        val db = MemolyDatabase.getDatabase(this)
        CoroutineScope(Dispatchers.IO).launch {
            imageUris.forEach { uri ->
                db.memoryItemDao().insert(
                    MemoryItem(
                        content = "Shared image",
                        contentType = ContentType.IMAGE,
                        imagePath = uri.toString(),
                        sourceApp = sourceApp
                    )
                )
            }

            runOnUiThread {
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "${imageUris.size} images saved to Memoly",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun saveMemory(
        content: String,
        contentType: ContentType,
        imagePath: String? = null,
        sourceApp: String? = null
    ) {
        val db = MemolyDatabase.getDatabase(this)
        CoroutineScope(Dispatchers.IO).launch {
            db.memoryItemDao().insert(
                MemoryItem(
                    content = content,
                    contentType = contentType,
                    imagePath = imagePath,
                    sourceApp = sourceApp
                )
            )

            runOnUiThread {
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "Saved to Memoly ✓",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
}
