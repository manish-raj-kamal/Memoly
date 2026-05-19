package com.memoly.dock.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class InlineAttachmentType {
    IMAGE,
    FILE
}

data class InlineAttachment(
    val type: InlineAttachmentType,
    val uri: String,
    val fileName: String? = null
)

fun buildInlineImageMarker(uri: String): String = "[img:$uri]"

fun buildInlineFileMarker(uri: String, fileName: String): String = "[file:$uri|$fileName]"

fun parseInlineAttachment(line: String): InlineAttachment? {
    return when {
        line.startsWith("[img:") && line.endsWith("]") -> {
            InlineAttachment(
                type = InlineAttachmentType.IMAGE,
                uri = line.substring(5, line.length - 1)
            )
        }

        line.startsWith("[file:") && line.endsWith("]") -> {
            val content = line.substring(6, line.length - 1)
            val parts = content.split("|", limit = 2)
            InlineAttachment(
                type = InlineAttachmentType.FILE,
                uri = parts.firstOrNull().orEmpty(),
                fileName = parts.getOrNull(1)
            )
        }

        else -> null
    }
}

fun containsInlineAttachment(text: String): Boolean {
    return text.lineSequence().any { parseInlineAttachment(it) != null }
}

fun extractFirstInlineImageUri(text: String): String? {
    return text.lineSequence()
        .mapNotNull(::parseInlineAttachment)
        .firstOrNull { it.type == InlineAttachmentType.IMAGE }
        ?.uri
}

fun firstInlineFileName(text: String): String? {
    return text.lineSequence()
        .mapNotNull(::parseInlineAttachment)
        .firstOrNull { it.type == InlineAttachmentType.FILE }
        ?.fileName
}

fun textWithoutInlineAttachments(text: String): String {
    return text.lineSequence()
        .filter { parseInlineAttachment(it) == null }
        .joinToString("\n")
        .trim()
}

fun reminderNotificationText(text: String): String {
    val visibleText = textWithoutInlineAttachments(text)
    if (visibleText.isNotBlank()) {
        return visibleText.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.take(200)
            ?: visibleText.take(200)
    }

    val attachments = text.lineSequence().mapNotNull(::parseInlineAttachment).toList()
    if (attachments.isEmpty()) {
        return "You have a reminder"
    }

    if (attachments.size > 1) {
        return "${attachments.size} attachments"
    }

    val attachment = attachments.first()
    return when (attachment.type) {
        InlineAttachmentType.IMAGE -> "Image attachment"
        InlineAttachmentType.FILE -> attachment.fileName?.takeIf { it.isNotBlank() } ?: "Document attachment"
    }
}

fun getDisplayName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path?.substringAfterLast('/')
    }
    return result
}

fun copyUriToInternalStorage(context: Context, uri: Uri, preferredName: String? = null): String? {
    return try {
        val extension = resolveExtension(context, uri, preferredName)
        val baseName = preferredName
            ?.substringBeforeLast('.', missingDelimiterValue = preferredName)
            ?.ifBlank { UUID.randomUUID().toString() }
            ?: UUID.randomUUID().toString()

        val storageDir = File(context.filesDir, "inline_attachments")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val destination = File(storageDir, "$baseName-${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        } ?: return null

        Uri.fromFile(destination).toString()
    } catch (_: Exception) {
        null
    }
}

fun openStoredAttachment(context: Context, storedUri: String, mimeType: String) {
    val shareableUri = toShareableUri(context, storedUri) ?: run {
        Toast.makeText(context, "Unable to open attachment", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(shareableUri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

private fun toShareableUri(context: Context, storedUri: String): Uri? {
    return try {
        val parsed = Uri.parse(storedUri)
        when (parsed.scheme) {
            "content" -> parsed
            "file" -> {
                val path = parsed.path ?: return null
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(path)
                )
            }

            null -> FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(storedUri)
            )

            else -> parsed
        }
    } catch (_: Exception) {
        null
    }
}

private fun resolveExtension(context: Context, uri: Uri, preferredName: String?): String {
    val fromName = preferredName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
    if (!fromName.isNullOrBlank()) {
        return fromName
    }

    val mimeType = context.contentResolver.getType(uri)
    val fromMime = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    if (!fromMime.isNullOrBlank()) {
        return fromMime
    }

    return getDisplayName(context, uri)
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        ?.ifBlank { "dat" }
        ?: "dat"
}
