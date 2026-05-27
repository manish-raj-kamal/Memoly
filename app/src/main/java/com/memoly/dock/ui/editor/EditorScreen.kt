@file:Suppress("DEPRECATION")
package com.memoly.dock.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
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
    windowSizeClass: WindowSizeClass,
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
    val isCompactHeight = windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact
    val screenHeight = configuration.screenHeightDp.dp
    val maxImageHeight = screenHeight * 0.7f
    val defaultImageHeight = minOf(320.dp, maxImageHeight)
    val filePlaceholderHeight = 88.dp
    val fileLineHeight = with(density) { filePlaceholderHeight.toSp() }
    val imageRowHeights = remember { mutableStateMapOf<String, Dp>() }

    var showTagDialog by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isEditing) "Edit Memory" else "New Memory",
                            fontWeight = FontWeight.SemiBold,
                            style = if (isCompactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
                        )
                        if (isCompactHeight) {
                            Spacer(Modifier.width(12.dp))
                            ContentTypeChip(type = contentType)
                        }
                    }
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
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = if (isCompactHeight) PaddingValues(horizontal = 12.dp, vertical = 0.dp) else ButtonDefaults.ContentPadding
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontSize = if (isCompactHeight) 12.sp else 14.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // In landscape, we might want a side toolbar
            if (isCompactHeight) {
                EditorSideToolbar(
                    isListMode = isListMode,
                    onFileClick = { filePickerLauncher.launch("*/*") },
                    onImageClick = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onTagClick = { showTagDialog = true },
                    onListToggle = { viewModel.toggleListMode() },
                    onReminderClick = { showReminderPicker = true },
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                )
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 800.dp)
                        .padding(horizontal = 16.dp)
                        .imePadding()
                ) {
                    if (!isCompactHeight || !tags.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.padding(vertical = if (isCompactHeight) 4.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isCompactHeight) {
                                ContentTypeChip(type = contentType)
                            }
                            if (!tags.isNullOrBlank()) {
                                Text(
                                    text = tags.split(",").joinToString(" ") { "#${it.trim()}" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(if (isCompactHeight) 8.dp else 16.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        ) {
                            val editorWidth = maxWidth

                            Column(modifier = Modifier.fillMaxWidth()) {
                                BasicTextField(
                                    value = title,
                                    onValueChange = viewModel::updateTitle,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = (if (isCompactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge).copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        if (title.isEmpty()) {
                                            Text(
                                                "Title (Optional)",
                                                style = (if (isCompactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge).copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                        innerTextField()
                                    }
                                )

                                Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 12.dp))

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                )

                                Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 12.dp))

                                if (contentValue.text.isEmpty()) {
                                    Text(
                                        "Start writing...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }

                                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                                LaunchedEffect(contentValue.selection.start, textLayoutResult) {
                                    val layout = textLayoutResult ?: return@LaunchedEffect
                                    if (layout.layoutInput.text.text != annotatedString.text) return@LaunchedEffect
                                    if (annotatedString.isEmpty()) return@LaunchedEffect
                                    focusRequester.requestFocus()

                                    val transformedOffset = offsetMapping.originalToTransformed(
                                        contentValue.selection.start.coerceIn(0, contentValue.text.length)
                                    ).coerceAtMost(maxOf(annotatedString.length - 1, 0))
                                    val lineIndex = layout.getLineForOffset(transformedOffset)
                                    val lineBottom = layout.getLineBottom(lineIndex).toInt()
                                    val bottomPadding = with(density) { 72.dp.toPx() }.toInt()
                                    val targetScroll = (lineBottom - scrollState.viewportSize + bottomPadding)
                                        .coerceIn(0, scrollState.maxValue)

                                    if (targetScroll > scrollState.value) {
                                        scrollState.animateScrollTo(targetScroll)
                                    }
                                }

                                BasicTextField(
                                    value = contentValue,
                                    onValueChange = viewModel::updateContentValue,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 100.dp)
                                        .focusRequester(focusRequester)
                                        .pointerInput(offsetMapping) {
                                            detectTapGestures { offset ->
                                                textLayoutResult?.let { layout ->
                                                    if (layout.layoutInput.text.text != annotatedString.text) return@let
                                                    val transformedOffset = layout.getOffsetForPosition(offset)
                                                    val originalOffset = offsetMapping.transformedToOriginal(transformedOffset)
                                                    viewModel.toggleCheckboxAt(originalOffset)
                                                }
                                            }
                                        },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = if (isCompactHeight) 24.sp else 30.sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    onTextLayout = { textLayoutResult = it },
                                    visualTransformation = {
                                        TransformedText(annotatedString, offsetMapping)
                                    }
                                )

                                textLayoutResult?.let { layout ->
                                    if (layout.layoutInput.text.text != annotatedString.text) return@let
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
                                                            .clickable {
                                                                openStoredAttachment(context, attachment.uri, "image/*")
                                                            }
                                                    ) {
                                                        AsyncImage(
                                                            model = ImageRequest.Builder(context)
                                                                .data(attachment.uri)
                                                                .size(Size.ORIGINAL)
                                                                .crossfade(true)
                                                                .build(),
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

                    if (!isCompactHeight) {
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
                            onReminderClick = { showReminderPicker = true },
                            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EditorSideToolbar(
    isListMode: Boolean,
    onFileClick: () -> Unit,
    onImageClick: () -> Unit,
    onTagClick: () -> Unit,
    onListToggle: () -> Unit,
    onReminderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(56.dp).wrapContentHeight(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFFFF5CF).copy(alpha = 0.92f),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onFileClick) {
                Icon(Icons.Outlined.AddCircleOutline, contentDescription = "Add File", tint = Color.Black)
            }
            IconButton(onClick = onImageClick) {
                Icon(Icons.Outlined.Image, contentDescription = "Add Image", tint = Color.Black)
            }
            IconButton(onClick = onTagClick) {
                Icon(Icons.Outlined.Tag, contentDescription = "Tags", tint = Color.Black)
            }
            IconButton(
                onClick = onListToggle,
                modifier = Modifier.background(
                    if (isListMode) Color.Black.copy(alpha = 0.1f) else Color.Transparent,
                    RoundedCornerShape(12.dp)
                )
            ) {
                Icon(
                    imageVector = if (isListMode) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                    contentDescription = "Checklist Mode",
                    tint = Color.Black
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            IconButton(
                onClick = onReminderClick,
                modifier = Modifier.background(Color(0xFFFFC107), CircleShape)
            ) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = "Reminder", tint = Color.Black, modifier = Modifier.size(20.dp))
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
