package com.memoly.dock.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memoly.dock.domain.model.ContentType
import com.memoly.dock.ui.theme.*

/**
 * Get the icon for a content type.
 */
@Suppress("DEPRECATION")
@Composable
fun contentTypeIcon(type: ContentType): ImageVector {
    return when (type) {
        ContentType.TEXT -> Icons.Outlined.TextSnippet
        ContentType.LINK -> Icons.Outlined.Link
        ContentType.SCREENSHOT -> Icons.Outlined.Screenshot
        ContentType.IMAGE -> Icons.Outlined.Image
        ContentType.NOTE -> Icons.Outlined.StickyNote2
    }
}

/**
 * Get the color for a content type.
 */
fun contentTypeColor(type: ContentType): Color {
    return when (type) {
        ContentType.TEXT -> TypeTextColor
        ContentType.LINK -> TypeLinkColor
        ContentType.SCREENSHOT -> TypeScreenshotColor
        ContentType.IMAGE -> TypeImageColor
        ContentType.NOTE -> TypeNoteColor
    }
}

/**
 * Get the label for a content type.
 */
fun contentTypeLabel(type: ContentType): String {
    return when (type) {
        ContentType.TEXT -> "Text"
        ContentType.LINK -> "Link"
        ContentType.SCREENSHOT -> "Screenshot"
        ContentType.IMAGE -> "Image"
        ContentType.NOTE -> "Note"
    }
}

/**
 * Content type chip indicator.
 */
@Composable
fun ContentTypeChip(
    type: ContentType,
    modifier: Modifier = Modifier
) {
    val color = contentTypeColor(type)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = contentTypeIcon(type),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = contentTypeLabel(type),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Date group header for the timeline.
 */
@Composable
fun DateGroupHeader(
    date: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = date,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            thickness = 1.dp
        )
    }
}

/**
 * Empty state for the timeline.
 */
@Composable
fun EmptyTimelineState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Psychology,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Your memory is empty",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap + to save your first memory",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Gradient overlay for cards and containers.
 */
@Composable
fun GradientOverlay(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                )
            )
    )
}

/**
 * Pin indicator icon with animation.
 */
@Composable
fun PinIndicator(
    isPinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isPinned) 1.1f else 1f,
        animationSpec = tween(200),
        label = "pin_scale"
    )
    val tint by animateColorAsState(
        targetValue = if (isPinned) MemolyTertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        animationSpec = tween(200),
        label = "pin_tint"
    )

    IconButton(
        onClick = onClick,
        modifier = modifier.scale(scale)
    ) {
        Icon(
            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
            contentDescription = if (isPinned) "Unpin" else "Pin",
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Reminder indicator badge.
 */
@Composable
fun ReminderBadge(
    reminderTime: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MemolyWarning.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = MemolyWarning,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = reminderTime,
                style = MaterialTheme.typography.labelSmall,
                color = MemolyWarning,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Settings toggle row with description.
 */
@Composable
fun SettingsToggleRow(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}
