@file:Suppress("DEPRECATION")
package com.memoly.dock.ui.editor

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.memoly.dock.domain.model.ContentType
import com.memoly.dock.ui.components.*
import com.memoly.dock.ui.theme.MemolyTertiary
import com.memoly.dock.ui.theme.MemolySecondary
import java.util.regex.Pattern

/**
 * Custom OffsetMapping to handle media markers replacement with a single character placeholder.
 */
class MediaOffsetMapping(private val originalText: String, private val transformedText: AnnotatedString) : OffsetMapping {
    
    override fun originalToTransformed(offset: Int): Int {
        var currentTransformed = 0
        var currentOriginal = 0
        
        val lines = originalText.split("\n")
        for (line in lines) {
            val lineLength = line.length
            val isMedia = line.startsWith("[img:") || line.startsWith("[file:")
            
            if (offset <= currentOriginal + lineLength) {
                if (isMedia) {
                    return currentTransformed
                } else {
                    return currentTransformed + (offset - currentOriginal)
                }
            }
            
            currentTransformed += if (isMedia) 1 else lineLength
            currentTransformed += 1 // \n
            currentOriginal += lineLength + 1
        }
        return transformedText.length
    }

    override fun transformedToOriginal(offset: Int): Int {
        var currentTransformed = 0
        var currentOriginal = 0
        
        val lines = originalText.split("\n")
        for (line in lines) {
            val isMedia = line.startsWith("[img:") || line.startsWith("[file:")
            val transformedLineLen = if (isMedia) 1 else line.length
            
            if (offset <= currentTransformed + transformedLineLen) {
                if (isMedia) {
                    return currentOriginal + line.length
                } else {
                    return currentOriginal + (offset - currentTransformed)
                }
            }
            
            currentTransformed += transformedLineLen + 1
            currentOriginal += line.length + 1
        }
        return originalText.length
    }
}

/**
 * Redesigned note editor screen with inline media, interactive checklists, and compact toolbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    editItemId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val contentValue by viewModel.contentValue.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val isPinned by viewModel.isPinned.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveComplete by viewModel.saveComplete.collectAsStateWithLifecycle()
    val contentType by viewModel.contentType.collectAsStateWithLifecycle()
    val isListMode by viewModel.isListMode.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val isEditing = editItemId != null
    var isTextFieldFocused by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }

    // Media launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.attachImage(it.toString()) }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = getFileName(context, it) ?: "Document"
            viewModel.attachFile(it.toString(), fileName)
        }
    }

    // Load existing item if editing
    LaunchedEffect(editItemId) {
        editItemId?.let { viewModel.loadItem(it) }
    }

    // Navigate back on save complete
    LaunchedEffect(saveComplete) {
        if (saveComplete) onNavigateBack()
    }

    // Auto-focus on content field
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // --- Annotated String for Rendering ---
    val annotatedString = buildAnnotatedString {
        val lines = contentValue.text.split("\n")
        lines.forEachIndexed { index, line ->
            if (line.startsWith("[img:") && line.endsWith("]")) {
                append("\uFFFC") 
            } else if (line.startsWith("[file:") && line.endsWith("]")) {
                append("\uFFFD")
            } else {
                val isChecked = line.trim().startsWith("☑")
                if (isChecked) {
                    // Checkbox character itself in yellow, text in gray with strikethrough
                    withStyle(style = SpanStyle(color = Color(0xFFFFC107))) {
                        append("☑")
                    }
                    withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color.Gray)) {
                        append(line.replaceFirst("☑", ""))
                    }
                } else {
                    append(line)
                }
            }
            if (index < lines.size - 1) append("\n")
        }
    }

    if (showTagDialog) {
        var tempTags by remember { mutableStateOf(tags ?: "") }
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("Tags") },
            text = {
                OutlinedTextField(value = tempTags, onValueChange = { tempTags = it }, placeholder = { Text("work, ideas, travel") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateTags(tempTags)
                    showTagDialog = false
                }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showReminderPicker) {
        ReminderTimePicker(
            onTimeSelected = { timeStr ->
                val currentText = contentValue.text
                val inserted = if (currentText.isBlank()) "?rem $timeStr" else "$currentText ?rem $timeStr"
                viewModel.updateContentValue(TextFieldValue(inserted, TextRange(inserted.length)))
                showReminderPicker = false
            },
            onDismiss = { showReminderPicker = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Memory" else "New Memory", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = viewModel::togglePin) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (isPinned) MemolyTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = viewModel::save,
                        enabled = (contentValue.text.isNotBlank() || contentType != ContentType.NOTE) && !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).imePadding()
        ) {
            Row(
                modifier = Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ContentTypeChip(type = contentType)
                if (!tags.isNullOrBlank()) {
                    Text(
                        text = tags.split(",").joinToString(" ") { "#${it.trim()}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val title by viewModel.title.collectAsStateWithLifecycle()
                    BasicTextField(
                        value = title,
                        onValueChange = viewModel::updateTitle,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (title.isEmpty()) {
                                Text("Title (Optional)", style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontWeight = FontWeight.Bold))
                            }
                            innerTextField()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val scrollState = rememberScrollState()
                    Box(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
                        if (contentValue.text.isEmpty()) {
                            Text("Start writing...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        }

                        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                        val screenHeight = LocalConfiguration.current.screenHeightDp.dp

                        BasicTextField(
                            value = contentValue,
                            onValueChange = viewModel::updateContentValue,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp)
                                .focusRequester(focusRequester)
                                .onFocusChanged { isTextFieldFocused = it.isFocused }
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        textLayoutResult?.let { layout ->
                                            val position = layout.getOffsetForPosition(offset)
                                            viewModel.toggleCheckboxAt(position)
                                        }
                                    }
                                },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = 32.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            onTextLayout = { textLayoutResult = it },
                            visualTransformation = { text ->
                                TransformedText(annotatedString, MediaOffsetMapping(contentValue.text, annotatedString))
                            }
                        )
                        
                        // Media Overlay
                        val lines = contentValue.text.split("\n")
                        Column {
                            lines.forEach { line ->
                                if (line.startsWith("[img:")) {
                                    val uri = line.substring(5, line.length - 1)
                                    Box(
                                        modifier = Modifier.fillMaxWidth().heightIn(max = screenHeight * 0.7f).padding(vertical = 8.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.05f))
                                    ) {
                                        AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                                    }
                                } else if (line.startsWith("[file:")) {
                                    val parts = line.substring(6, line.length - 1).split("|")
                                    val name = parts.getOrNull(1) ?: "File"
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.InsertDriveFile, contentDescription = null, tint = MemolySecondary)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                Text("Document", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(32.sp.toDp()))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            EditorToolbar(
                isListMode = isListMode,
                onFileClick = { filePickerLauncher.launch("*/*") },
                onImageClick = { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onTagClick = { showTagDialog = true },
                onListToggle = { viewModel.toggleListMode() },
                onReminderClick = { showReminderPicker = true }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun getFileName(context: android.content.Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = cursor.getString(index)
            }
        }
    }
    if (result == null) {
        result = uri.path?.let { path ->
            val cut = path.lastIndexOf('/')
            if (cut != -1) path.substring(cut + 1) else path
        }
    }
    return result
}

@Composable
fun TextUnit.toDp(): androidx.compose.ui.unit.Dp {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return with(density) { this@toDp.toDp() }
}
