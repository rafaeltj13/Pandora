package dev.rafaelm.pandora.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Optional features the user turns on in Settings. Home and Settings are always available. */
enum class Module { TASKS, DEVELOPMENT }

/** Device-local module switches, available before the first Compose frame. Every module starts off. */
class ModulesRepository(context: Context, preferencesName: String = "modules") {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(Module.entries.filter { preferences.getBoolean(it.name, false) }.toSet())
    val enabled = mutableEnabled.asStateFlow()

    fun setEnabled(module: Module, enabled: Boolean) {
        preferences.edit { putBoolean(module.name, enabled) }
        mutableEnabled.value = if (enabled) mutableEnabled.value + module else mutableEnabled.value - module
    }
}
