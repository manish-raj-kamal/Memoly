package com.memoly.dock.domain.model

/**
 * Enum representing the types of content a memory item can hold.
 * Extensible for future content types.
 */
enum class ContentType {
    TEXT,
    LINK,
    SCREENSHOT,
    IMAGE,
    NOTE;

    companion object {
        fun fromString(value: String): ContentType {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                TEXT
            }
        }
    }
}
