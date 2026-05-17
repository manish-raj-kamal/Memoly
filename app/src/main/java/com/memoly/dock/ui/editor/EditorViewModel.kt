package com.memoly.dock.ui.editor

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
 * Handles creating and editing memory items with reminder parsing,
 * inline image support, and smart checklist mode.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MemoryRepository
    private val app = application

    private val _contentValue = MutableStateFlow(TextFieldValue(""))
    val contentValue: StateFlow<TextFieldValue> = _contentValue.asStateFlow()

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

    private val _isListMode = MutableStateFlow(false)
    val isListMode: StateFlow<Boolean> = _isListMode.asStateFlow()

    init {
        val db = MemolyDatabase.getDatabase(application)
        repository = MemoryRepository(db.memoryItemDao())
    }

    fun updateContentValue(value: TextFieldValue) {
        val oldText = _contentValue.value.text
        val newText = value.text

        var finalValue = value

        // Handle list mode auto-continuation on Enter
        if (_isListMode.value && newText.length > oldText.length && newText.endsWith("\n")) {
            val lines = newText.split("\n")
            // Check the line that was just finished (second to last)
            val lastLine = lines.getOrNull(lines.size - 2) ?: ""
            if (lastLine.trim().startsWith("☐") || lastLine.trim().startsWith("☑")) {
                val prefix = "☐ "
                val updatedText = newText + prefix
                finalValue = value.copy(
                    text = updatedText,
                    selection = TextRange(updatedText.length)
                )
            }
        }

        _contentValue.value = finalValue
        
        // Auto-detect content type
        _contentType.value = when {
            _attachedImageUri.value != null -> ContentType.IMAGE
            finalValue.text.isUrl() || finalValue.text.containsUrl() -> ContentType.LINK
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
        // For inline media, we'll append a marker to the text
        // Format: [img:uri]
        val marker = "\n[img:$uri]\n"
        val currentText = _contentValue.value.text
        val newText = if (currentText.isBlank()) marker else "$currentText$marker"
        
        _contentValue.value = TextFieldValue(
            text = newText,
            selection = TextRange(newText.length)
        )
        _contentType.value = ContentType.IMAGE
    }
    
    fun attachFile(uri: String, fileName: String) {
        // Format: [file:uri|name]
        val marker = "\n[file:$uri|$fileName]\n"
        val currentText = _contentValue.value.text
        val newText = if (currentText.isBlank()) marker else "$currentText$marker"
        
        _contentValue.value = TextFieldValue(
            text = newText,
            selection = TextRange(newText.length)
        )
        _contentType.value = ContentType.FILE
    }

    fun removeImage() {
        _attachedImageUri.value = null
        // Re-detect content type
        _contentType.value = when {
            _contentValue.value.text.isUrl() || _contentValue.value.text.containsUrl() -> ContentType.LINK
            else -> ContentType.NOTE
        }
    }

    fun toggleListMode() {
        val currentText = _contentValue.value.text
        val selection = _contentValue.value.selection
        
        // If we are at the end of a line or starting a new one
        val lines = currentText.split("\n").toMutableList()
        
        // Find which line the cursor is currently on
        var charCount = 0
        var targetLineIndex = 0
        for (i in lines.indices) {
            charCount += lines[i].length + 1 // +1 for the newline
            if (selection.start < charCount) {
                targetLineIndex = i
                break
            }
        }
        
        val targetLine = lines[targetLineIndex]

        if (!_isListMode.value) {
            // Turning ON: Add checkbox to target line
            if (!targetLine.trim().startsWith("☐") && !targetLine.trim().startsWith("☑")) {
                lines[targetLineIndex] = "☐ $targetLine"
                val updatedText = lines.joinToString("\n")
                _contentValue.value = TextFieldValue(
                    text = updatedText,
                    selection = TextRange(selection.start + 2) // Move cursor after checkbox
                )
            }
            _isListMode.value = true
        } else {
            // Turning OFF: Remove checkbox from target line
            if (targetLine.trim().startsWith("☐") || targetLine.trim().startsWith("☑")) {
                val cleanedLine = targetLine.replaceFirst("☐ ", "").replaceFirst("☑ ", "")
                lines[targetLineIndex] = cleanedLine
                val updatedText = lines.joinToString("\n")
                _contentValue.value = TextFieldValue(
                    text = updatedText,
                    selection = TextRange(maxOf(0, selection.start - 2))
                )
            }
            _isListMode.value = false
        }
    }
    
    /**
     * Toggles a checkbox state at a specific character offset.
     * Called when user clicks a checkbox in the text.
     */
    fun toggleCheckboxAt(offset: Int) {
        val currentText = _contentValue.value.text
        if (offset < 0 || offset >= currentText.length) return
        
        val char = currentText[offset]
        val newText = when (char) {
            '☐' -> currentText.substring(0, offset) + '☑' + currentText.substring(offset + 1)
            '☑' -> currentText.substring(0, offset) + '☐' + currentText.substring(offset + 1)
            else -> currentText
        }
        
        if (newText != currentText) {
            _contentValue.value = _contentValue.value.copy(text = newText)
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
                _contentValue.value = TextFieldValue(
                    text = item.content,
                    selection = TextRange(item.content.length)
                )
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
     */
    fun save() {
        val currentContent = _contentValue.value.text.trim()
        if (currentContent.isBlank()) return

        _isSaving.value = true

        viewModelScope.launch {
            try {
                // Parse for reminder commands
                val parseResult = ReminderParser.parse(currentContent)
                val finalContent = parseResult.cleanedText
                val reminderTime = parseResult.reminderTimeMillis

                // Type detection
                val contentType = when {
                    finalContent.contains("[img:") -> ContentType.IMAGE
                    finalContent.contains("[file:") -> ContentType.FILE
                    finalContent.isUrl() || finalContent.containsUrl() -> ContentType.LINK
                    else -> ContentType.NOTE
                }

                val editId = _editingItemId.value

                if (editId != null) {
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
                                lastModifiedAt = System.currentTimeMillis()
                            )
                        )

                        if (reminderTime != null) {
                            ReminderWorker.schedule(app, editId, finalContent, reminderTime)
                        }
                    }
                } else {
                    val newId = repository.insert(
                        MemoryItem(
                            content = finalContent,
                            title = _title.value.takeIf { it.isNotBlank() },
                            contentType = contentType,
                            tags = _tags.value.takeIf { it.isNotBlank() },
                            isPinned = _isPinned.value,
                            reminderTime = reminderTime
                        )
                    )

                    if (reminderTime != null) {
                        ReminderWorker.schedule(app, newId, finalContent, reminderTime)
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
