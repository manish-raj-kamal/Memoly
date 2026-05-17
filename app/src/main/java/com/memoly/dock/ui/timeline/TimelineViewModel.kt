package com.memoly.dock.ui.timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memoly.dock.data.local.MemolyDatabase
import com.memoly.dock.data.model.MemoryItem
import com.memoly.dock.data.repository.MemoryRepository
import com.memoly.dock.domain.model.ContentType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Timeline screen.
 * Handles search, filtering, and memory item operations.
 */
enum class TabType {
    HOME, FAVORITES, REMINDERS, NOTES
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TimelineViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MemoryRepository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _selectedFilter = MutableStateFlow<ContentType?>(null)
    val selectedFilter: StateFlow<ContentType?> = _selectedFilter.asStateFlow()

    private val _selectedTab = MutableStateFlow(TabType.HOME)
    val selectedTab: StateFlow<TabType> = _selectedTab.asStateFlow()

    private val _remindersFilter = MutableStateFlow<String>("Upcoming") // "Upcoming" or "Completed"
    val remindersFilter: StateFlow<String> = _remindersFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_CREATED_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    val memoryItems: StateFlow<List<MemoryItem>>
    val pinnedItems: StateFlow<List<MemoryItem>>

    init {
        val db = MemolyDatabase.getDatabase(application)
        repository = MemoryRepository(db.memoryItemDao())

        val allItemsFlow = combine(
            _searchQuery.debounce(300),
            _selectedFilter,
            _selectedTab,
            _remindersFilter,
            _sortOrder
        ) { query, filter, tab, remFilter, sort ->
            data class FilterState(val query: String, val filter: ContentType?, val tab: TabType, val remFilter: String, val sort: SortOrder)
            FilterState(query, filter, tab, remFilter, sort)
        }.flatMapLatest { state ->
            val baseFlow = when {
                state.query.isNotBlank() -> repository.searchItems(state.query)
                state.tab == TabType.FAVORITES -> repository.getFavorites()
                state.tab == TabType.NOTES -> repository.getItemsByType(ContentType.NOTE)
                state.tab == TabType.REMINDERS -> repository.getAllItems().map { items -> 
                    val now = System.currentTimeMillis()
                    items.filter { 
                        if (it.reminderTime == null) false
                        else if (state.remFilter == "Upcoming") it.reminderTime > now
                        else it.reminderTime <= now
                    }.sortedBy { it.reminderTime }
                }
                state.filter != null -> repository.getItemsByType(state.filter)
                else -> repository.getAllItems()
            }
            
            baseFlow.map { items ->
                when (state.sort) {
                    SortOrder.DATE_CREATED_DESC -> items.sortedByDescending { it.timestamp }
                    SortOrder.DATE_CREATED_ASC -> items.sortedBy { it.timestamp }
                    SortOrder.DATE_MODIFIED -> items.sortedByDescending { it.lastModifiedAt }
                    SortOrder.TAGS -> items.sortedBy { it.tags ?: "zzz" }
                    SortOrder.TYPE -> items.sortedBy { it.contentType.name }
                }
            }
        }

        memoryItems = allItemsFlow.map { items ->
            items.filter { !it.isPinned }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        pinnedItems = allItemsFlow.map { items ->
            items.filter { it.isPinned }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setTab(tab: TabType) {
        _selectedTab.value = tab
        _selectedFilter.value = null
    }

    fun setRemindersFilter(filter: String) {
        _remindersFilter.value = filter
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            _searchQuery.value = ""
        }
    }

    fun setFilter(type: ContentType?) {
        _selectedFilter.value = type
    }

    fun togglePin(id: Long) {
        viewModelScope.launch {
            repository.togglePin(id)
        }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(id)
        }
    }

    fun deleteItem(item: MemoryItem) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }
}
