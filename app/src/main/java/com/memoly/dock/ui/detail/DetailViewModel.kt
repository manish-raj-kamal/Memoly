package com.memoly.dock.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memoly.dock.data.local.MemolyDatabase
import com.memoly.dock.data.model.MemoryItem
import com.memoly.dock.data.repository.MemoryRepository
import com.memoly.dock.workers.ReminderWorker
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
            repository.update(
                item.copy(
                    reminderTime = null,
                    isReminderDone = false,
                    lastModifiedAt = System.currentTimeMillis()
                )
            )
            ReminderWorker.cancel(getApplication(), item.id)
        }
    }

    fun markReminderDone() {
        val item = _memoryItem.value ?: return
        viewModelScope.launch {
            repository.markReminderDone(item.id)
            ReminderWorker.cancel(getApplication(), item.id)
        }
    }

    fun rescheduleReminder(timeMillis: Long) {
        val item = _memoryItem.value ?: return
        if (timeMillis <= System.currentTimeMillis()) return
        viewModelScope.launch {
            val updatedItem = item.copy(
                reminderTime = timeMillis,
                isReminderDone = false,
                lastModifiedAt = System.currentTimeMillis()
            )
            repository.update(updatedItem)
            ReminderWorker.schedule(
                context = getApplication(),
                memoryId = item.id,
                content = item.content,
                triggerAtMillis = timeMillis
            )
        }
    }

    fun deleteItem() {
        val item = _memoryItem.value ?: return
        viewModelScope.launch {
            ReminderWorker.cancel(getApplication(), item.id)
            repository.delete(item)
            _deleted.value = true
        }
    }
}
