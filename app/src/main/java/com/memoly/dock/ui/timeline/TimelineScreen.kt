@file:Suppress("DEPRECATION")
package com.memoly.dock.ui.timeline

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.memoly.dock.data.model.MemoryItem
import com.memoly.dock.domain.model.ContentType
import com.memoly.dock.ui.components.*
import com.memoly.dock.ui.theme.*
import com.memoly.dock.utils.extractFirstInlineImageUri
import com.memoly.dock.utils.firstInlineFileName
import com.memoly.dock.utils.textWithoutInlineAttachments
import com.memoly.dock.utils.*

/**
 * Main Timeline screen — the heart of Memoly.
 * Displays all memory items in a chronological timeline grouped by date,
 * with a vertical timeline indicator, timestamps, and bottom navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onAddClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: TimelineViewModel = viewModel()
) {
    val items by viewModel.memoryItems.collectAsStateWithLifecycle()
    val pinnedItems by viewModel.pinnedItems.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearchActive by viewModel.isSearchActive.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val remindersFilter by viewModel.remindersFilter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

    val searchFocusRequester = remember { FocusRequester() }
    var showSortMenu by remember { mutableStateOf(false) }

    // Auto-focus search field when it becomes active
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TimelineTopBar(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onSearchToggle = { viewModel.setSearchActive(!isSearchActive) },
                onSettingsClick = onSettingsClick,
                sortOrder = sortOrder,
                onSortClick = { showSortMenu = true },
                searchFocusRequester = searchFocusRequester
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MemolyPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(56.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = MemolyPrimary.copy(alpha = 0.4f),
                        spotColor = MemolyPrimary.copy(alpha = 0.4f)
                    )
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add memory", modifier = Modifier.size(28.dp))
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.shadow(elevation = 8.dp)
            ) {
                NavigationBarItem(
                    selected = selectedTab == TabType.HOME,
                    onClick = { viewModel.setTab(TabType.HOME) },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    label = { Text("Home", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MemolyPrimary,
                        selectedTextColor = MemolyPrimary,
                        indicatorColor = MemolyPrimary.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == TabType.FAVORITES,
                    onClick = { viewModel.setTab(TabType.FAVORITES) },
                    icon = { Icon(if (selectedTab == TabType.FAVORITES) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = null) },
                    label = { Text("Favourites", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MemolyPrimary,
                        selectedTextColor = MemolyPrimary,
                        indicatorColor = MemolyPrimary.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == TabType.REMINDERS,
                    onClick = { viewModel.setTab(TabType.REMINDERS) },
                    icon = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null) },
                    label = { Text("Reminders", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MemolyPrimary,
                        selectedTextColor = MemolyPrimary,
                        indicatorColor = MemolyPrimary.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == TabType.NOTES,
                    onClick = { viewModel.setTab(TabType.NOTES) },
                    icon = { Icon(Icons.Outlined.StickyNote2, contentDescription = null) },
                    label = { Text("Notes", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MemolyPrimary,
                        selectedTextColor = MemolyPrimary,
                        indicatorColor = MemolyPrimary.copy(alpha = 0.1f)
                    )
                )
            }
        }
    ) { padding ->
        // Sorting Modal Bottom Sheet or Menu
        if (showSortMenu) {
            ModalBottomSheet(
                onDismissRequest = { showSortMenu = false },
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        "Sort By",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Bold
                    )
                    SortOrder.entries.forEach { order ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSortOrder(order)
                                    showSortMenu = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = sortOrder == order, onClick = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(order.label)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips
            if (selectedTab == TabType.REMINDERS) {
                RemindersFilterRow(
                    selectedFilter = remindersFilter,
                    onFilterSelected = viewModel::setRemindersFilter
                )
            } else if (selectedTab == TabType.HOME) {
                FilterChipRow(
                    selectedFilter = selectedFilter,
                    onFilterSelected = viewModel::setFilter
                )
            }

            if (items.isEmpty() && pinnedItems.isEmpty()) {
                EmptyTimelineState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // 1. Pinned Section
                    if (pinnedItems.isNotEmpty()) {
                        item {
                            TimelineSectionHeader(title = "Pinned")
                        }
                        items(
                            items = pinnedItems,
                            key = { "pinned_${it.id}" }
                        ) { memoryItem ->
                            TimelineItemRow(
                                item = memoryItem,
                                onClick = { onItemClick(memoryItem.id) },
                                onFavoriteToggle = { viewModel.toggleFavorite(memoryItem.id) },
                                onPinToggle = { viewModel.togglePin(memoryItem.id) },
                                onDelete = { viewModel.deleteItem(memoryItem) },
                                modifier = Modifier.animateItem()
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // 2. Main Timeline Section
                    // Group items by date for chronological view (Home/Notes/Favorites)
                    // For Reminders, we might not want date grouping as they are already sorted by time
                    val showDateHeaders = selectedTab != TabType.REMINDERS
                    
                    if (showDateHeaders) {
                        val groupedItems = items.groupBy { it.timestamp.toGroupDateString() }
                        groupedItems.forEach { (date, dateItems) ->
                            item(key = "header_$date") {
                                TimelineDateHeader(date = date)
                            }
                            items(
                                items = dateItems,
                                key = { it.id }
                            ) { memoryItem ->
                                TimelineItemRow(
                                    item = memoryItem,
                                    onClick = { onItemClick(memoryItem.id) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(memoryItem.id) },
                                    onPinToggle = { viewModel.togglePin(memoryItem.id) },
                                    onDelete = { viewModel.deleteItem(memoryItem) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    } else {
                        // For Reminders or non-chronological sorting, just list them
                        item {
                            TimelineSectionHeader(title = if (selectedTab == TabType.REMINDERS) "Reminders" else "Memories")
                        }
                        items(
                            items = items,
                            key = { it.id }
                        ) { memoryItem ->
                            TimelineItemRow(
                                item = memoryItem,
                                onClick = { onItemClick(memoryItem.id) },
                                onFavoriteToggle = { viewModel.toggleFavorite(memoryItem.id) },
                                onPinToggle = { viewModel.togglePin(memoryItem.id) },
                                onDelete = { viewModel.deleteItem(memoryItem) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemindersFilterRow(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val filters = listOf("Upcoming", "Completed")
        items(filters) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter, style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MemolyPrimary.copy(alpha = 0.15f),
                    selectedLabelColor = MemolyPrimary
                )
            )
        }
    }
}

/**
 * Top bar: "M Memoly" logo + search + settings icons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    sortOrder: SortOrder,
    onSortClick: () -> Unit,
    searchFocusRequester: FocusRequester
) {
    TopAppBar(
        title = {
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            "Search memories...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MemolyPrimary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // "M" logo
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(MemolyPrimary, Color(0xFF9C88FF))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "M",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Memoly",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        },
        actions = {
            if (!isSearchActive) {
                IconButton(onClick = onSortClick) {
                    Icon(
                        imageVector = Icons.Outlined.Sort,
                        contentDescription = "Sort",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onSearchToggle) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Filled.Close else Icons.Outlined.Search,
                    contentDescription = if (isSearchActive) "Close search" else "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isSearchActive) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun FilterChipRow(
    selectedFilter: ContentType?,
    onFilterSelected: (ContentType?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { onFilterSelected(null) },
                label = { Text("All", style = MaterialTheme.typography.labelMedium) },
                leadingIcon = if (selectedFilter == null) {
                    {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null,
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MemolyPrimary.copy(alpha = 0.15f),
                    selectedLabelColor = MemolyPrimary
                )
            )
        }
        items(ContentType.entries.toList()) { type ->
            FilterChip(
                selected = selectedFilter == type,
                onClick = { onFilterSelected(if (selectedFilter == type) null else type) },
                label = { Text(contentTypeLabel(type), style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = contentTypeIcon(type),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (selectedFilter == type) contentTypeColor(type)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = contentTypeColor(type).copy(alpha = 0.12f),
                    selectedLabelColor = contentTypeColor(type)
                )
            )
        }
    }
}

@Composable
private fun TimelineSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        )
    }
}

/**
 * Date group header — matches screenshot ("Today", "Yesterday", etc.)
 */
