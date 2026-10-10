package dev.rafaelm.pandora.data

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class HttpStatusRepositoryTest {
    private lateinit var server: HttpServer
    private lateinit var repository: HttpStatusRepository
    private var responseCode = 200
    private var body = """{"app":"Pandora","message":"Welcome to Pandora","version":"0.1.0"}"""

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/status") { exchange ->
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(responseCode, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        repository = HttpStatusRepository("http://127.0.0.1:${server.address.port}")
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `HTTP response becomes typed status`() = runTest {
        assertEquals(ServerStatus("Pandora", "Welcome to Pandora", "0.1.0"), repository.load())
    }

    @Test
    fun `additional fields preserve client compatibility`() = runTest {
        body = """{"app":"Pandora","message":"Hello","version":"0.1.0","futureField":true}"""
        assertEquals("Hello", repository.load().message)
    }

    @Test
    fun `server failures are not presented as successful connections`() {
        responseCode = 503
        assertThrows(IOException::class.java) { runTest { repository.load() } }
    }

    @Test
    fun `missing required response fields are rejected`() {
        body = """{"app":"Pandora"}"""
        assertThrows(SerializationException::class.java) { runTest { repository.load() } }
    }
}
