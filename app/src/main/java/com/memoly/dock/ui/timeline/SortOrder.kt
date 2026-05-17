package com.memoly.dock.ui.timeline

enum class SortOrder(val label: String) {
    DATE_CREATED_DESC("Newest First"),
    DATE_CREATED_ASC("Oldest First"),
    DATE_MODIFIED("Recently Modified"),
    TAGS("By Tags"),
    TYPE("By Content Type")
}
