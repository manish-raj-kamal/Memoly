package com.memoly.dock.services

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Handles on-device text recognition (OCR) using Google ML Kit.
 */
object OCRManager {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extracts text from an image URI.
     * Returns null if no text found or error occurred.
     */
    suspend fun extractText(context: Context, imageUri: String): String? {
        return try {
            val uri = Uri.parse(imageUri)
            val image = InputImage.fromFilePath(context, uri)
            val result = recognizer.process(image).await()
            result.text.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
