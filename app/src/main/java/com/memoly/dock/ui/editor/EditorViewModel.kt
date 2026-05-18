package com.memoly.dock.ui.editor

import android.app.Application
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memoly.dock.data.local.MemolyDatabase
import com.memoly.dock.data.model.MemoryItem
import com.memoly.dock.data.repository.MemoryRepository
import com.memoly.dock.domain.model.ContentType
import com.memoly.dock.domain.usecase.ReminderParser
import com.memoly.dock.utils.buildInlineFileMarker
import com.memoly.dock.utils.buildInlineImageMarker
import com.memoly.dock.utils.containsInlineAttachment
import com.memoly.dock.utils.copyUriToInternalStorage
import com.memoly.dock.utils.getDisplayName
import com.memoly.dock.utils.parseInlineAttachment
import com.memoly.dock.utils.containsUrl
import com.memoly.dock.utils.isUrl
import com.memoly.dock.workers.ReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        removeTouchedInlineAttachment(oldText, newText)?.let { removedValue ->
            finalValue = removedValue
        }

        continueListAfterEnter(finalValue)?.let { continuedValue ->
            finalValue = continuedValue
        }

        _contentValue.value = finalValue
        
        // Auto-detect content type
        _contentType.value = when {
            finalValue.text.contains("[img:") -> ContentType.IMAGE
            finalValue.text.contains("[file:") -> ContentType.FILE
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

    fun attachImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val localPath = copyUriToInternalStorage(app, uri, getDisplayName(app, uri))
            if (localPath != null) {
                withContext(Dispatchers.Main) {
                    insertInlineMarker(
                        marker = buildInlineImageMarker(localPath),
                        type = ContentType.IMAGE,
                        addCursorSpaceAfterMarker = true
                    )
                }
            }
        }
    }

    fun attachFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileName = getDisplayName(app, uri) ?: "Document"
            val localPath = copyUriToInternalStorage(app, uri, fileName)
            if (localPath != null) {
                withContext(Dispatchers.Main) {
                    insertInlineMarker(
                        marker = buildInlineFileMarker(localPath, fileName),
                        type = ContentType.FILE,
                        addCursorSpaceAfterMarker = true
                    )
                }
            }
        }
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
            _contentValue.value = TextFieldValue(
                text = newText,
                selection = _contentValue.value.selection
            )
        }
    }

    /**
     * Load an existing item for editing.
     */
    fun loadItem(itemId: Long) {
        viewModelScope.launch {
            val item = repository.getItemByIdOnce(itemId)
            if (item != null) {
                val inlineContent = migrateLegacyAttachmentToInline(item)
                _editingItemId.value = item.id
                _contentValue.value = TextFieldValue(
                    text = inlineContent,
                    selection = TextRange(inlineContent.length)
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
                                imagePath = if (contentType == ContentType.SCREENSHOT) existing.imagePath else null,
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
                            reminderTime = reminderTime,
                            imagePath = null
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

    private fun insertInlineMarker(
        marker: String,
        type: ContentType,
        addCursorSpaceAfterMarker: Boolean = false
    ) {
        val currentValue = _contentValue.value
        val start = currentValue.selection.min
        val end = currentValue.selection.max
        val prefix = if (start > 0 && currentValue.text[start - 1] != '\n') "\n" else ""
        val suffix = when {
            addCursorSpaceAfterMarker -> "\n "
            end < currentValue.text.length && currentValue.text[end] != '\n' -> "\n"
            else -> ""
        }
        val insertion = buildString {
            append(prefix)
            append(marker)
            append(suffix)
        }

        val updatedText = currentValue.text.replaceRange(start, end, insertion)
        val updatedSelection = start + prefix.length + marker.length + suffix.length
        _contentValue.value = TextFieldValue(
            text = updatedText,
            selection = TextRange(updatedSelection)
        )
        _contentType.value = type
    }

    private fun continueListAfterEnter(value: TextFieldValue): TextFieldValue? {
        if (!_isListMode.value || value.selection.min != value.selection.max) return null

        val cursor = value.selection.start
        val insertedNewlineIndex = cursor - 1
        if (insertedNewlineIndex !in value.text.indices || value.text[insertedNewlineIndex] != '\n') {
            return null
        }

        val previousLineStart = value.text.lastIndexOf('\n', insertedNewlineIndex - 1).let { index ->
            if (index == -1) 0 else index + 1
        }
        val previousLine = value.text.substring(previousLineStart, insertedNewlineIndex)
        val checkboxIndex = previousLine.indexOfFirst { it == '☐' || it == '☑' }
        if (checkboxIndex == -1 || previousLine.take(checkboxIndex).isNotBlank()) {
            return null
        }

        val prefix = "${previousLine.take(checkboxIndex)}☐ "
        val updatedText = value.text.replaceRange(cursor, cursor, prefix)
        val updatedCursor = cursor + prefix.length
        return TextFieldValue(
            text = updatedText,
            selection = TextRange(updatedCursor)
        )
    }

    private fun removeTouchedInlineAttachment(oldText: String, newText: String): TextFieldValue? {
        if (!containsInlineAttachment(oldText) || oldText == newText) return null

        val changeStart = firstDifferenceIndex(oldText, newText)
        var oldEnd = oldText.length - 1
        var newEnd = newText.length - 1
        while (oldEnd >= changeStart && newEnd >= changeStart && oldText[oldEnd] == newText[newEnd]) {
            oldEnd--
            newEnd--
        }

        var lineStart = 0
        oldText.split("\n").forEach { line ->
            val lineEndExclusive = lineStart + line.length
            val attachment = parseInlineAttachment(line)
            if (attachment != null && changeStart <= lineEndExclusive && oldEnd >= lineStart) {
                val removalEnd = if (lineEndExclusive < oldText.length) lineEndExclusive + 1 else lineEndExclusive
                val updatedText = oldText.removeRange(lineStart, removalEnd)
                return TextFieldValue(
                    text = updatedText,
                    selection = TextRange(lineStart.coerceAtMost(updatedText.length))
                )
            }
            lineStart = lineEndExclusive + 1
        }

        return null
    }

    private fun firstDifferenceIndex(oldText: String, newText: String): Int {
        val limit = minOf(oldText.length, newText.length)
        for (index in 0 until limit) {
            if (oldText[index] != newText[index]) {
                return index
            }
        }
        return limit
    }

    private fun migrateLegacyAttachmentToInline(item: MemoryItem): String {
        if (containsInlineAttachment(item.content)) {
            return item.content
        }

        val path = item.imagePath ?: return item.content
        return when (item.contentType) {
            ContentType.IMAGE -> buildInlineImageMarker(path)
            ContentType.FILE -> {
                val fileName = item.content.takeIf { it.isNotBlank() }
                    ?: path.substringAfterLast('/')
                buildInlineFileMarker(path, fileName)
            }

            else -> item.content
        }
    }
}
