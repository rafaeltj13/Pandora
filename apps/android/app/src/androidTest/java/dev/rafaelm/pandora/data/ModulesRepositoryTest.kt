package dev.rafaelm.pandora.data

import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class ModulesRepositoryTest {
    @Test
    fun modulesStartOffAndSwitchesPersist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "modules_test_${UUID.randomUUID()}"
        try {
            val repository = ModulesRepository(context, name)
            assertEquals(emptySet<Module>(), repository.enabled.value)

            repository.setEnabled(Module.TASKS, true)
            repository.setEnabled(Module.DEVELOPMENT, true)
            repository.setEnabled(Module.DEVELOPMENT, false)

            assertEquals(setOf(Module.TASKS), repository.enabled.value)
            assertEquals(setOf(Module.TASKS), ModulesRepository(context, name).enabled.value)
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
