package dev.rafaelm.pandora.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.rafaelm.pandora.data.ServerStatus
import dev.rafaelm.pandora.data.StatusRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WelcomeState {
    data object Loading : WelcomeState
    data class Ready(val status: ServerStatus) : WelcomeState
    data object Error : WelcomeState
}

class WelcomeViewModel(private val repository: StatusRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<WelcomeState>(WelcomeState.Loading)
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init {
        retry()
    }

    fun retry() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            mutableState.value = WelcomeState.Loading
            mutableState.value = try {
                WelcomeState.Ready(repository.load())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                WelcomeState.Error
            }
        }
    }

    class Factory(private val repository: StatusRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WelcomeViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return WelcomeViewModel(repository) as T
        }
    }
}
