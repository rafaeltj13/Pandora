package dev.rafaelm.pandora.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStorageTest {
    @Test fun tasksAreShownByDefaultAndTheSelectedFilterIsRemembered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "tasks.test.${UUID.randomUUID()}"
        try {
            val store = TaskStorage(context, name)
            assertFalse(store.showEvents())
            store.setShowEvents(true)
            assertTrue(TaskStorage(context, name).showEvents())
            store.setShowEvents(false)
            assertFalse(TaskStorage(context, name).showEvents())
        } finally {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
