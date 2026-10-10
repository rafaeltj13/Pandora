package dev.rafaelm.pandora.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ServerStatus(val app: String, val message: String, val version: String)

fun interface StatusRepository {
    suspend fun load(): ServerStatus
}

class HttpStatusRepository(
    baseUrl: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StatusRepository {
    private val endpoint = URI("${baseUrl.trimEnd('/')}/api/v1/status").toURL()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): ServerStatus = withContext(dispatcher) {
        val connection = endpoint.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Pandora API returned HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { reader ->
                json.decodeFromString<ServerStatus>(reader.readText())
            }
        } finally {
            connection.disconnect()
        }
    }
}
