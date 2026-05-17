package com.memoly.dock.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memoly.dock.data.local.MemolyDatabase
import com.memoly.dock.data.model.MemoryItem
import com.memoly.dock.data.repository.MemoryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the detail view of a memory item.
 */
class DetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MemoryRepository

    private val _memoryItem = MutableStateFlow<MemoryItem?>(null)
    val memoryItem: StateFlow<MemoryItem?> = _memoryItem.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    init {
        val db = MemolyDatabase.getDatabase(application)
        repository = MemoryRepository(db.memoryItemDao())
    }

    fun loadItem(id: Long) {
        viewModelScope.launch {
            repository.getItemById(id).collect { item ->
                _memoryItem.value = item
            }
        }
    }

    fun togglePin() {
        val item = _memoryItem.value ?: return
        viewModelScope.launch {
            repository.togglePin(item.id)
        }
    }

    fun toggleFavorite() {
        val item = _memoryItem.value ?: return
        viewModelScope.launch {
            repository.toggleFavorite(item.id)
        }
    }

    fun cancelReminder() {
        val item = _memoryItem.value ?: return
        viewModelScope.launch {
            repository.updateReminderTime(item.id, null)
        }
    }

    fun markReminderDone() {
        val item = _memoryItem.value ?: return
        viewModelScope.launch {
            repository.markReminderDone(item.id)
        }
    }

    fun rescheduleReminder(timeMillis: Long) {
        val item = _memoryItem.value ?: return
        viewModelScope.launch {
            repository.updateReminderTime(item.id, timeMillis)
        }
    }

    fun deleteItem() {
        val item = _memoryItem.value ?: return
        viewModelScope.launch {
            repository.delete(item)
            _deleted.value = true
        }
    }
}
