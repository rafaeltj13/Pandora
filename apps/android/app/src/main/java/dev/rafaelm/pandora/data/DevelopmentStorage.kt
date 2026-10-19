package dev.rafaelm.pandora.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** GitHub credentials and the separate attachment token are encrypted at rest with Android Keystore. */
class DevelopmentStorage(context: Context, preferencesName: String = "development") : GitHubCredentialStore {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val alias = "pandora.github.credentials.v2.$preferencesName"
    private val json = Json { ignoreUnknownKeys = true }

    init {
        // The previous server session is not a GitHub token; never send it to GitHub.
        preferences.edit().remove("session").remove("pending").apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized
    override fun credentials(): GitHubCredentials? {
        val value = preferences.getString("github_credentials_v2", null) ?: return null
        return try { decrypt(value, GitHubCredentials.serializer()) }
        catch (_: Exception) { clear(); null }
    }

    @Synchronized
    override fun save(credentials: GitHubCredentials) {
        val value = encrypt(json.encodeToString(credentials))
        check(preferences.edit().putString("github_credentials_v2", value).commit()) { "Could not store GitHub connection" }
    }

    @Synchronized
    override fun uploadToken(): GitHubUploadToken? {
        val value = preferences.getString("github_upload_token_v1", null) ?: return null
        return try { decrypt(value, GitHubUploadToken.serializer()) }
        catch (_: Exception) {
            preferences.edit().remove("github_upload_token_v1").apply()
            null
        }
    }

    @Synchronized
    override fun saveUploadToken(token: GitHubUploadToken?) {
        val editor = preferences.edit()
        if (token == null) editor.remove("github_upload_token_v1")
        else editor.putString("github_upload_token_v1", encrypt(json.encodeToString(token)))
        check(editor.commit()) { if (token == null) "Could not remove attachment token" else "Could not store attachment token" }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun <T> decrypt(value: String, deserializer: kotlinx.serialization.DeserializationStrategy<T>): T {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return json.decodeFromString(deserializer, String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }

    @Synchronized
    override fun clear() {
        check(preferences.edit().remove("github_credentials_v2").remove("github_upload_token_v1").commit()) { "Could not remove GitHub connection" }
    }

    override fun selected(login: String): Set<String> = preferences.getStringSet("repos:${login.lowercase()}", emptySet())?.toSet() ?: emptySet()
    override fun select(login: String, names: Set<String>) {
        preferences.edit().putStringSet("repos:${login.lowercase()}", names).apply()
    }
}
