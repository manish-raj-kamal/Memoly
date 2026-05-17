package com.memoly.dock.data.repository

import com.memoly.dock.data.local.MemoryItemDao
import com.memoly.dock.data.model.MemoryItem
import com.memoly.dock.domain.model.ContentType
import kotlinx.coroutines.flow.Flow

/**
 * Repository for memory item operations.
 * Acts as the single source of truth for the data layer.
 */
class MemoryRepository(private val dao: MemoryItemDao) {

    /** Get all memory items as a reactive Flow */
    fun getAllItems(): Flow<List<MemoryItem>> = dao.getAllItems()

    /** Get a single item by ID */
    fun getItemById(id: Long): Flow<MemoryItem?> = dao.getItemById(id)

    /** Get a single item by ID (one-shot) */
    suspend fun getItemByIdOnce(id: Long): MemoryItem? = dao.getItemByIdOnce(id)

    /** Search items by query string */
    fun searchItems(query: String): Flow<List<MemoryItem>> = dao.searchItems(query)

    /** Get items filtered by content type */
    fun getItemsByType(type: ContentType): Flow<List<MemoryItem>> = dao.getItemsByType(type)

    /** Get items with upcoming reminders */
    fun getUpcomingReminders(): Flow<List<MemoryItem>> = dao.getUpcomingReminders()

    /** Get items within a date range */
    fun getItemsInRange(startTime: Long, endTime: Long): Flow<List<MemoryItem>> =
        dao.getItemsInRange(startTime, endTime)

    /** Insert a new memory item, returns generated ID */
    suspend fun insert(item: MemoryItem): Long = dao.insert(item)

    /** Update an existing memory item */
    suspend fun update(item: MemoryItem) = dao.update(item)

    /** Delete a memory item */
    suspend fun delete(item: MemoryItem) = dao.delete(item)

    /** Delete a memory item by ID */
    suspend fun deleteById(id: Long) = dao.deleteById(id)

    /** Toggle pin status for an item */
    suspend fun togglePin(id: Long) = dao.togglePin(id)

    /** Toggle favorite status for an item */
    suspend fun toggleFavorite(id: Long) = dao.toggleFavorite(id)

    /** Get favorite items */
    fun getFavorites(): Flow<List<MemoryItem>> = dao.getFavorites()

    /** Update reminder time for an item */
    suspend fun updateReminderTime(id: Long, reminderTime: Long?) =
        dao.updateReminderTime(id, reminderTime)

    /** Mark reminder as done */
    suspend fun markReminderDone(id: Long) = dao.markReminderDone(id)

    /** Get total item count */
    fun getItemCount(): Flow<Int> = dao.getItemCount()
}
