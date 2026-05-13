@file:Suppress("DEPRECATION")
package com.memoly.dock.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.memoly.dock.domain.model.ContentType
import com.memoly.dock.ui.components.*
import com.memoly.dock.ui.theme.*
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
                            ReminderBadge(reminderTime = reminderTime.toDateTimeString())
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Image preview
                if (memory.contentType == ContentType.SCREENSHOT || memory.contentType == ContentType.IMAGE) {
                    memory.imagePath?.let { path ->
                        AsyncImage(
                            model = path,
                            contentDescription = "Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                // Content
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Link action
                if (memory.contentType == ContentType.LINK) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                val uri = if (memory.content.startsWith("http")) {
                                    Uri.parse(memory.content)
                                } else {
                                    Uri.parse("https://${memory.content}")
                                }
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            } catch (e: Exception) {
                                // Invalid URL
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Link")
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
