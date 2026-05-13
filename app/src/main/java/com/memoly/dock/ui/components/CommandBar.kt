@file:Suppress("DEPRECATION")
package com.memoly.dock.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memoly.dock.ui.theme.*

/**
 * Quick command bar — smart suggestion chips above the keyboard.
 *
 * Chips:
 * - ?rem  → Insert reminder command (with optional inline time picker)
 * - ?todo → Tag as todo
 * - ?pin  → Pin the note
 * - ?link → Mark as link
 *
 * Feels lightweight and modern, similar to keyboard smart suggestions.
 */
@Composable
fun CommandBar(
    onCommandInsert: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showTimePicker by remember { mutableStateOf(false) }

    // Inline time picker for ?rem
    if (showTimePicker) {
        ReminderTimePicker(
            onTimeSelected = { timeStr ->
                onCommandInsert("?rem $timeStr")
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        item {
            CommandChip(
                label = "?rem",
                icon = Icons.Outlined.NotificationsActive,
                color = MemolyWarning,
                onClick = { showTimePicker = true }
            )
        }
        item {
            CommandChip(
                label = "?todo",
                icon = Icons.Outlined.CheckCircleOutline,
                color = MemolySecondary,
                onClick = { onCommandInsert("?todo") }
            )
        }
        item {
            CommandChip(
                label = "?pin",
                icon = Icons.Outlined.PushPin,
                color = MemolyTertiary,
                onClick = { onCommandInsert("?pin") }
            )
        }
        item {
            CommandChip(
                label = "?link",
                icon = Icons.Outlined.Link,
                color = TypeLinkColor,
                onClick = { onCommandInsert("?link") }
            )
        }
    }
}

@Composable
private fun CommandChip(
    label: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.1f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Inline time picker for the ?rem command.
 * Shows quick preset buttons + custom option.
 */
@Composable
private fun ReminderTimePicker(
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Set Reminder",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Quick presets:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Preset time chips
                val presets = listOf(
                    "in 15 minutes" to "in 15 minutes",
                    "in 30 minutes" to "in 30 minutes",
                    "in 1 hour" to "in 1 hours",
                    "in 2 hours" to "in 2 hours",
                    "Tomorrow 9am" to "tomorrow 9am",
                    "Tomorrow 6pm" to "tomorrow 6pm"
                )

                presets.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { (label, value) ->
                            FilledTonalButton(
                                onClick = { onTimeSelected(value) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MemolyPrimary.copy(alpha = 0.1f),
                                    contentColor = MemolyPrimary
                                )
                            ) {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Custom time input
                var customTime by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = customTime,
                    onValueChange = { customTime = it },
                    label = { Text("Custom (e.g. 7pm, 3:30pm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MemolyPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )

                if (customTime.isNotBlank()) {
                    FilledTonalButton(
                        onClick = { onTimeSelected(customTime.trim()) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Set for $customTime")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
