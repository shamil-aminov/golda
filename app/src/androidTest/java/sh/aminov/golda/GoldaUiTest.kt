package sh.aminov.golda

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Repo
import sh.aminov.golda.domain.tr

/**
 * The shared shape of a settings scenario: fresh known data in the test application's isolated
 * storage, the real activity, and small helpers that drive the UI the way a person would.
 */
abstract class GoldaUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    protected lateinit var scenario: ActivityScenario<MainActivity>

    protected val app: TestGoldaApplication
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestGoldaApplication

    protected val repo: Repo get() = app.repo

    @Before
    fun start() {
        Emulator.require()
        runBlocking { seed(repo) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    @After
    fun stop() {
        if (::scenario.isInitialized) scenario.close()
    }

    protected fun openSettings() {
        compose.onNodeWithContentDescription(tr("Настройки", "Settings")).performClick()
        compose.waitForIdle()
    }

    /** A settings row by its title, scrolled into view first. */
    protected fun openRow(title: String) {
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** The system back key, as a person presses it. */
    protected fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    /** Types into the first text field on screen (the big number in a value sheet or a form). */
    protected fun typeIntoFirstField(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(text)
        compose.waitForIdle()
    }

    protected fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    protected fun textOf(tag: String): String =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().text()

    /** Waits until [text] is on screen (sheets and pages animate in). */
    protected fun waitForText(text: String, timeoutMs: Long = 5_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    protected fun closeEntry() {
        // First back hides the keyboard, the second closes the page.
        back()
        back()
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun emulatorOnly() = Emulator.require()

        /** Anything that toggles: the buttons of a choice group, the shown-currency chips, switches. */
        val toggle = SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)

        fun SemanticsNode.text(): String =
            config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
                ?: config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

        /**
         * Known data: onboarded, no daily limit, rubles, dollars and lari shown, rubles local, and one
         * reserve account per currency.
         */
        suspend fun seed(repo: Repo) {
            repo.resetAll()
            repo.settings.update {
                it.copy(
                    onboarded = true,
                    displayCurrencies = listOf("RUB", "USD", "GEL"),
                    localCurrency = "RUB",
                    markup = 0.10,
                    reconcileReminder = false,
                )
            }
            repo.saveAccount(Account(name = "Карта ₽", currency = "RUB", type = AccountType.CARD, includeInFree = true, sort = 0), 1_000_000)
            repo.saveAccount(Account(name = "Лари", currency = "GEL", type = AccountType.CASH, includeInFree = true, sort = 1), 50_000)
            repo.saveAccount(Account(name = "Доллары", currency = "USD", type = AccountType.CARD, includeInFree = true, sort = 2), 10_000)
        }
    }
}
