package dev.rafaelm.pandora.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.rafaelm.pandora.data.AppearanceRepository
import dev.rafaelm.pandora.data.ThemeMode
import dev.rafaelm.pandora.data.CatTheme

class AppearanceViewModel(private val repository: AppearanceRepository) : ViewModel() {
    val appearance = repository.appearance

    fun setMode(mode: ThemeMode) = repository.setMode(mode)
    fun setTheme(theme: CatTheme, dark: Boolean) = repository.setTheme(theme, dark)
    fun setBothThemes(theme: CatTheme) = repository.setBothThemes(theme)

    class Factory(private val repository: AppearanceRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppearanceViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return AppearanceViewModel(repository) as T
        }
    }
}
