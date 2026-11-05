package sh.aminov.golda

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.OwnThemeScreen
import sh.aminov.golda.ui.SettingsScreen
import sh.aminov.golda.ui.SettingsSection
import sh.aminov.golda.ui.ThemeScreen
import java.util.TimeZone

/** What lies on which level of Settings and the Theme page; the rows' own logic is as it was. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1300dp-xxhdpi")
class SettingsSectionsTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private fun settings(section: SettingsSection? = null, backlog: Int = 0, onSection: (SettingsSection) -> Unit = {}, onTheme: () -> Unit = {}) {
        val data = ScreenshotTest().demo()
        compose.setContent {
            GoldaTheme("golda", "LIGHT") {
                SettingsScreen(
                    data, {}, {}, remember { SnackbarHostState() }, {}, {}, {}, {}, {},
                    voiceBacklog = backlog, onTheme = onTheme, section = section, onSection = onSection,
                )
            }
        }
    }

    private fun shown(vararg texts: String) = texts.forEach { compose.onNodeWithText(it).assertExists() }
    private fun gone(vararg texts: String) = texts.forEach { compose.onAllNodesWithText(it).assertCountEquals(0) }

    @Test
    fun theContentsAreSixRowsThatOpenTheirPages() {
        val opened = mutableListOf<SettingsSection>()
        var theme = 0
        settings(onSection = { opened += it }, onTheme = { theme++ })
        shown("Голос и разборы", "Копии и данные", "Напоминания", "Язык")
        gone("Местная валюта", "Ключ Gemini", "Стереть всё", "Напоминать о сверке")
        compose.onNodeWithText("Валюты и курс").performClick()
        compose.onNodeWithText("Голос и разборы").performClick()
        compose.onNodeWithText("Копии и данные").performClick()
        compose.onNodeWithText("Напоминания").performClick()
        compose.onNodeWithText("Тема").performClick()
        assertEquals(listOf(SettingsSection.MONEY, SettingsSection.VOICE, SettingsSection.DATA, SettingsSection.REMINDERS), opened)
        assertEquals(1, theme)
    }

    /** A failed voice note stays in sight on the contents, a level above its row. */
    @Test
    fun aFailedVoiceNoteShowsOnTheContents() {
        settings(backlog = 3)
        shown("Не разобрано: 3")
        gone("Gemini · ключа нет")
    }

    @Test
    fun theSummaryIsTheKeysWhenNothingFailed() {
        settings()
        shown("Gemini · ключа нет")
    }

    @Test
    fun moneyHoldsTheCurrenciesAndTheRatesWithoutExplanations() {
        settings(SettingsSection.MONEY)
        shown("Местная валюта", "Основная валюта", "Валюты", "Наценка к ЦБ", "Обновить курс", "1 $ = 101,2 ₽", "ЦБ 92")
        gone("₽ RUB, крупные цифры и итоги")
    }

    @Test
    fun voiceHoldsItsRowAsLongAsThereIsAFailure() {
        settings(SettingsSection.VOICE, backlog = 2)
        shown("Ключ Gemini", "Модель для голоса", "Модель для разборов", "Не разобрано: 2")
        gone("Голосовые ждут разбора")
    }

    @Test
    fun dataEndsWithTheErasing() {
        settings(SettingsSection.DATA)
        shown("Сохранить копию", "Восстановить", "Копия каждую неделю", "Стереть всё")
        gone("Счета, операции и настройки")
    }

    @Test
    fun theThemePageKeepsTheGeneratorALevelDown() {
        var own = 0
        compose.setContent {
            GoldaTheme("golda", "LIGHT") { ThemeScreen("golda", "LIGHT", emptyList(), {}, {}, {}, { own++ }, {}) }
        }
        shown("Плотность", "Цветные траты", "Чистый чёрный", "Спокойные", "Смелые")
        gone("Стиль", "Контраст", "Из обоев")
        compose.onNodeWithText("Своя тема").performClick()
        assertEquals(1, own)
    }

    /** The generated theme's save moved with the generator; it comes back unkept and is said to be on. */
    @Test
    fun theGeneratorKeepsWhatItBuilt() {
        val key = "custom:40:CALM:SYSTEM:1"
        val kept = mutableListOf<String>()
        compose.setContent {
            GoldaTheme("golda", "LIGHT") { OwnThemeScreen(key, "LIGHT", emptyList(), {}, {}, { kept += it }, {}) }
        }
        shown("Стиль", "Контраст", "Из обоев", "Случайная")
        compose.onNodeWithText("Сохранить").performClick()
        assertEquals(listOf(key), kept)
    }

    @Test
    fun theRowSaysAGeneratedThemeIsOn() {
        compose.setContent {
            GoldaTheme("golda", "LIGHT") { ThemeScreen("custom:40:CALM:SYSTEM:1", "LIGHT", emptyList(), {}, {}, {}, {}, {}) }
        }
        shown("Включена")
    }
}
