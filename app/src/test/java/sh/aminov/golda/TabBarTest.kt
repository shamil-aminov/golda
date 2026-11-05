package sh.aminov.golda

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
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
import sh.aminov.golda.ui.EntryBar
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.MineScreen
import sh.aminov.golda.ui.TabBar
import sh.aminov.golda.ui.VoiceControl
import sh.aminov.golda.ui.VoiceState

/** The tabs on top, the ways of writing down at the bottom, and what opens behind the person. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TabBarTest {
    @get:Rule val compose = createComposeRule()

    @Before fun russian() { I18n.russian = true }

    @Test
    fun aWordOpensItsTabThePersonWhatIsYoursAndTheWordFollowsThePosition() {
        var picked = -1
        var mine = 0
        compose.setContent { GoldaTheme("golda", "LIGHT", "ROOMY") { TabBar(listOf("Сегодня", "Траты", "Счета"), { 0.8f }, { picked = it }, { mine++ }) } }
        compose.onNodeWithText("Траты").assertIsSelected()
        compose.onNodeWithText("Сегодня").assertIsNotSelected()
        compose.onNodeWithText("Счета").performClick()
        assertEquals(2, picked)
        compose.onNodeWithContentDescription("Моё").performClick()
        assertEquals(1, mine)
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
    fun aPhoneWithACameraHasItsMark() {
        var taken = 0
        compose.setContent { GoldaTheme("golda", "LIGHT", "ROOMY") { EntryBar(VoiceControl(VoiceState.Idle, {}, camera = { taken++ }), {}) } }
        compose.onNodeWithContentDescription("Снять чек").performClick()
        assertEquals(1, taken)
    }

    @Test
    fun mineLeadsToTheSettingsAndShowsNothingEmpty() {
        var settings = 0
        compose.setContent { GoldaTheme("golda", "LIGHT", "ROOMY") { MineScreen({}, { settings++ }) } }
        compose.onNodeWithText("Настройки").performClick()
        assertEquals(1, settings)
        compose.onAllNodesWithContentDescription("Семья").assertCountEquals(0)
    }
}
