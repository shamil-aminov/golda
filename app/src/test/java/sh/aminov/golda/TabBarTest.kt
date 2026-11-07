package sh.aminov.golda

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.EntryBar
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.TabBar
import sh.aminov.golda.ui.VoiceControl
import sh.aminov.golda.ui.VoiceState
import sh.aminov.golda.ui.HomeScreen
import sh.aminov.golda.ui.ListSpace
import sh.aminov.golda.ui.Tags
import androidx.compose.ui.test.onNodeWithTag

/** The tabs on top and the ways of writing down at the bottom. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TabBarTest {
    @get:Rule val compose = createComposeRule()

    @Before fun russian() { I18n.russian = true }

    @Test
    fun aWordOpensItsTabThePersonTheSettingsAndTheWordFollowsThePosition() {
        var picked = -1
        var settings = 0
        compose.setContent { GoldaTheme("golda", "LIGHT", "ROOMY") { TabBar(listOf("Главная", "Аналитика", "Счета"), { 0.8f }, { picked = it }, { settings++ }) } }
        compose.onNodeWithText("Аналитика").assertIsSelected()
        compose.onNodeWithText("Главная").assertIsNotSelected()
        compose.onNodeWithText("Счета").performClick()
        assertEquals(2, picked)
        compose.onNodeWithContentDescription("Настройки").performClick()
        assertEquals(1, settings)
    }

    @Test
    fun pillsKeepTheCardEdgesBaselinesAndCardGapAtEveryDensityAndSelection() {
        val spacing = mutableStateOf("ROOMY")
        val position = mutableStateOf(0f)
        val english = mutableStateOf(false)
        val data = ScreenshotTest().demo()
        var scale = 0f
        var gap = 0f
        compose.setContent {
            GoldaTheme("golda", "LIGHT", spacing.value) {
                scale = LocalDensity.current.density
                gap = with(LocalDensity.current) { ListSpace.cards.toPx() }
                Scaffold(topBar = {
                    TabBar(if (english.value) listOf("Home", "Insights", "Accounts") else listOf("Главная", "Аналитика", "Счета"),
                        { position.value }, {}, {})
                }) { padding -> HomeScreen(data, padding, rememberLazyListState(), {}, onEdit = {}) }
            }
        }
        listOf("COMPACT", "NORMAL", "ROOMY").forEach { density ->
            listOf(false, true).forEach { en ->
                (0..2).forEach { selected ->
                    spacing.value = density
                    english.value = en
                    position.value = selected.toFloat()
                    compose.waitForIdle()
                    val words = if (en) listOf("Home", "Insights", "Accounts") else listOf("Главная", "Аналитика", "Счета")
                    val tabs = words.map { compose.onNodeWithText(it) }
                    val bounds = tabs.map { it.fetchSemanticsNode().boundsInRoot }
                    assertEquals(8 * scale, bounds.first().left, 1f)
                    bounds.forEach { assertEquals(48 * scale, it.height, 1f) }
                    val gear = compose.onNodeWithContentDescription("Настройки").fetchSemanticsNode().boundsInRoot
                    assertEquals(411 * scale - 8 * scale, gear.right, 1f)
                    assertTrue(bounds.last().right <= gear.left)
                    val baselines = tabs.mapIndexed { index, node ->
                        val layouts = mutableListOf<TextLayoutResult>()
                        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        val textBounds = compose.onNodeWithText(words[index], useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                        textBounds.top + layouts.single().firstBaseline
                    }
                    baselines.forEach { assertEquals(baselines.first(), it, 1f) }
                    val card = compose.onNodeWithTag(Tags.HOME_CARD).fetchSemanticsNode().boundsInRoot
                    assertEquals(gap, card.top - bounds.first().bottom, 1f)
                    assertEquals(card.left, bounds.first().left, 1f)
                    assertEquals(card.right, gear.right, 1f)
                }
            }
        }
    }

    @Test
    fun theBarHasTheMicThePlusAndTheCameraWhereThereIsOne() {
        var said = 0
        var added = 0
        compose.setContent { GoldaTheme("golda", "LIGHT", "ROOMY") { EntryBar(VoiceControl(VoiceState.Idle, { said++ }), { added++ }) } }
        compose.onNodeWithContentDescription("Сказать").performClick()
        compose.onNodeWithContentDescription("Записать вручную").performClick()
        assertEquals(1, said)
        assertEquals(1, added)
        compose.onAllNodesWithContentDescription("Снять чек").assertCountEquals(0)
    }

    @Test
    fun recordingShowsCancelAndStopInPlaceOfTheWaysToWriteDown() {
        var cancelled = 0
        var stopped = 0
        compose.setContent { GoldaTheme("golda", "LIGHT", "ROOMY") { EntryBar(VoiceControl(VoiceState.Recording, { stopped++ }, camera = {}, cancel = { cancelled++ }), {}) } }
        compose.onAllNodesWithContentDescription("Снять чек").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Записать вручную").assertCountEquals(0)
        compose.onNodeWithContentDescription("Отменить запись").performClick()
        compose.onNodeWithContentDescription("Слушаю. Нажми, когда закончишь").performClick()
        assertEquals(1, cancelled)
        assertEquals(1, stopped)
    }

    @Test
    fun aPhoneWithACameraHasItsMark() {
        var taken = 0
        compose.setContent { GoldaTheme("golda", "LIGHT", "ROOMY") { EntryBar(VoiceControl(VoiceState.Idle, {}, camera = { taken++ }), {}) } }
        compose.onNodeWithContentDescription("Снять чек").performClick()
        assertEquals(1, taken)
    }
}
