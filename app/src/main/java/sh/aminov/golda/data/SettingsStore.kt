package sh.aminov.golda.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import sh.aminov.golda.domain.Settings

private val Context.dataStore by preferencesDataStore("settings")

/** The settings, in the app's "settings" DataStore unless a test hands in its own [store]. */
class SettingsStore(context: Context, private val store: DataStore<Preferences> = context.dataStore) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val spendAccountId = longPreferencesKey("spendAccountId")
        val periodStart = longPreferencesKey("periodStart")
        val periodEnd = longPreferencesKey("periodEnd")
        val displayCurrencies = stringPreferencesKey("displayCurrencies")
        val localCurrency = stringPreferencesKey("localCurrency")
        val baseCurrency = stringPreferencesKey("baseCurrency")
        val markup = doublePreferencesKey("markup")
        val lastAccountId = longPreferencesKey("lastAccountId")
        val geminiModel = stringPreferencesKey("geminiModel")
        val reviewModel = stringPreferencesKey("reviewModel")
        val geminiKey = stringPreferencesKey("geminiKey")
        val reconcileReminder = booleanPreferencesKey("reconcileReminder")
        val palette = stringPreferencesKey("palette")
        val themeMode = stringPreferencesKey("themeMode")
        val autoBackupFolder = stringPreferencesKey("autoBackupFolder")
        val lastAutoBackup = longPreferencesKey("lastAutoBackup")
        val savedThemes = stringPreferencesKey("savedThemes")
        val reconciledAt = stringPreferencesKey("reconciledAt")
    }

    val flow: Flow<Settings> = store.data.map { it.toSettings() }

    suspend fun update(change: (Settings) -> Settings) {
        store.edit { prefs -> prefs.write(change(prefs.toSettings())) }
    }

    suspend fun geminiKey(): String? = store.data.first()[K.geminiKey]?.let(KeyVault::decrypt)

    suspend fun setGeminiKey(key: String) {
        store.edit { prefs ->
            if (key.isBlank()) prefs.remove(K.geminiKey) else prefs[K.geminiKey] = KeyVault.encrypt(key.trim())
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            onboarded = this[K.onboarded] ?: d.onboarded,
            spendAccountId = this[K.spendAccountId],
            periodStart = this[K.periodStart] ?: d.periodStart,
            periodEnd = this[K.periodEnd] ?: d.periodEnd,
            displayCurrencies = this[K.displayCurrencies]?.split(',')?.filter { it.isNotBlank() } ?: d.displayCurrencies,
            localCurrency = this[K.localCurrency] ?: d.localCurrency,
            baseCurrency = this[K.baseCurrency] ?: d.baseCurrency,
            markup = this[K.markup] ?: d.markup,
            lastAccountId = this[K.lastAccountId],
            geminiModel = this[K.geminiModel] ?: d.geminiModel,
            reviewModel = this[K.reviewModel] ?: d.reviewModel,
            hasGeminiKey = this[K.geminiKey] != null,
            reconcileReminder = this[K.reconcileReminder] ?: d.reconcileReminder,
            palette = this[K.palette] ?: d.palette,
            // Before the mode was one setting, some themes were dark (or light) only: keep them so.
            themeMode = this[K.themeMode] ?: legacyThemeMode(this[K.palette]),
            autoBackupFolder = this[K.autoBackupFolder],
            lastAutoBackup = this[K.lastAutoBackup] ?: d.lastAutoBackup,
            savedThemes = this[K.savedThemes]?.split('|')?.filter { it.isNotBlank() } ?: d.savedThemes,
            reconciledAt = this[K.reconciledAt].orEmpty().split(',').mapNotNull { pair ->
                pair.split(':').takeIf { it.size == 2 }?.let { (id, at) -> id.toLongOrNull()?.let { key -> at.toLongOrNull()?.let { key to it } } }
            }.toMap(),
        )
    }

    private fun MutablePreferences.write(s: Settings) {
        this[K.onboarded] = s.onboarded
        if (s.spendAccountId != null) this[K.spendAccountId] = s.spendAccountId else remove(K.spendAccountId)
        this[K.periodStart] = s.periodStart
        this[K.periodEnd] = s.periodEnd
        this[K.displayCurrencies] = s.displayCurrencies.joinToString(",")
        this[K.localCurrency] = s.localCurrency
        this[K.baseCurrency] = s.baseCurrency
        this[K.markup] = s.markup
        if (s.lastAccountId != null) this[K.lastAccountId] = s.lastAccountId else remove(K.lastAccountId)
        this[K.geminiModel] = s.geminiModel
        this[K.reviewModel] = s.reviewModel
        this[K.reconcileReminder] = s.reconcileReminder
        this[K.palette] = s.palette
        this[K.themeMode] = s.themeMode
        if (s.autoBackupFolder != null) this[K.autoBackupFolder] = s.autoBackupFolder else remove(K.autoBackupFolder)
        this[K.lastAutoBackup] = s.lastAutoBackup
        this[K.savedThemes] = s.savedThemes.joinToString("|")
        this[K.reconciledAt] = s.reconciledAt.entries.joinToString(",") { "${it.key}:${it.value}" }
    }
}

/** The mode a theme picked before 0.19.0 had: the dark-only presets dark, a generated theme its own. */
internal fun legacyThemeMode(palette: String?): String = when {
    palette == null -> "SYSTEM"
    palette in setOf("midnight", "espresso", "forest", "graphite", "clear-night") -> "DARK"
    palette in setOf("beach", "sakura", "mint", "lavender", "clear") -> "LIGHT"
    palette.startsWith("custom:") -> palette.split(':').getOrNull(3)?.takeIf { it in setOf("LIGHT", "DARK") } ?: "SYSTEM"
    else -> "SYSTEM"
}
