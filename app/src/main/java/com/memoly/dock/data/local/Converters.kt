package com.memoly.dock.data.local

import androidx.room.TypeConverter
import com.memoly.dock.domain.model.ContentType

/**
 * Room type converters for custom types.
 */
class Converters {

    @TypeConverter
    fun fromContentType(value: ContentType): String {
        return value.name
    }

    @TypeConverter
    fun toContentType(value: String): ContentType {
        return ContentType.fromString(value)
    }
}
