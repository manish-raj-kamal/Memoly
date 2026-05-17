@file:Suppress("DEPRECATION")
package com.memoly.dock.ui.quickcapture

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memoly.dock.data.local.MemolyDatabase
import com.memoly.dock.data.model.MemoryItem
import com.memoly.dock.domain.model.ContentType
import com.memoly.dock.domain.usecase.ReminderParser
import com.memoly.dock.ui.components.QuickCommandBar
import com.memoly.dock.ui.theme.*
import com.memoly.dock.utils.containsUrl
import com.memoly.dock.utils.isUrl
import com.memoly.dock.workers.ReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Minimal quick-note overlay screen.
 *
 * Uses imePadding() so the card + command bar float above the keyboard.
 */
@Composable
fun QuickCaptureScreen(
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var text by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    // Auto-focus keyboard
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Scrim — tap outside card to dismiss
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() }
            .imePadding(), // Push everything above keyboard
        contentAlignment = Alignment.BottomCenter
    ) {
        // Card — main overlay
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* prevent tap-through */ },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.FlashOn,
                            contentDescription = null,
                            tint = MemolyPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Quick Capture",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Text input area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(14.dp)
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = "What do you want to remember?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MemolyPrimary),
                        maxLines = 4
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Command bar chips — always visible above save button
                QuickCommandBar(
                    onCommandInsert = { command ->
                        text = if (text.isBlank()) command else "$text $command"
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Save button
                Button(
                    onClick = {
                        if (text.isBlank() || isSaving) return@Button
                        isSaving = true

                        scope.launch(Dispatchers.IO) {
                            try {
                                val parseResult = ReminderParser.parse(text.trim())
                                val finalContent = parseResult.cleanedText
                                val reminderTime = parseResult.reminderTimeMillis

                                val contentType = when {
                                    finalContent.isUrl() || finalContent.containsUrl() -> ContentType.LINK
                                    else -> ContentType.NOTE
                                }

                                // Handle ?pin
                                val isPinned = text.contains("?pin", ignoreCase = true)
                                val cleanContent = finalContent
                                    .replace(Regex("\\?pin\\s*", RegexOption.IGNORE_CASE), "")
                                    .trim()

                                val db = MemolyDatabase.getDatabase(context)
                                val newId = db.memoryItemDao().insert(
                                    MemoryItem(
                                        content = cleanContent.ifBlank { finalContent },
                                        contentType = contentType,
                                        isPinned = isPinned,
                                        reminderTime = reminderTime,
                                        sourceApp = "Quick Capture"
                                    )
                                )

                                // Schedule reminder if detected
                                if (reminderTime != null) {
                                    ReminderWorker.schedule(
                                        context = context,
                                        memoryId = newId,
                                        content = cleanContent,
                                        triggerAtMillis = reminderTime
                                    )
                                }

                                launch(Dispatchers.Main) {
                                    Toast.makeText(context, "✓ Saved to Memoly", Toast.LENGTH_SHORT).show()
                                    onSaved()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                launch(Dispatchers.Main) {
                                    Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
                                    isSaving = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = text.isNotBlank() && !isSaving,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MemolyPrimary,
                        contentColor = Color.White
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Memory", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
