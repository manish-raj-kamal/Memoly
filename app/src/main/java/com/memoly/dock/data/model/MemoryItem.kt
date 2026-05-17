package com.memoly.dock.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.memoly.dock.domain.model.ContentType

/**
 * Room entity representing a single memory item in the timeline.
 * All user data is stored locally on-device.
 */
@Entity(tableName = "memory_items")
data class MemoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** The main content text or URL */
    val content: String,

    /** Type of content: TEXT, LINK, SCREENSHOT, IMAGE, NOTE */
    val contentType: ContentType = ContentType.TEXT,

    /** When this memory was captured (epoch millis) */
    val timestamp: Long = System.currentTimeMillis(),

    /** Name of the source app (if shared from another app) */
    val sourceApp: String? = null,

    /** Comma-separated tags for search and organization */
    val tags: String? = null,

    /** The title of the memory */
    val title: String? = null,

    /** Scheduled reminder time (epoch millis), null if no reminder */
    val reminderTime: Long? = null,

    /** Whether the reminder has been marked as done */
    val isReminderDone: Boolean = false,

    /** File path for images/screenshots stored locally */
    val imagePath: String? = null,

    /** Extracted text from images (OCR) */
    val extractedText: String? = null,

    /** Whether this item is pinned to the top */
    val isPinned: Boolean = false,

    /** Whether this item is starred/favorited */
    val isFavorite: Boolean = false,

    /** When this item was last modified */
    val lastModifiedAt: Long = System.currentTimeMillis(),

    /** When this entity was created in the database */
    val createdAt: Long = System.currentTimeMillis()
)
