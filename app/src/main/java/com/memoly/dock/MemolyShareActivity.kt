package com.memoly.dock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.memoly.dock.data.local.MemolyDatabase
import com.memoly.dock.data.model.MemoryItem
import com.memoly.dock.domain.model.ContentType
import com.memoly.dock.utils.buildInlineFileMarker
import com.memoly.dock.utils.buildInlineImageMarker
import com.memoly.dock.utils.copyUriToInternalStorage
import com.memoly.dock.utils.getDisplayName
import com.memoly.dock.utils.isUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Receives shared content from other apps (ACTION_SEND / ACTION_SEND_MULTIPLE).
 *
 * Handles:
 * - Text content (notes, URLs)
 * - Image URIs
 * - Documents (PDF, XLS, etc.)
 * - Multiple files
 *
 * Flow: receive → copy file to internal storage → save silently → show toast → close
 */
class MemolyShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentAction = intent?.action
        if (intentAction == Intent.ACTION_SEND) {
            handleSingleShare(intent)
        } else if (intentAction == Intent.ACTION_SEND_MULTIPLE) {
            handleMultipleShare(intent)
        } else {
            finish()
        }
    }

    private fun handleSingleShare(intent: Intent) {
        val type = intent.type ?: return finish()
        val sourceApp = getSourceAppName(intent)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (type.startsWith("text/")) {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (text != null) {
                        saveMemory(
                            content = text,
                            contentType = if (text.isUrl()) ContentType.LINK else ContentType.TEXT,
                            sourceApp = sourceApp
                        )
                    } else {
                        finishOnMain()
                    }
                } else if (type.startsWith("image/")) {
                    val uri = getUriExtra(intent)
                    if (uri != null) {
                        val localPath = copyUriToInternalStorage(this@MemolyShareActivity, uri)
                        if (localPath != null) {
                            saveMemory(
                                content = buildInlineImageMarker(localPath),
                                contentType = ContentType.IMAGE,
                                sourceApp = sourceApp
                            )
                        } else {
                            finishOnMain()
                        }
                    } else {
                        finishOnMain()
                    }
                } else {
                    // Application/Documents
                    val uri = getUriExtra(intent)
                    if (uri != null) {
                        val fileName = getFileName(uri) ?: "Shared Document"
                        val localPath = copyUriToInternalStorage(this@MemolyShareActivity, uri, fileName)
                        if (localPath != null) {
                            saveMemory(
                                content = buildInlineFileMarker(localPath, fileName),
                                contentType = ContentType.FILE,
                                sourceApp = sourceApp
                            )
                        } else {
                            finishOnMain()
                        }
                    } else {
                        finishOnMain()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                finishOnMain()
            }
        }
    }

    private fun handleMultipleShare(intent: Intent) {
        val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        val sourceApp = getSourceAppName(intent)
        val type = intent.type ?: "*/*"

        if (uris.isNullOrEmpty()) {
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = MemolyDatabase.getDatabase(this@MemolyShareActivity)
                var savedCount = 0

                uris.forEach { uri ->
                    val fileName = getFileName(uri) ?: "Shared Document"
                    val localPath = copyUriToInternalStorage(this@MemolyShareActivity, uri, fileName)
                    if (localPath != null) {
                        val isImage = type.startsWith("image/")
                        val contentType = if (isImage) ContentType.IMAGE else ContentType.FILE
                        val content = if (isImage) {
                            buildInlineImageMarker(localPath)
                        } else {
                            buildInlineFileMarker(localPath, fileName)
                        }

                        db.memoryItemDao().insert(
                            MemoryItem(
                                content = content,
                                contentType = contentType,
                                sourceApp = sourceApp
                            )
                        )
                        savedCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    if (savedCount > 0) {
                        Toast.makeText(
                            this@MemolyShareActivity,
                            "$savedCount items saved to Memoly",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                finishOnMain()
            }
        }
    }

    private fun getSourceAppName(intent: Intent): String {
        var packageName: String? = null
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val referrerUri = referrer
            if (referrerUri != null && referrerUri.scheme == "android-app") {
                packageName = referrerUri.host
            }
        }
        
        if (packageName == null) {
            packageName = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)
        }
        
        if (packageName == null) {
            val referrerUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_REFERRER, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_REFERRER)
            }
            if (referrerUri?.scheme == "android-app") {
                packageName = referrerUri.host
            }
        }
        
        if (packageName == null) {
            packageName = callingPackage
        }
        
        if (packageName == null) {
            packageName = callingActivity?.packageName
        }
        
        if (packageName != null) {
            try {
                val pm = packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                return pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                return packageName
            }
        }
        
        return "Unknown App"
    }

    private suspend fun saveMemory(
        content: String,
        contentType: ContentType,
        sourceApp: String? = null
    ) {
        val db = MemolyDatabase.getDatabase(this)
        db.memoryItemDao().insert(
            MemoryItem(
                content = content,
                contentType = contentType,
                sourceApp = sourceApp
            )
        )

        withContext(Dispatchers.Main) {
            Toast.makeText(
                this@MemolyShareActivity,
                "Saved to Memoly ✓",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    private suspend fun finishOnMain() {
        withContext(Dispatchers.Main) {
            finish()
        }
    }

    private fun getUriExtra(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    /**
     * Copies the content of a shared URI to internal app storage.
     * Required because the sending app might revoke URI permissions or delete the temp file
     * as soon as our Activity finishes.
     *
     * @return The absolute path to the copied file in internal storage, or null if failed.
     */
    private fun getFileName(uri: Uri): String? {
        return getDisplayName(this, uri)
    }
}
