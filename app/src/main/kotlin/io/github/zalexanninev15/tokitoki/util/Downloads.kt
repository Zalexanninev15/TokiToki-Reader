package io.github.zalexanninev15.tokitoki.util

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Writes into the public Downloads folder.
 *
 * MediaStore is used rather than a raw File path so no storage permission is needed on
 * API 29+, and the file shows up in the Downloads app immediately.
 */
object Downloads {

    suspend fun saveImage(context: Context, url: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = fileNameFor(url)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(name))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("could not create the file")

                context.contentResolver.openOutputStream(uri).use { output ->
                    checkNotNull(output) { "could not open the file for writing" }
                    URL(url).openStream().use { input -> input.copyTo(output) }
                }
                name
            }
        }

    suspend fun saveText(context: Context, fileName: String, content: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("could not create the file")

                context.contentResolver.openOutputStream(uri).use { output ->
                    checkNotNull(output) { "could not open the file for writing" }
                    output.write(content.toByteArray())
                }
                fileName
            }
        }

    private fun fileNameFor(url: String): String {
        val tail = url.substringAfterLast('/').substringBefore('?').take(60)
        val safe = tail.filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
        val stamp = System.currentTimeMillis()
        return if (safe.contains('.')) "tokitoki_${stamp}_$safe" else "tokitoki_$stamp.jpg"
    }

    private fun mimeFor(name: String): String = when {
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".gif", true) -> "image/gif"
        name.endsWith(".webp", true) -> "image/webp"
        else -> "image/jpeg"
    }
}
