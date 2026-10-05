package sh.aminov.golda

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.runner.AndroidJUnitRunner
import sh.aminov.golda.data.GoldaDb
import sh.aminov.golda.data.Repo
import sh.aminov.golda.data.SettingsStore

/**
 * Runs the instrumented tests in [TestGoldaApplication], and only on an emulator.
 *
 * The guard comes first: on anything that is not an emulator the run stops before the app object
 * even exists. Note that it cannot stop Gradle itself from installing and afterwards uninstalling
 * the app on every connected device, which wipes a phone's data, so always set ANDROID_SERIAL to an
 * emulator before connectedDebugAndroidTest (see CONTRIBUTING.md).
 */
class GoldaTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        Emulator.require()
        return super.newApplication(cl, TestGoldaApplication::class.java.name, context)
    }
}

/** The app with its storage swapped: an in-memory database and a separate settings file. */
class TestGoldaApplication : GoldaApplication() {
    /** The in-memory database behind [repo], for tests that set up what the UI cannot (old rates). */
    val db: GoldaDb by lazy { Room.inMemoryDatabaseBuilder(this, GoldaDb::class.java).build() }

    override fun createRepo(): Repo = Repo(
        context = this,
        db = db,
        settings = SettingsStore(this, testSettings(this)),
    )

    companion object {
        /** The settings file the tests use; never the app's "settings". One DataStore per file and process. */
        const val SETTINGS_FILE = "settings-instrumented-test"

        @Volatile
        private var store: DataStore<Preferences>? = null

        fun testSettings(context: Context): DataStore<Preferences> = store ?: synchronized(this) {
            store ?: PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SETTINGS_FILE) }.also { store = it }
        }
    }
}

/** Whether this is an Android emulator: its virtual hardware or the qemu property, never a guess from the model name. */
object Emulator {
    fun detected(): Boolean {
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()
        return hardware == "ranchu" || hardware == "goldfish" ||
            property("ro.kernel.qemu") == "1" || property("ro.boot.qemu") == "1" ||
            product.startsWith("sdk_gphone") || product.startsWith("sdk_google_phone")
    }

    /** Fails at once anywhere else: these tests write data and must never run on a phone. */
    fun require() = check(detected()) {
        "Golda's instrumented tests run only on an emulator (HARDWARE=${Build.HARDWARE}, PRODUCT=${Build.PRODUCT}). " +
            "Set ANDROID_SERIAL=emulator-55xx and run again."
    }

    private fun property(name: String): String? = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
    }.getOrNull()
}
