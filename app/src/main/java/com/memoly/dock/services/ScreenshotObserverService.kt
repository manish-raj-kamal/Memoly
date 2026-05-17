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
import com.memoly.dock.settings.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Efficient screenshot observer using MediaStore ContentObserver.
 *
 * Fix: Uses a "Sync Anchor" (lastScreenshotId) to prevent deleted
 * screenshots from reappearing. Uses the actual MediaStore timestamp
 * instead of the current system time for correct ordering.
 */
class ScreenshotObserverService(
    private val context: Context,
    private val onScreenshotDetected: ((String) -> Unit)? = null
) {

    private var contentObserver: ContentObserver? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()
    private val prefs = AppPreferences(context)

    companion object {
        private val SCREENSHOT_PATTERNS = listOf(
            "screenshot",
            "screen_shot",
            "screen_capture",
            "screencap",
            "screen shot"
        )
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
     * Only processes images with an ID greater than the last synced ID.
     */
    private fun syncScreenshots() {
        scope.launch {
            // Only one coroutine processes at a time
            if (!mutex.tryLock()) return@launch

            try {
                val lastId = prefs.lastScreenshotId.first()
                
                val cursor = context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.DATE_ADDED
                    ),
                    "${MediaStore.Images.Media._ID} > ?",
                    arrayOf(lastId.toString()),
                    "${MediaStore.Images.Media._ID} ASC"
                )

                cursor?.use {
                    var newLastId = lastId
                    val idIndex = it.getColumnIndex(MediaStore.Images.Media._ID)
                    val nameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateIndex = it.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)

                    while (it.moveToNext()) {
                        if (idIndex < 0 || nameIndex < 0 || dateIndex < 0) continue
                        
                        val imageId = it.getLong(idIndex)
                        val fileName = it.getString(nameIndex)?.lowercase() ?: continue
                        val dateAddedSec = it.getLong(dateIndex)
                        val timestamp = dateAddedSec * 1000 // Convert to millis

                        newLastId = maxOf(newLastId, imageId)

                        // Check if it matches screenshot naming patterns
                        if (SCREENSHOT_PATTERNS.any { pattern -> fileName.contains(pattern) }) {
                            val contentUri = Uri.withAppendedPath(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                imageId.toString()
                            ).toString()

                            // Double check if this URI already exists in the database just in case
                            val db = MemolyDatabase.getDatabase(context)
                            val existing = db.memoryItemDao().getItemByImagePath(contentUri)
                            if (existing != null) continue 

                            // Save to database with the CORRECT media timestamp
                            db.memoryItemDao().insert(
                                MemoryItem(
                                    content = "Screenshot captured",
                                    contentType = ContentType.SCREENSHOT,
                                    imagePath = contentUri,
                                    sourceApp = "System Screenshot",
                                    timestamp = timestamp
                                )
                            )

                            onScreenshotDetected?.invoke(contentUri)
                        }
                    }
                    
                    if (newLastId > lastId) {
                        prefs.setLastScreenshotId(newLastId)
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
