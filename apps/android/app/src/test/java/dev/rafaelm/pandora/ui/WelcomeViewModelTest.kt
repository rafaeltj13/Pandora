package dev.rafaelm.pandora.ui

import dev.rafaelm.pandora.data.ServerStatus
import dev.rafaelm.pandora.data.StatusRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    private val status = ServerStatus("Pandora", "Welcome to Pandora", "0.1.0")

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial request goes from loading to ready`() = runTest {
        val model = WelcomeViewModel(StatusRepository { status })
        assertEquals(WelcomeState.Loading, model.state.value)
        advanceUntilIdle()
        assertEquals(WelcomeState.Ready(status), model.state.value)
    }

    @Test
    fun `failed request can recover through retry`() = runTest {
        var attempts = 0
        val model = WelcomeViewModel(StatusRepository {
            attempts++
            if (attempts == 1) throw IOException("Server unavailable")
            status
        })
        advanceUntilIdle()
        assertEquals(WelcomeState.Error, model.state.value)
        model.retry()
        advanceUntilIdle()
        assertEquals(WelcomeState.Ready(status), model.state.value)
        assertEquals(2, attempts)
    }

    @Test
    fun `repeated taps do not start simultaneous requests`() = runTest {
        var attempts = 0
        val response = CompletableDeferred<ServerStatus>()
        val model = WelcomeViewModel(StatusRepository {
            attempts++
            response.await()
        })
        runCurrent()
        repeat(5) { model.retry() }
        runCurrent()
        assertEquals(1, attempts)
        assertTrue(model.state.value is WelcomeState.Loading)
        response.complete(status)
        advanceUntilIdle()
        assertEquals(WelcomeState.Ready(status), model.state.value)
    }
}
