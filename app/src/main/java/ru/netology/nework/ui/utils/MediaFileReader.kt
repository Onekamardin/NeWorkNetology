package ru.netology.nework.ui.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import ru.netology.nework.domain.model.MediaFile

const val MAX_MEDIA_SIZE_BYTES = 15 * 1024 * 1024

class MediaTooLargeException : Exception()
class UnsupportedMediaException : Exception()
class EmptyMediaException : Exception()

fun readMediaFile(context: Context, uri: Uri): MediaFile {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri)?.lowercase()
        ?: throw UnsupportedMediaException()

    if (!mimeType.startsWith("image/") && !mimeType.startsWith("audio/") && !mimeType.startsWith("video/")) {
        throw UnsupportedMediaException()
    }

    val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.takeIf(String::isNotBlank) ?: "attachment"

    val bytes = ByteArrayOutputStream().use { output ->
        val input = resolver.openInputStream(uri) ?: throw EmptyMediaException()
        input.use { stream ->
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count == -1) break
                total += count
                if (total > MAX_MEDIA_SIZE_BYTES) throw MediaTooLargeException()
                output.write(buffer, 0, count)
            }
        }
        output.toByteArray()
    }
    if (bytes.isEmpty()) throw EmptyMediaException()
    return MediaFile(fileName, mimeType, bytes)
}
