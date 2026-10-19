package dev.rafaelm.pandora.data

import android.content.ContentResolver
import androidx.core.net.toUri
import android.provider.OpenableColumns
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads only a selected image, with a bound even when the provider omits its size. */
class AndroidIssueImages(private val resolver: ContentResolver) {
    suspend fun read(value: String): GitHubImage = withContext(Dispatchers.IO) {
        val uri = value.toUri()
        require(uri.scheme == "content") { "Choose an image from your photos." }
        val type = resolver.getType(uri)
        val extension = when (type) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> throw IOException("Choose a PNG, JPEG, GIF or WebP image.")
        }
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf { it.isNotBlank() } ?: "image.$extension"
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > GitHubImage.MAX_BYTES) throw IOException("Images must be 10 MB or smaller.")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IOException("Could not open the selected image.")
        if (bytes.isEmpty()) throw IOException("The selected image is empty.")
        GitHubImage(name, type, bytes)
    }
}
