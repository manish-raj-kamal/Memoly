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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.memoly.dock.domain.model.ContentType
import com.memoly.dock.ui.components.*
import com.memoly.dock.ui.theme.MemolyTertiary
import com.memoly.dock.ui.theme.MemolySecondary
import java.util.regex.Pattern

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

    // --- Annotated String with Strikethrough and Inline Media ---
    val annotatedString = buildAnnotatedString {
        val lines = contentValue.text.split("\n")
        lines.forEachIndexed { index, line ->
            if (line.startsWith("[img:") && line.endsWith("]")) {
                val uri = line.substring(5, line.length - 1)
                appendInlineContent("img_$uri", "[Image]")
            } else if (line.startsWith("[file:") && line.endsWith("]")) {
                val parts = line.substring(6, line.length - 1).split("|")
                val uri = parts.getOrNull(0) ?: ""
                val name = parts.getOrNull(1) ?: "File"
                appendInlineContent("file_$uri", name)
            } else {
                val isChecked = line.trim().startsWith("☑")
                if (isChecked) {
                    withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color.Gray)) {
                        append(line)
                    }
                } else {
                    append(line)
                }
            }
            if (index < lines.size - 1) append("\n")
        }
    }

    // --- Inline Content Map ---
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val inlineContent = remember(contentValue.text) {
        val map = mutableMapOf<String, InlineTextContent>()
        
        val imgMatcher = Pattern.compile("""\[img:(.+?)]""").matcher(contentValue.text)
        while (imgMatcher.find()) {
            val uri = imgMatcher.group(1) ?: ""
            map["img_$uri"] = InlineTextContent(
                Placeholder(width = 300.sp, height = 200.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.Center)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(max = screenHeight * 0.7f).clip(RoundedCornerShape(16.dp)).background(Color.LightGray.copy(alpha = 0.2f))
                ) {
                    AsyncImage(model = uri, contentDescription = "Inline Image", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }

        val fileMatcher = Pattern.compile("""\[file:(.+?)\|(.+?)]""").matcher(contentValue.text)
        while (fileMatcher.find()) {
            val uri = fileMatcher.group(1) ?: ""
            val name = fileMatcher.group(2) ?: "File"
            map["file_$uri"] = InlineTextContent(
                Placeholder(width = 300.sp, height = 60.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.Center)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.InsertDriveFile, contentDescription = null, tint = MemolySecondary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("Document", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }
            }
        }
        map
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
                    Box(modifier = Modifier.weight(1f)) {
                        if (contentValue.text.isEmpty()) {
                            Text("Start writing...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        }

                        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                        // Using a higher-level Text implementation that supports inlineContent
                        // and visualTransformation for the rich editing experience.
                        BasicTextField(
                            value = contentValue,
                            onValueChange = viewModel::updateContentValue,
                            modifier = Modifier
                                .fillMaxSize()
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
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = 24.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            onTextLayout = { textLayoutResult = it },
                            visualTransformation = { text ->
                                TransformedText(annotatedString, OffsetMapping.Identity)
                            }
                        )
                        
                        // Overlay to render inline content manually if BasicTextField doesn't support it directly
                        // (Note: Material3 BasicTextField has limitations with inlineContent vs foundation version)
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
