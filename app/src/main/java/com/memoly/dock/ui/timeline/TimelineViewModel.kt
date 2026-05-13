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
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TimelineViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MemoryRepository

    /** Current search query */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Whether search is active */
    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    /** Content type filter */
    private val _selectedFilter = MutableStateFlow<ContentType?>(null)
    val selectedFilter: StateFlow<ContentType?> = _selectedFilter.asStateFlow()

    /** All memory items (reactive) */
    val memoryItems: StateFlow<List<MemoryItem>>

    init {
        val db = MemolyDatabase.getDatabase(application)
        repository = MemoryRepository(db.memoryItemDao())

        memoryItems = combine(
            _searchQuery.debounce(300),
            _selectedFilter
        ) { query, filter ->
            Pair(query, filter)
        }.flatMapLatest { (query, filter) ->
            when {
                query.isNotBlank() -> repository.searchItems(query)
                filter != null -> repository.getItemsByType(filter)
                else -> repository.getAllItems()
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
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

    fun deleteItem(item: MemoryItem) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }
}
