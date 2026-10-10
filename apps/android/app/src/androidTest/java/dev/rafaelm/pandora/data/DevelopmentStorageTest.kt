package dev.rafaelm.pandora.data

import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class DevelopmentStorageTest {
    @Test fun tokensAreEncryptedPersistAcrossStoreInstancesAndCanBeRemovedOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "development_test_${UUID.randomUUID()}"
        val credentials = GitHubCredentials("ghu_test_access", "ghr_test_refresh", 123456, 654321, "test_client", "owner")
        try {
            val store = DevelopmentStorage(context, name)
            store.save(credentials)
            store.select("owner", setOf("private-repo"))
            val ciphertext = context.getSharedPreferences(name, 0).getString("github_credentials_v2", null)!!
            assertFalse(ciphertext.contains(credentials.accessToken))
            assertFalse(ciphertext.contains(credentials.refreshToken!!))
            assertEquals(credentials, DevelopmentStorage(context, name).credentials())
            store.clear()
            assertNull(DevelopmentStorage(context, name).credentials())
            assertEquals(setOf("private-repo"), store.selected("owner"))
        } finally {
            context.deleteSharedPreferences(name)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("pandora.github.credentials.v2.$name") }
        }
    }
}