@Composable
private fun TimelineDateHeader(
    date: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = date,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

/**
 * Single timeline row: time on left | vertical line with dot | card on right.
 * Matches the provided screenshot design.
 */
@Composable
fun TimelineItemRow(
    item: MemoryItem,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onPinToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Memory") },
            text = { Text("Are you sure you want to delete this memory? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
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

    // Determine dot color based on content type
    val dotColor = contentTypeColor(item.contentType)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.Top
    ) {
        // ── Left column: timestamp ──
        Column(
            modifier = Modifier.width(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = item.timestamp.toTimelineTimeString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }

        // ── Vertical timeline line + dot ──
        Column(
            modifier = Modifier.width(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Line above dot
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            )
            // Dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            // Line below dot
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f, fill = true)
                    .defaultMinSize(minHeight = 40.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // ── Card ──
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Top row: badge + actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContentTypeChip(type = item.contentType)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Favorite icon
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (item.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onFavoriteToggle() }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        // Pin icon
                        Icon(
                            imageVector = if (item.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (item.isPinned) MemolyTertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onPinToggle() }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        // Delete icon
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { showDeleteDialog = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Colored icon square + content row
                Row(verticalAlignment = Alignment.Top) {
                    // Small colored icon thumbnail
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(dotColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val previewImage = item.imagePath ?: extractFirstInlineImageUri(item.content)
                        if ((item.contentType == ContentType.SCREENSHOT || item.contentType == ContentType.IMAGE) && previewImage != null) {
                            AsyncImage(
                                model = previewImage,
                                contentDescription = "Preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = contentTypeIcon(item.contentType),
                                contentDescription = null,
                                tint = dotColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        val contentText = textWithoutInlineAttachments(item.content)
                        val fallbackTitle = when {
                            item.contentType == ContentType.FILE -> firstInlineFileName(item.content) ?: "Document"
                            item.contentType == ContentType.IMAGE -> "Image"
                            contentText.isNotBlank() -> contentText.lines().firstOrNull().orEmpty()
                            else -> item.content.lines().firstOrNull() ?: item.content
                        }
                        val displayTitle = item.title?.takeIf { it.isNotBlank() } ?: fallbackTitle
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Subtitle — source or secondary info
                        val subtitle = when {
                            item.contentType == ContentType.LINK -> item.content.extractDomain() ?: "Link"
                            item.contentType == ContentType.SCREENSHOT -> item.sourceApp ?: "Screenshot"
                            item.sourceApp != null -> item.sourceApp
                            contentText.lines().size > 1 -> contentText.lines().drop(1).joinToString(" ").take(60)
                            else -> if (item.title?.isNotBlank() == true) contentText.take(60) else ""
                        }
                        if (subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Reminder badge
                if (item.reminderTime != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (item.isReminderDone) MaterialTheme.colorScheme.surfaceVariant else MemolySecondary.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (item.isReminderDone) Icons.Outlined.CheckCircle else Icons.Outlined.Notifications,
                                    contentDescription = null,
                                    tint = if (item.isReminderDone) MaterialTheme.colorScheme.onSurfaceVariant else MemolySecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = item.reminderTime.toTimeString(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        textDecoration = if (item.isReminderDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                    ),
                                    color = if (item.isReminderDone) MaterialTheme.colorScheme.onSurfaceVariant else MemolySecondary
                                )
                            }
                        }
                    }
                }

                // Tags row
                if (!item.tags.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.tags.split(",").take(3).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MemolyPrimary.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = "#${tag.trim()}",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MemolyPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
