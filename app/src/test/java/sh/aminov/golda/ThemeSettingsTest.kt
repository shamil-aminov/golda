package sh.aminov.golda

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import sh.aminov.golda.data.SettingsStore
import sh.aminov.golda.domain.Settings

/** The Theme page's switches survive a store closed and opened again; a fresh store has them at their defaults. */
@RunWith(RobolectricTestRunner::class)
class ThemeSettingsTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun <T> withStore(file: String, block: suspend (SettingsStore) -> T): T = runBlocking {
        val job = SupervisorJob()
        val store = SettingsStore(context, PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { context.preferencesDataStoreFile(file) })
        block(store).also { job.cancelAndJoin() }
    }

    @Test
    fun theSwitchesComeBack() {
        val file = "theme-settings-test"
        context.preferencesDataStoreFile(file).delete()
        val fresh = withStore(file) { it.flow.first() }
        assertEquals(Settings().oled, fresh.oled)
        assertEquals(false, fresh.oled)
        assertEquals(true, fresh.colouredRows)

        withStore(file) { it.update { s -> s.copy(palette = "marathon", oled = true, colouredRows = false) } }
        val again = withStore(file) { it.flow.first() }
        context.preferencesDataStoreFile(file).delete()
        assertEquals("marathon", again.palette)
        assertEquals(true, again.oled)
        assertEquals(false, again.colouredRows)
    }
}
