package com.memoly.dock.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.memoly.dock.data.model.MemoryItem

/**
 * Main Room database for Memoly.
 * All data is stored locally on-device — no cloud sync.
 */
@Database(
    entities = [MemoryItem::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MemolyDatabase : RoomDatabase() {

    abstract fun memoryItemDao(): MemoryItemDao

    companion object {
        @Volatile
        private var INSTANCE: MemolyDatabase? = null

        fun getDatabase(context: Context): MemolyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MemolyDatabase::class.java,
                    "memoly_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
