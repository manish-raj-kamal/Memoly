package com.memoly.dock.data.local

import androidx.room.*
import com.memoly.dock.data.model.MemoryItem
import com.memoly.dock.domain.model.ContentType
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for memory items.
 * All queries return Flow for reactive UI updates.
 */
@Dao
interface MemoryItemDao {

    /** Get all items ordered by pinned first, then newest */
    @Query("SELECT * FROM memory_items ORDER BY isPinned DESC, timestamp DESC")
    fun getAllItems(): Flow<List<MemoryItem>>

    /** Get a single item by ID */
    @Query("SELECT * FROM memory_items WHERE id = :id")
    fun getItemById(id: Long): Flow<MemoryItem?>

    /** Get a single item by ID (suspend, non-flow) */
    @Query("SELECT * FROM memory_items WHERE id = :id")
    suspend fun getItemByIdOnce(id: Long): MemoryItem?

    /** Search items by content or tags */
    @Query("""
        SELECT * FROM memory_items 
        WHERE content LIKE '%' || :query || '%' 
        OR tags LIKE '%' || :query || '%'
        OR extractedText LIKE '%' || :query || '%'
        ORDER BY isPinned DESC, timestamp DESC
    """)
    fun searchItems(query: String): Flow<List<MemoryItem>>

    /** Get items by content type */
    @Query("SELECT * FROM memory_items WHERE contentType = :type ORDER BY isPinned DESC, timestamp DESC")
    fun getItemsByType(type: ContentType): Flow<List<MemoryItem>>

    /** Get items with upcoming reminders */
    @Query("SELECT * FROM memory_items WHERE reminderTime IS NOT NULL AND reminderTime > :currentTime ORDER BY reminderTime ASC")
    fun getUpcomingReminders(currentTime: Long = System.currentTimeMillis()): Flow<List<MemoryItem>>

    /** Get items within a date range */
    @Query("SELECT * FROM memory_items WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getItemsInRange(startTime: Long, endTime: Long): Flow<List<MemoryItem>>

    /** Insert a new item, returning the generated ID */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MemoryItem): Long

    /** Update an existing item */
    @Update
    suspend fun update(item: MemoryItem)

    /** Delete an item */
    @Delete
    suspend fun delete(item: MemoryItem)

    /** Delete item by ID */
    @Query("DELETE FROM memory_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Toggle pin status */
    @Query("UPDATE memory_items SET isPinned = NOT isPinned WHERE id = :id")
    suspend fun togglePin(id: Long)

    /** Toggle favorite status */
    @Query("UPDATE memory_items SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    /** Get favorite items */
    @Query("SELECT * FROM memory_items WHERE isFavorite = 1 ORDER BY isPinned DESC, timestamp DESC")
    fun getFavorites(): Flow<List<MemoryItem>>

    /** Update reminder time for an item */
    @Query("UPDATE memory_items SET reminderTime = :reminderTime WHERE id = :id")
    suspend fun updateReminderTime(id: Long, reminderTime: Long?)

    /** Mark reminder as done */
    @Query("UPDATE memory_items SET isReminderDone = 1 WHERE id = :id")
    suspend fun markReminderDone(id: Long)

    /** Find item by image path (for duplicate detection) */
    @Query("SELECT * FROM memory_items WHERE imagePath = :imagePath LIMIT 1")
    suspend fun getItemByImagePath(imagePath: String): MemoryItem?

    /** Count all items */
    @Query("SELECT COUNT(*) FROM memory_items")
    fun getItemCount(): Flow<Int>
}
