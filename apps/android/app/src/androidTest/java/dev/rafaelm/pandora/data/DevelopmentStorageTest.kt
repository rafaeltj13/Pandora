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
        val uploadToken = GitHubUploadToken("github_pat_private_value", "test_client", "owner")
        try {
            val store = DevelopmentStorage(context, name)
            store.save(credentials)
            store.saveUploadToken(uploadToken)
            store.select("owner", setOf("private-repo"))
            val ciphertext = context.getSharedPreferences(name, 0).getString("github_credentials_v2", null)!!
            assertFalse(ciphertext.contains(credentials.accessToken))
            assertFalse(ciphertext.contains(credentials.refreshToken!!))
            val uploadCiphertext = context.getSharedPreferences(name, 0).getString("github_upload_token_v1", null)!!
            assertFalse(uploadCiphertext.contains(uploadToken.token))
            assertEquals(credentials, DevelopmentStorage(context, name).credentials())
            assertEquals(uploadToken, DevelopmentStorage(context, name).uploadToken())
            store.saveUploadToken(null)
            assertNull(DevelopmentStorage(context, name).uploadToken())
            assertEquals(credentials, DevelopmentStorage(context, name).credentials())
            store.saveUploadToken(uploadToken)
            store.clear()
            assertNull(DevelopmentStorage(context, name).credentials())
            assertNull(DevelopmentStorage(context, name).uploadToken())
            assertEquals(setOf("private-repo"), store.selected("owner"))
        } finally {
            context.deleteSharedPreferences(name)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("pandora.github.credentials.v2.$name") }
        }
    }
    @Test fun corruptAttachmentTokenIsRemovedWithoutClearingTheGitHubConnection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "development_test_${UUID.randomUUID()}"
        val credentials = GitHubCredentials("ghu_test", null, Long.MAX_VALUE, Long.MAX_VALUE, "test_client", "owner")
        try {
            val store = DevelopmentStorage(context, name)
            store.save(credentials)
            val preferences = context.getSharedPreferences(name, 0)
            preferences.edit().putString("github_upload_token_v1", "invalid-ciphertext").commit()
            assertNull(store.uploadToken())
            assertFalse(preferences.contains("github_upload_token_v1"))
            assertEquals(credentials, store.credentials())
        } finally {
            context.deleteSharedPreferences(name)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("pandora.github.credentials.v2.$name") }
        }
    }

}
