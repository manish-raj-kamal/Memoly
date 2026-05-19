@file:Suppress("DEPRECATION")
package com.memoly.dock.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.memoly.dock.domain.model.ContentType
import com.memoly.dock.ui.components.*
import com.memoly.dock.ui.theme.*
import com.memoly.dock.utils.InlineAttachmentType
import com.memoly.dock.utils.extractFirstInlineImageUri
import com.memoly.dock.utils.extractUrls
import com.memoly.dock.utils.openStoredAttachment
import com.memoly.dock.utils.parseInlineAttachment
import com.memoly.dock.utils.*

/**
 * Detail view for a single memory item.
 * Shows full content, metadata, and actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    memoryId: Long,
    onNavigateBack: () -> Unit,
    onEditClick: (Long) -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val item by viewModel.memoryItem.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCancelReminderDialog by remember { mutableStateOf(false) }

    // Custom Date/Time Picker State
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var reminderError by remember { mutableStateOf<String?>(null) }
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()

    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateMillis = datePickerState.selectedDateMillis
                    if (selectedDateMillis == null) {
                        reminderError = "Pick a date first."
                    } else {
                        reminderError = null
                        showDatePicker = false
                        showTimePicker = true
                    }
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time Picker Dialog
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val calendar = java.util.Calendar.getInstance()
                    selectedDateMillis?.let { utcMillis ->
                        val utcCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = utcMillis
                        }
                        calendar.set(
                            utcCalendar.get(java.util.Calendar.YEAR),
                            utcCalendar.get(java.util.Calendar.MONTH),
                            utcCalendar.get(java.util.Calendar.DAY_OF_MONTH)
                        )
                    }
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                    calendar.set(java.util.Calendar.MINUTE, timePickerState.minute)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    if (calendar.timeInMillis <= System.currentTimeMillis()) {
                        reminderError = "Reminder must be set in the future."
                        return@TextButton
                    }
                    reminderError = null
                    viewModel.rescheduleReminder(calendar.timeInMillis)
                    showTimePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Back") }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    if (showCancelReminderDialog) {
        AlertDialog(
            onDismissRequest = { showCancelReminderDialog = false },
            title = { Text("Cancel Reminder") },
            text = { Text("Do you want to remove the reminder for this note?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelReminder()
                        showCancelReminderDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MemolyError)
                ) { Text("Remove Reminder") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelReminderDialog = false }) { Text("Keep it") }
            }
        )
    }

    LaunchedEffect(memoryId) {
        viewModel.loadItem(memoryId)
    }

    LaunchedEffect(deleted) {
        if (deleted) onNavigateBack()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Memory") },
            text = { Text("This memory will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MemolyError)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Memory Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    item?.let { memory ->
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (memory.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (memory.isFavorite) androidx.compose.ui.graphics.Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.togglePin() }) {
                            Icon(
                                imageVector = if (memory.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Pin",
                                tint = if (memory.isPinned) MemolyTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onEditClick(memory.id) }) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete",
                                tint = MemolyError.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        item?.let { memory ->
            val isMissedReminder = memory.isReminderMissed()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Metadata card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ContentTypeChip(type = memory.contentType)
                            if (memory.isPinned) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MemolyTertiary.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.PushPin,
                                            null,
                                            Modifier.size(12.dp),
                                            tint = MemolyTertiary
                                        )
                                        Text(
                                            "Pinned",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MemolyTertiary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Time info
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column {
                                Text(
                                    "Created",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    memory.createdAt.toDateTimeString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            memory.sourceApp?.let {
                                Column {
                                    Text(
                                        "Source",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Reminder info
                        memory.reminderTime?.let { reminderTime ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when {
                                            memory.isReminderDone -> Icons.Outlined.CheckCircle
                                            isMissedReminder -> Icons.Outlined.ErrorOutline
                                            else -> Icons.Outlined.Notifications
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            memory.isReminderDone -> MaterialTheme.colorScheme.onSurfaceVariant
                                            isMissedReminder -> MemolyError
                                            else -> MemolySecondary
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isMissedReminder) "Missed • ${reminderTime.toDateTimeString()}" else reminderTime.toDateTimeString(),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            textDecoration = if (memory.isReminderDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                        ),
                                        color = when {
                                            memory.isReminderDone -> MaterialTheme.colorScheme.onSurfaceVariant
                                            isMissedReminder -> MemolyError
                                            else -> MemolySecondary
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!memory.isReminderDone) {
                                    OutlinedButton(
                                        onClick = { viewModel.markReminderDone() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Mark Done", fontSize = 12.sp)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showDatePicker = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Reschedule", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { showCancelReminderDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Cancel", fontSize = 12.sp, color = MemolyError)
                                }
                            }
                            reminderError?.let { message ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MemolyError
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Title
                if (!memory.title.isNullOrBlank()) {
                    Text(
                        text = memory.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val contentLines = memory.content.lineSequence().toList()
                val legacyPreviewImage = memory.imagePath ?: extractFirstInlineImageUri(memory.content)

                if ((memory.contentType == ContentType.SCREENSHOT || memory.contentType == ContentType.IMAGE) &&
                    legacyPreviewImage != null && contentLines.none { parseInlineAttachment(it)?.type == InlineAttachmentType.IMAGE }
                ) {
                    InlineImageBlock(
                        imageUri = legacyPreviewImage,
                        onOpen = { openStoredAttachment(context, legacyPreviewImage, "image/*") }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                contentLines.forEachIndexed { index, line ->
                    when (val attachment = parseInlineAttachment(line)) {
                        null -> {
                            if (line.isNotBlank()) {
                                InlineTextLine(line = line, context = context)
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        else -> when (attachment.type) {
                            InlineAttachmentType.IMAGE -> {
                                InlineImageBlock(
                                    imageUri = attachment.uri,
                                    onOpen = { openStoredAttachment(context, attachment.uri, "image/*") }
                                )
                            }

                            InlineAttachmentType.FILE -> {
                                InlineDocumentBlock(
                                    attachment = attachment,
                                    onOpen = { openStoredAttachment(context, attachment.uri, "*/*") }
                                )
                            }
                        }
                    }

                    if (index < contentLines.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Tags
                if (!memory.tags.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Tags",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        memory.tags.split(",").forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "#${tag.trim()}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        } ?: run {
            // Loading state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun InlineImageBlock(
    imageUri: String,
    onOpen: () -> Unit
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUri)
            .size(Size.ORIGINAL)
            .crossfade(true)
            .build(),
        contentDescription = "Image",
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen),
        contentScale = ContentScale.FillWidth
    )
}

@Composable
private fun InlineDocumentBlock(
    attachment: com.memoly.dock.utils.InlineAttachment,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.InsertDriveFile, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    attachment.fileName ?: "Document",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Tap to open",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InlineTextLine(
    line: String,
    context: android.content.Context
) {
    val urls = line.extractUrls()
    val annotatedContent = buildAnnotatedString {
        var lastIndex = 0
        urls.forEach { url ->
            val startIndex = line.indexOf(url, lastIndex)
            if (startIndex != -1) {
                append(line.substring(lastIndex, startIndex))
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(url)
                }
                lastIndex = startIndex + url.length
            }
        }
        if (lastIndex < line.length) {
            append(line.substring(lastIndex))
        }
    }

    Text(
        text = annotatedContent,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )

    if (urls.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            urls.forEach { url ->
                OutlinedButton(
                    onClick = {
                        try {
                            val parsedUri = if (url.startsWith("http")) {
                                Uri.parse(url)
                            } else {
                                Uri.parse("https://$url")
                            }
                            context.startActivity(Intent(Intent.ACTION_VIEW, parsedUri))
                        } catch (_: Exception) {
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open $url", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}
