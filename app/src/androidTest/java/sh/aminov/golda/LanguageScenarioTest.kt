package sh.aminov.golda

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import sh.aminov.golda.data.AppLanguage

/** Russian ↔ English from Settings: the interface switches, and stays switched when the activity is recreated. */
@RunWith(AndroidJUnit4::class)
class LanguageScenarioTest : GoldaUiTest() {
    private var original: String? = null

    @Test
    fun languageSwitchesAndSurvivesRecreate() {
        original = AppLanguage.tag(app)
        val russianNow = AppLanguage.russian(app)
        val (from, to) = if (russianNow) Ui.RU to Ui.EN else Ui.EN to Ui.RU

        compose.onNodeWithContentDescription(from.settings).performClick()
        compose.waitForIdle()
        openRow(from.languageRow)
        tap(to.optionName)

        // The activity is recreated in the new language, still on Settings, which speaks it now.
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithText(to.languageRow).assertExists(); true }.getOrDefault(false)
        }
        assertEquals(to.tag, AppLanguage.tag(app))
        // Back to Home by the page's own arrow (a key event could reach Android 8 before the recreated
        // window has focus): Home's controls speak the new language too.
        compose.onNodeWithContentDescription(to.back).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(to.home).assertExists()

        scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(to.home).assertExists()
        compose.onNodeWithContentDescription(to.settings).assertExists()
    }

    /** Leaves the emulator's language as it found it. */
    @After
    fun restore() {
        val tag = original ?: return
        if (AppLanguage.tag(app) != tag) scenario.onActivity { AppLanguage.set(it, tag) }
    }

    private class Ui(val tag: String, val home: String, val settings: String, val languageRow: String, val optionName: String, val back: String) {
        companion object {
            val RU = Ui("ru", "Главная", "Настройки", "Язык приложения", "Русский", "Назад")
            val EN = Ui("en", "Home", "Settings", "App language", "English", "Back")
        }
    }
}
