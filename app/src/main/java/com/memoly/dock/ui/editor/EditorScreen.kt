@file:Suppress("DEPRECATION")
package com.memoly.dock.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.memoly.dock.domain.model.ContentType
import com.memoly.dock.ui.components.ContentTypeChip
import com.memoly.dock.ui.components.EditorToolbar
import com.memoly.dock.ui.components.ReminderTimePicker
import com.memoly.dock.ui.theme.MemolySecondary
import com.memoly.dock.ui.theme.MemolyTertiary
import com.memoly.dock.utils.InlineAttachmentType
import com.memoly.dock.utils.openStoredAttachment
import com.memoly.dock.utils.parseInlineAttachment

class MediaOffsetMapping(
    private val originalText: String,
    private val transformedText: AnnotatedString
) : OffsetMapping {

    override fun originalToTransformed(offset: Int): Int {
        var currentTransformed = 0
        var currentOriginal = 0

        val lines = originalText.split("\n")
        for (line in lines) {
            val lineLength = line.length
            val isMedia = parseInlineAttachment(line) != null

            if (offset <= currentOriginal + lineLength) {
                return if (isMedia) {
                    if (offset == currentOriginal) currentTransformed else currentTransformed + 1
                } else {
                    currentTransformed + (offset - currentOriginal)
                }
            }

            currentTransformed += if (isMedia) 1 else lineLength
            currentTransformed += 1
            currentOriginal += lineLength + 1
        }

        return transformedText.length
    }

    override fun transformedToOriginal(offset: Int): Int {
        var currentTransformed = 0
        var currentOriginal = 0

        val lines = originalText.split("\n")
        for (line in lines) {
            val isMedia = parseInlineAttachment(line) != null
            val transformedLineLength = if (isMedia) 1 else line.length

            if (offset <= currentTransformed + transformedLineLength) {
                return if (isMedia) {
                    if (offset == currentTransformed) currentOriginal else currentOriginal + line.length
                } else {
                    currentOriginal + (offset - currentTransformed)
                }
            }

            currentTransformed += transformedLineLength + 1
            currentOriginal += line.length + 1
        }

        return originalText.length
    }
}

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
    val title by viewModel.title.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val focusRequester = remember { FocusRequester() }
    val isEditing = editItemId != null
    val screenHeight = configuration.screenHeightDp.dp
    val maxImageHeight = screenHeight * 0.7f
    val defaultImageHeight = minOf(320.dp, maxImageHeight)
    val filePlaceholderHeight = 88.dp
    val fileLineHeight = with(density) { filePlaceholderHeight.toSp() }
    val imageRowHeights = remember { mutableStateMapOf<String, Dp>() }

    var showTagDialog by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }
    var previewImageUri by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let(viewModel::attachImage)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let(viewModel::attachFile)
    }

    LaunchedEffect(editItemId) {
        editItemId?.let(viewModel::loadItem)
    }

    LaunchedEffect(saveComplete) {
        if (saveComplete) onNavigateBack()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val annotatedString = remember(
        contentValue.text,
        imageRowHeights.toMap(),
        defaultImageHeight,
        fileLineHeight,
        density
    ) {
        buildAnnotatedString {
            val lines = contentValue.text.split("\n")
            lines.forEachIndexed { index, line ->
                when (val attachment = parseInlineAttachment(line)) {
                    null -> {
                        val checkboxIndex = line.indexOfFirst { it == '☐' || it == '☑' }
                        val isListItem = checkboxIndex >= 0 && line.take(checkboxIndex).isBlank()
                        if (isListItem) {
                            append(line.take(checkboxIndex))
                            withStyle(SpanStyle(color = Color(0xFFFFC107))) {
                                append(line[checkboxIndex])
                            }
                            val textAfterCheckbox = line.substring(checkboxIndex + 1)
                            if (line[checkboxIndex] == '☑') {
                                withStyle(
                                    SpanStyle(
                                        textDecoration = TextDecoration.LineThrough,
                                        color = Color.Gray
                                    )
                                ) {
                                    append(textAfterCheckbox)
                                }
                            } else {
                                append(textAfterCheckbox)
                            }
                        } else {
                            append(line)
                        }
                    }

                    else -> {
                        val placeholderSize = if (attachment.type == InlineAttachmentType.IMAGE) {
                            with(density) {
                                (imageRowHeights[attachment.uri] ?: defaultImageHeight).toSp()
                            }
                        } else {
                            fileLineHeight
                        }
                        withStyle(ParagraphStyle(lineHeight = placeholderSize)) {
                            withStyle(SpanStyle(fontSize = placeholderSize, color = Color.Transparent)) {
                                append("\uFFFC")
                            }
                        }
                    }
                }
                if (index < lines.size - 1) append("\n")
            }
        }
    }

    val offsetMapping = remember(contentValue.text, annotatedString) {
        MediaOffsetMapping(contentValue.text, annotatedString)
    }

    previewImageUri?.let { uri ->
        FullscreenImageDialog(uri = uri, onDismiss = { previewImageUri = null })
    }

    if (showTagDialog) {
        var tempTags by remember { mutableStateOf(tags ?: "") }
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("Tags") },
            text = {
                OutlinedTextField(
                    value = tempTags,
                    onValueChange = { tempTags = it },
                    placeholder = { Text("work, ideas, travel") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateTags(tempTags)
                    showTagDialog = false
                }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) {
                    Text("Cancel")
                }
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
                title = {
                    Text(
                        if (isEditing) "Edit Memory" else "New Memory",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .imePadding()
        ) {
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    BasicTextField(
                        value = title,
                        onValueChange = viewModel::updateTitle,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (title.isEmpty()) {
                                Text(
                                    "Title (Optional)",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val scrollState = rememberScrollState()
                    BoxWithConstraints(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
                        val editorWidth = maxWidth

                        if (contentValue.text.isEmpty()) {
                            Text(
                                "Start writing...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }

                        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                        BasicTextField(
                            value = contentValue,
                            onValueChange = viewModel::updateContentValue,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp)
                                .focusRequester(focusRequester)
                                .pointerInput(offsetMapping) {
                                    detectTapGestures { offset ->
                                        textLayoutResult?.let { layout ->
                                            val transformedOffset = layout.getOffsetForPosition(offset)
                                            val originalOffset = offsetMapping.transformedToOriginal(transformedOffset)
                                            viewModel.toggleCheckboxAt(originalOffset)
                                        }
                                    }
                                },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 30.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            onTextLayout = { textLayoutResult = it },
                            visualTransformation = {
                                TransformedText(annotatedString, offsetMapping)
                            }
                        )

                        textLayoutResult?.let { layout ->
                            val lines = contentValue.text.split("\n")
                            var currentOriginalOffset = 0

                            lines.forEach { line ->
                                val attachment = parseInlineAttachment(line)
                                if (attachment != null) {
                                    val transformedOffset = offsetMapping.originalToTransformed(currentOriginalOffset)
                                    val safeOffset = transformedOffset.coerceAtMost(maxOf(annotatedString.length - 1, 0))
                                    val lineIndex = layout.getLineForOffset(safeOffset)
                                    val top = with(density) { layout.getLineTop(lineIndex).toDp() }
                                    val height = with(density) {
                                        (layout.getLineBottom(lineIndex) - layout.getLineTop(lineIndex)).toDp()
                                    }

                                    when (attachment.type) {
                                        InlineAttachmentType.IMAGE -> {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .offset(y = top)
                                                    .height(height)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(Color.Black.copy(alpha = 0.05f))
                                                    .clickable { previewImageUri = attachment.uri }
                                            ) {
                                                AsyncImage(
                                                    model = attachment.uri,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Fit,
                                                    onSuccess = { state ->
                                                        val drawable = state.result.drawable
                                                        val width = drawable.intrinsicWidth
                                                        val height = drawable.intrinsicHeight
                                                        if (width > 0 && height > 0) {
                                                            val measuredHeight = calculateInlineImageHeight(
                                                                availableWidth = editorWidth,
                                                                intrinsicWidth = width,
                                                                intrinsicHeight = height,
                                                                maxHeight = maxImageHeight
                                                            )
                                                            if (imageRowHeights[attachment.uri] != measuredHeight) {
                                                                imageRowHeights[attachment.uri] = measuredHeight
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }

                                        InlineAttachmentType.FILE -> {
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .offset(y = top)
                                                    .height(height)
                                                    .clickable {
                                                        openStoredAttachment(context, attachment.uri, "*/*")
                                                    },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.InsertDriveFile,
                                                        contentDescription = null,
                                                        tint = MemolySecondary
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column {
                                                        Text(
                                                            attachment.fileName ?: "File",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Text(
                                                            "Tap to open",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val checkboxIndex = line.indexOfFirst { it == '☐' || it == '☑' }
                                    if (checkboxIndex >= 0 && line.take(checkboxIndex).isBlank()) {
                                        val checkboxOffset = currentOriginalOffset + checkboxIndex
                                        val transformedOffset = offsetMapping.originalToTransformed(checkboxOffset)
                                        val lineIndex = layout.getLineForOffset(
                                            transformedOffset.coerceAtMost(maxOf(annotatedString.length - 1, 0))
                                        )
                                        val top = with(density) { layout.getLineTop(lineIndex).toDp() }
                                        val height = with(density) {
                                            (layout.getLineBottom(lineIndex) - layout.getLineTop(lineIndex)).toDp()
                                        }
                                        val left = with(density) {
                                            layout.getHorizontalPosition(transformedOffset, true).toDp()
                                        }

                                        Box(
                                            modifier = Modifier
                                                .offset(x = left - 10.dp, y = top)
                                                .width(48.dp)
                                                .height(height)
                                                .clickable {
                                                    viewModel.toggleCheckboxAt(checkboxOffset)
                                                }
                                        )
                                    }
                                }
                                currentOriginalOffset += line.length + 1
                            }
                        }
                    }
                }
            }

            EditorToolbar(
                isListMode = isListMode,
                onFileClick = { filePickerLauncher.launch("*/*") },
                onImageClick = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onTagClick = { showTagDialog = true },
                onListToggle = { viewModel.toggleListMode() },
                onReminderClick = { showReminderPicker = true }
            )
        }
    }
}

@Composable
private fun FullscreenImageDialog(
    uri: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

private fun calculateInlineImageHeight(
    availableWidth: Dp,
    intrinsicWidth: Int,
    intrinsicHeight: Int,
    maxHeight: Dp
): Dp {
    val widthFitHeight = availableWidth * (intrinsicHeight.toFloat() / intrinsicWidth.toFloat())
    return widthFitHeight.coerceAtMost(maxHeight)
}
