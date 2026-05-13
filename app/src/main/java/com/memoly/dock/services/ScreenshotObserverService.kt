package com.memoly.dock.services

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.memoly.dock.data.local.MemolyDatabase
import com.memoly.dock.data.model.MemoryItem
import com.memoly.dock.domain.model.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Efficient screenshot observer using MediaStore ContentObserver.
 *
 * Fix: Debounces by actual image ID (not observer URI) and checks the
 * database before inserting to prevent duplicates. ContentObserver.onChange()
 * can fire 30-50+ times per screenshot on some devices (Vivo, Samsung).
 */
class ScreenshotObserverService(
    private val context: Context,
    private val onScreenshotDetected: ((String) -> Unit)? = null
) {

    private var contentObserver: ContentObserver? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()

    /** Track the last saved image ID to prevent duplicates */
    private var lastSavedImageId: Long = -1
    private var lastSavedTime: Long = 0

    companion object {
        private val SCREENSHOT_PATTERNS = listOf(
            "screenshot",
            "screen_shot",
            "screen_capture",
            "screencap",
            "screen shot"
        )
        // No time-based debounce needed anymore; we rely on DB checks and image ID
    }

    fun startObserving() {
        if (contentObserver != null) return

        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                syncScreenshots()
            }
        }

        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver!!
        )
        
        // Initial sync when observing starts
        syncScreenshots()
    }

    fun stopObserving() {
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
        }
        contentObserver = null
    }

    /**
     * Called when the app comes to the foreground to catch any screenshots
     * taken while the app was backgrounded.
     */
    fun syncMissedScreenshots() {
        syncScreenshots()
    }

    /**
     * Checks the most recently added images in MediaStore.
     * Looks at the last 5 images to handle bursts or background missed screenshots.
     */
    private fun syncScreenshots() {
        scope.launch {
            // Only one coroutine processes at a time to prevent race conditions with DB
            if (!mutex.tryLock()) return@launch

            try {
                val cursor = context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.DATE_ADDED
                    ),
                    null, null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )

                cursor?.use {
                    var count = 0
                    // Check up to the 5 most recent images
                    while (it.moveToNext() && count < 5) {
                        count++
                        
                        val idIndex = it.getColumnIndex(MediaStore.Images.Media._ID)
                        val nameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                        
                        if (idIndex >= 0 && nameIndex >= 0) {
                            val imageId = it.getLong(idIndex)
                            val fileName = it.getString(nameIndex)?.lowercase() ?: continue

                            // Check if it matches screenshot naming patterns
                            if (SCREENSHOT_PATTERNS.any { pattern -> fileName.contains(pattern) }) {
                                val contentUri = Uri.withAppendedPath(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    imageId.toString()
                                ).toString()

                                // Check if this URI already exists in the database
                                val db = MemolyDatabase.getDatabase(context)
                                val existing = db.memoryItemDao().getItemByImagePath(contentUri)
                                if (existing != null) continue // Already saved

                                // Save to database
                                db.memoryItemDao().insert(
                                    MemoryItem(
                                        content = "Screenshot captured",
                                        contentType = ContentType.SCREENSHOT,
                                        imagePath = contentUri,
                                        sourceApp = "System Screenshot"
                                    )
                                )

                                onScreenshotDetected?.invoke(contentUri)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                mutex.unlock()
            }
        }
    }

    fun isObserving(): Boolean = contentObserver != null
}
