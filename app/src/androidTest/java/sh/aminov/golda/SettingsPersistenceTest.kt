package sh.aminov.golda

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import sh.aminov.golda.data.SettingsStore

/**
 * What a process restart does to the settings: one store writes them and is closed, a brand-new
 * store opens the same file and reads them back. Uses its own file, never the app's.
 */
@RunWith(AndroidJUnit4::class)
class SettingsPersistenceTest {
    @Test
    fun settingsComeBackAfterTheStoreIsReopened() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = "settings-persistence-test"
        context.preferencesDataStoreFile(file).delete()

        // Each store lives in its own scope; closing it (cancel and wait) is what a process exit does.
        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(firstJob + Dispatchers.IO)
        val first = SettingsStore(context, PreferenceDataStoreFactory.create(scope = firstScope) { context.preferencesDataStoreFile(file) })
        first.update {
            it.copy(localCurrency = "THB", displayCurrencies = listOf("RUB", "THB"), periodStart = 20_400, periodEnd = 20_430, spendAccountId = 7, markup = 0.125)
        }
        firstJob.cancelAndJoin()

        val secondJob = SupervisorJob()
        val secondScope = CoroutineScope(secondJob + Dispatchers.IO)
        val again = SettingsStore(context, PreferenceDataStoreFactory.create(scope = secondScope) { context.preferencesDataStoreFile(file) })
            .flow.first()
        secondJob.cancelAndJoin()
        context.preferencesDataStoreFile(file).delete()

        assertEquals("THB", again.localCurrency)
        assertEquals(listOf("RUB", "THB"), again.displayCurrencies)
        assertEquals(20_400L, again.periodStart)
        assertEquals(20_430L, again.periodEnd)
        assertEquals(7L, again.spendAccountId)
        assertEquals(0.125, again.markup, 1e-9)
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun emulatorOnly() = Emulator.require()
    }
}
