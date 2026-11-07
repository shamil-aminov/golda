package sh.aminov.golda

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.data.OpType
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Light
import sh.aminov.golda.ui.*
import java.util.TimeZone

/** Production screens for the final comparison frame. Earlier decision frames remain in docs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w432dp-h936dp-mdpi")
class CodexBatch1PreviewTest {
    @get:Rule val compose = createComposeRule()

    @Before fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private fun shot(name: String, tab: Int, data: AppData = ScreenshotTest().demo()) {
        compose.setContent {
            GoldaTheme("golda", "LIGHT", "ROOMY") {
                Scaffold(
                    topBar = { TabBar(listOf("Главная", "Аналитика", "Счета"), { tab.toFloat() }, {}, {}) },
                    floatingActionButtonPosition = FabPosition.Center,
                    floatingActionButton = { EntryBar(VoiceControl(VoiceState.Idle, {}, camera = {}), {}) },
                ) { padding ->
                    when (tab) {
                        0 -> HomeScreen(data, padding, rememberLazyListState(), {}, onEdit = {})
                        1 -> AnalyticsScreen(data, padding) {}
                        else -> AccountsScreen(data, padding, {}, {}, { _, _, _ -> })
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/codex-batch1/final_$name.png")
    }

    @Test fun finalHome() = shot("home", 0)
    @Test fun finalInsights() = shot("insights", 1)
    @Test fun finalAccounts() = shot("accounts", 2)

    @Test fun finalHomeWithinPlan() {
        val demo = ScreenshotTest().demo()
        val envelope = demo.envelope!!
        // Keep the period's funds and today's spending. Reduce the earlier one-off rent.
        val reduction = envelope.spentMinor - envelope.normMinor + envelope.fundsMinor / 10
        val rent = demo.operations.first { it.op.type == OpType.EXPENSE && it.op.note == "Аренда" }
        val operations = demo.operations.map { full ->
            if (full != rent) full else full.copy(postings = full.postings.map { posting ->
                posting.copy(amountMinor = posting.amountMinor + reduction,
                    rubMinor = posting.rubMinor + demo.rates.rubMinor(reduction, envelope.currency)!!)
            })
        }
        val data = AppData(demo.settings, demo.accounts, operations, demo.categories, emptyList(), demo.obligations, demo.today)
        assertEquals(envelope.fundsMinor, data.envelope!!.fundsMinor)
        assertEquals(Light.GREEN, data.envelope!!.light)
        shot("green", 0, data)
    }
}