@file:Suppress("DEPRECATION")
package com.memoly.dock.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memoly.dock.domain.usecase.ReminderParser
import com.memoly.dock.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Lightweight command bar for Quick Capture.
 */
@Composable
fun QuickCommandBar(
    onCommandInsert: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showReminderPicker by remember { mutableStateOf(false) }

    if (showReminderPicker) {
        ReminderTimePicker(
            onTimeSelected = { timeStr ->
                onCommandInsert("?rem $timeStr")
                showReminderPicker = false
            },
            onDismiss = { showReminderPicker = false }
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CommandChip(
            label = "?rem",
            icon = Icons.Outlined.NotificationsActive,
            color = MemolyWarning,
            onClick = { showReminderPicker = true }
        )
        CommandChip(
            label = "?todo",
            icon = Icons.Outlined.CheckCircleOutline,
            color = MemolySecondary,
            onClick = { onCommandInsert("?todo") }
        )
    }
}

@Composable
private fun CommandChip(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

/**
 * Compact toolbar for the editor, matching the user's reference image.
 * Features: File import (+), Image import, Tag dialog (#), Checklist toggle, Reminder picker (REM).
 */
@Composable
fun EditorToolbar(
    isListMode: Boolean,
    onFileClick: () -> Unit,
    onImageClick: () -> Unit,
    onTagClick: () -> Unit,
    onListToggle: () -> Unit,
    onReminderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFFFF5CF).copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFileClick, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Outlined.AddCircleOutline, contentDescription = "Add File", tint = Color.Black)
                }
                IconButton(onClick = onImageClick, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Outlined.Image, contentDescription = "Add Image", tint = Color.Black)
                }
                IconButton(onClick = onTagClick, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Outlined.Tag, contentDescription = "Tags", tint = Color.Black)
                }
                IconButton(
                    onClick = onListToggle,
                    modifier = Modifier.background(
                        if (isListMode) Color.Black.copy(alpha = 0.1f) else Color.Transparent,
                        RoundedCornerShape(999.dp)
                    ).size(42.dp)
                ) {
                    Icon(
                        imageVector = if (isListMode) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                        contentDescription = "Checklist Mode",
                        tint = Color.Black
                    )
                }
            }

            // Reminder Button (REM)
            Surface(
                onClick = onReminderClick,
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFFFC107),
                tonalElevation = 0.dp,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "REM",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

/**
 * Enhanced reminder picker with 8 presets and Date/Time picker support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimePicker(
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                        errorMessage = "Pick a date first."
                    } else {
                        errorMessage = null
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
                    val calendar = Calendar.getInstance()

                    // Correctly handle UTC millis from DatePickerState to local Calendar
                    selectedDateMillis?.let { utcMillis ->
                        val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = utcMillis
                        }
                        calendar.set(
                            utcCalendar.get(Calendar.YEAR),
                            utcCalendar.get(Calendar.MONTH),
                            utcCalendar.get(Calendar.DAY_OF_MONTH)
                        )
                    }
                    
                    calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    calendar.set(Calendar.MINUTE, timePickerState.minute)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)

                    if (calendar.timeInMillis <= System.currentTimeMillis()) {
                        errorMessage = "Reminder must be set in the future."
                        return@TextButton
                    }

                    // Use Locale.US to ensure month names match ReminderParser's English-only support
                    val sdf = SimpleDateFormat("d MMM h:mm a", Locale.US)
                    errorMessage = null
                    onTimeSelected(sdf.format(calendar.time))
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
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Quick presets:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Generate 8 presets
                val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                val nextWeek = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
                
                // Use Locale.US for internal commands
                val dateFormat = SimpleDateFormat("d MMM", Locale.US)
                
                val presets = listOf(
                    "15 mins" to "in 15 minutes",
                    "1 hour" to "in 1 hour",
                    "2 hours" to "in 2 hours",
                    "Tonight 9pm" to "9pm",
                    "Tomorrow 9am" to "tomorrow 9am",
                    "Tomorrow 8pm" to "tomorrow 8pm",
                    "Next Week" to "${dateFormat.format(nextWeek.time)} 9am",
                    "${dateFormat.format(tomorrow.time)} 10am" to "${dateFormat.format(tomorrow.time)} 10am"
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
                                contentPadding = PaddingValues(horizontal = 4.dp),
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

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                // Custom Date & Time Picker Button
                Button(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MemolyPrimary
                    )
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick Date & Time...")
                }

                // Manual custom input as fallback
                var customText by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    label = { Text("Or type (e.g. 20 May 8pm)", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MemolyPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )

                if (customText.isNotBlank()) {
                    Button(
                        onClick = {
                            val parsedTime = ReminderParser.parse("?rem ${customText.trim()}").reminderTimeMillis
                            if (parsedTime == null || parsedTime <= System.currentTimeMillis()) {
                                errorMessage = "Enter a future reminder time."
                            } else {
                                errorMessage = null
                                onTimeSelected(customText.trim())
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MemolySecondary)
                    ) {
                        Text("Set: $customText")
                    }
                }

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MemolyError,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
