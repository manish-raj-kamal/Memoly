@file:Suppress("DEPRECATION")
package com.memoly.dock.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memoly.dock.ui.components.SettingsToggleRow
import com.memoly.dock.ui.theme.MemolySecondary

/**
 * Privacy-focused settings screen.
 * Every automated feature has a clearly described toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    windowSizeClass: WindowSizeClass,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val screenshotDetection by viewModel.screenshotDetection.collectAsStateWithLifecycle()
    val clipboardMonitoring by viewModel.clipboardMonitoring.collectAsStateWithLifecycle()
    val reminderParsing by viewModel.reminderParsing.collectAsStateWithLifecycle()
    val linkMetadata by viewModel.linkMetadata.collectAsStateWithLifecycle()
    val autoTagging by viewModel.autoTagging.collectAsStateWithLifecycle()
    val notificationReminders by viewModel.notificationReminders.collectAsStateWithLifecycle()
    val backgroundObservers by viewModel.backgroundObservers.collectAsStateWithLifecycle()
    val autoStartup by viewModel.autoStartup.collectAsStateWithLifecycle()
    val quickTileEnabled by viewModel.quickTileEnabled.collectAsStateWithLifecycle()
    val gestureTrigger by viewModel.gestureTrigger.collectAsStateWithLifecycle()
    val commandBar by viewModel.commandBar.collectAsStateWithLifecycle()

    val isCompactHeight = windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier = if (isCompactHeight) Modifier.height(48.dp) else Modifier,
                title = {
                    Text(
                        "Settings",
                        style = if (isCompactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = if (isCompactHeight) Modifier.size(40.dp) else Modifier) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 800.dp)
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Privacy banner
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = if (isCompactHeight) 8.dp else 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MemolySecondary.copy(alpha = 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MemolySecondary,
                            modifier = Modifier.size(if (isCompactHeight) 24.dp else 32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Privacy First",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MemolySecondary
                            )
                            Text(
                                "All data stays on your device. No tracking, no analytics, no cloud sync.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Section: Quick Capture
                SettingsSectionHeader("Quick Capture", isCompact = isCompactHeight)

                SettingsToggleRow(
                    title = "Quick Settings Tile",
                    description = "Show 'New Memory' tile in the notification shade for instant capture.",
                    icon = Icons.Outlined.Dashboard,
                    checked = quickTileEnabled,
                    onCheckedChange = viewModel::setQuickTileEnabled
                )

                SettingsToggleRow(
                    title = "Gesture Trigger",
                    description = "Use accessibility shortcut to open quick capture from anywhere.",
                    icon = Icons.Outlined.TouchApp,
                    checked = gestureTrigger,
                    onCheckedChange = viewModel::setGestureTrigger
                )

                SettingsToggleRow(
                    title = "Command Bar",
                    description = "Show quick command chips (?rem, ?todo, ?pin) while typing.",
                    icon = Icons.Outlined.SmartButton,
                    checked = commandBar,
                    onCheckedChange = viewModel::setCommandBar
                )

                // Section: Capture
                SettingsSectionHeader("Capture Features", isCompact = isCompactHeight)

                SettingsToggleRow(
                    title = "Screenshot Detection",
                    description = "Automatically detect and save screenshots into your timeline.",
                    icon = Icons.Outlined.Screenshot,
                    checked = screenshotDetection,
                    onCheckedChange = viewModel::setScreenshotDetection
                )

                SettingsToggleRow(
                    title = "Clipboard Monitoring",
                    description = "Monitor clipboard for copied text and links to save automatically.",
                    icon = Icons.Outlined.ContentPaste,
                    checked = clipboardMonitoring,
                    onCheckedChange = viewModel::setClipboardMonitoring
                )

                // Section: Intelligence
                SettingsSectionHeader("Smart Features", isCompact = isCompactHeight)

                SettingsToggleRow(
                    title = "Reminder Parsing",
                    description = "Detect ?rem commands in notes and create automatic reminders.",
                    icon = Icons.Outlined.NotificationsActive,
                    checked = reminderParsing,
                    onCheckedChange = viewModel::setReminderParsing
                )

                SettingsToggleRow(
                    title = "Link Metadata",
                    description = "Fetch webpage titles and previews for saved links. Requires internet.",
                    icon = Icons.Outlined.Language,
                    checked = linkMetadata,
                    onCheckedChange = viewModel::setLinkMetadata
                )

                SettingsToggleRow(
                    title = "Auto Tagging",
                    description = "Automatically suggest tags based on content. May use online APIs.",
                    icon = Icons.Outlined.Label,
                    checked = autoTagging,
                    onCheckedChange = viewModel::setAutoTagging
                )

                // Section: Notifications
                SettingsSectionHeader("Notifications", isCompact = isCompactHeight)

                SettingsToggleRow(
                    title = "Notification Reminders",
                    description = "Show notifications for scheduled reminders.",
                    icon = Icons.Outlined.Notifications,
                    checked = notificationReminders,
                    onCheckedChange = viewModel::setNotificationReminders
                )

                // Section: Background
                SettingsSectionHeader("Background Behavior", isCompact = isCompactHeight)

                SettingsToggleRow(
                    title = "Background Observers",
                    description = "Allow background monitoring for screenshots and clipboard changes.",
                    icon = Icons.Outlined.Visibility,
                    checked = backgroundObservers,
                    onCheckedChange = viewModel::setBackgroundObservers
                )

                SettingsToggleRow(
                    title = "Start on Boot",
                    description = "Automatically start background observers when your device boots up.",
                    icon = Icons.Outlined.PowerSettingsNew,
                    checked = autoStartup,
                    onCheckedChange = viewModel::setAutoStartup
                )

                // Section: About
                SettingsSectionHeader("About", isCompact = isCompactHeight)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Memoly v1.0",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "A private second memory for Android.\n\n" +
                                    "• All data stored locally on-device\n" +
                                    "• No tracking or analytics\n" +
                                    "• No cloud sync\n" +
                                    "• No user content uploaded without consent\n" +
                                    "• Open and transparent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, isCompact: Boolean = false) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = if (isCompact) 12.dp else 20.dp, bottom = 8.dp)
    )
}
