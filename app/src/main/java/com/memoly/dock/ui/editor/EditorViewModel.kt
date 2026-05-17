package com.memoly.dock.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memoly.dock.data.local.MemolyDatabase
import com.memoly.dock.data.model.MemoryItem
import com.memoly.dock.data.repository.MemoryRepository
import com.memoly.dock.domain.model.ContentType
import com.memoly.dock.domain.usecase.ReminderParser
import com.memoly.dock.utils.containsUrl
import com.memoly.dock.utils.isUrl
import com.memoly.dock.workers.ReminderWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the note editor screen.
 * Handles creating and editing memory items with reminder parsing
 * and image attachment support.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MemoryRepository
    private val app = application

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _tags = MutableStateFlow("")
    val tags: StateFlow<String> = _tags.asStateFlow()

    private val _isPinned = MutableStateFlow(false)
    val isPinned: StateFlow<Boolean> = _isPinned.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveComplete = MutableStateFlow(false)
    val saveComplete: StateFlow<Boolean> = _saveComplete.asStateFlow()

    private val _editingItemId = MutableStateFlow<Long?>(null)
    val editingItemId: StateFlow<Long?> = _editingItemId.asStateFlow()

    private val _contentType = MutableStateFlow(ContentType.NOTE)
    val contentType: StateFlow<ContentType> = _contentType.asStateFlow()

    private val _attachedImageUri = MutableStateFlow<String?>(null)
    val attachedImageUri: StateFlow<String?> = _attachedImageUri.asStateFlow()

    init {
        val db = MemolyDatabase.getDatabase(application)
        repository = MemoryRepository(db.memoryItemDao())
    }

    fun updateContent(text: String) {
        _content.value = text
        // Auto-detect content type
        _contentType.value = when {
            _attachedImageUri.value != null -> ContentType.IMAGE
            text.isUrl() || text.containsUrl() -> ContentType.LINK
            else -> ContentType.NOTE
        }
    }

    fun updateTitle(text: String) {
        _title.value = text
    }

    fun updateTags(text: String) {
        _tags.value = text
    }

    fun togglePin() {
        _isPinned.value = !_isPinned.value
    }

    fun attachImage(uri: String) {
        _attachedImageUri.value = uri
        _contentType.value = ContentType.IMAGE
    }

    fun removeImage() {
        _attachedImageUri.value = null
        // Re-detect content type
        _contentType.value = when {
            _content.value.isUrl() || _content.value.containsUrl() -> ContentType.LINK
            else -> ContentType.NOTE
        }
    }

    /**
     * Load an existing item for editing.
     */
    fun loadItem(itemId: Long) {
        viewModelScope.launch {
            val item = repository.getItemByIdOnce(itemId)
            if (item != null) {
                _editingItemId.value = item.id
                _content.value = item.content
                _title.value = item.title ?: ""
                _tags.value = item.tags ?: ""
                _isPinned.value = item.isPinned
                _contentType.value = item.contentType
                _attachedImageUri.value = item.imagePath
            }
        }
    }

    /**
     * Save the current note (create or update).
     * Handles reminder parsing with ?rem command and image attachments.
     */
    fun save() {
        val currentContent = _content.value.trim()
        if (currentContent.isBlank() && _attachedImageUri.value == null) return

        _isSaving.value = true

        viewModelScope.launch {
            try {
                // Parse for reminder commands
                val parseResult = ReminderParser.parse(currentContent)
                val finalContent = parseResult.cleanedText.ifBlank { "Image" }
                val reminderTime = parseResult.reminderTimeMillis

                val contentType = when {
                    _attachedImageUri.value != null -> ContentType.IMAGE
                    finalContent.isUrl() || finalContent.containsUrl() -> ContentType.LINK
                    _contentType.value == ContentType.SCREENSHOT -> ContentType.SCREENSHOT
                    else -> ContentType.NOTE
                }

                val editId = _editingItemId.value

                if (editId != null) {
                    // Update existing item
                    val existing = repository.getItemByIdOnce(editId)
                    if (existing != null) {
                        repository.update(
                            existing.copy(
                                content = finalContent,
                                title = _title.value.takeIf { it.isNotBlank() },
                                contentType = contentType,
                                tags = _tags.value.takeIf { it.isNotBlank() },
                                isPinned = _isPinned.value,
                                reminderTime = reminderTime ?: existing.reminderTime,
                                isReminderDone = if (reminderTime != null) false else existing.isReminderDone,
                                imagePath = _attachedImageUri.value
                            )
                        )

                        // Schedule reminder if present
                        if (reminderTime != null) {
                            ReminderWorker.schedule(
                                context = app,
                                memoryId = editId,
                                content = finalContent,
                                triggerAtMillis = reminderTime
                            )
                        }
                    }
                } else {
                    // Create new item
                    val newId = repository.insert(
                        MemoryItem(
                            content = finalContent,
                            title = _title.value.takeIf { it.isNotBlank() },
                            contentType = contentType,
                            tags = _tags.value.takeIf { it.isNotBlank() },
                            isPinned = _isPinned.value,
                            reminderTime = reminderTime,
                            imagePath = _attachedImageUri.value
                        )
                    )

                    // Schedule reminder if present
                    if (reminderTime != null) {
                        ReminderWorker.schedule(
                            context = app,
                            memoryId = newId,
                            content = finalContent,
                            triggerAtMillis = reminderTime
                        )
                    }
                }

                _saveComplete.value = true
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSaving.value = false
            }
        }
    }
}
