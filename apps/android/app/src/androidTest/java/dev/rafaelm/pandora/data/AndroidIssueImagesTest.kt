package dev.rafaelm.pandora.data

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AndroidIssueImagesTest {
    private val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

    private fun withImage(bytes: ByteArray, test: suspend (String, String) -> Unit) = kotlinx.coroutines.runBlocking {
        val name = "pandora-test-${java.util.UUID.randomUUID()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
            test(uri.toString(), name)
        } finally { resolver.delete(uri, null, null) }
    }

    @Test fun selectedContentUriReadsNameTypeAndBytes() = withImage(byteArrayOf(1, 2, 3)) { uri, name ->
        val image = AndroidIssueImages(resolver).read(uri)
        assertEquals(name, image.name)
        assertEquals("image/png", image.contentType)
        assertArrayEquals(byteArrayOf(1, 2, 3), image.bytes)
    }

    @Test fun oversizedImageIsRejectedWhileReading() = withImage(ByteArray(GitHubImage.MAX_BYTES + 1)) { uri, _ ->
        try {
            AndroidIssueImages(resolver).read(uri)
            fail("Oversized image accepted")
        } catch (expected: java.io.IOException) {
            assertTrue(expected.message!!.contains("10 MB"))
        }
    }

    @Test fun emptyImageIsRejected() = withImage(byteArrayOf()) { uri, _ ->
        try {
            AndroidIssueImages(resolver).read(uri)
            fail("Empty image accepted")
        } catch (expected: java.io.IOException) {
            assertTrue(expected.message!!.contains("empty"))
        }
    }
}
