package sh.aminov.golda

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertContentDescriptionEquals
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
import sh.aminov.golda.domain.Fmt
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

    @Test fun finalHome() {
        val data = ScreenshotTest().demo()
        shot("home", 0, data)
        val envelope = data.envelope!!
        compose.onNodeWithTag(Tags.HOME_SPENT).assertContentDescriptionEquals(
            "Осталось ${Fmt.whole(envelope.balanceMinor, "GEL")} ₾, по плану должно остаться ${Fmt.whole(envelope.plannedLeftMinor, "GEL")} ₾",
        )
    }
    @Test fun finalInsights() = shot("insights", 1)
    @Test fun finalAccounts() = shot("accounts", 2)

    @Test fun finalHomeWithinPlan() {
        val demo = ScreenshotTest().demo()
        val envelope = demo.envelope!!
        // Keep the period's funds and today's spending. Reduce two earlier expenses to leave 1 930 GEL.
        val rent = demo.operations.first { it.op.type == OpType.EXPENSE && it.op.note == "Аренда" }
        val food = demo.operations.first { it.op.type == OpType.EXPENSE && it.op.note == "Продукты" }
        val reductions = mapOf(rent.op.id to 85_000L, food.op.id to 5_410L)
        val operations = demo.operations.map { full ->
            val reduction = reductions[full.op.id]
            if (reduction == null) full else full.copy(
                op = full.op.copy(purchaseAmountMinor = full.op.purchaseAmountMinor?.minus(reduction)),
                postings = full.postings.map { posting ->
                    posting.copy(amountMinor = posting.amountMinor + reduction,
                        rubMinor = posting.rubMinor + demo.rates.rubMinor(reduction, envelope.currency)!!)
                },
            )
        }
        val data = AppData(demo.settings, demo.accounts, operations, demo.categories, emptyList(), demo.obligations, demo.today)
        assertEquals(envelope.fundsMinor, data.envelope!!.fundsMinor)
        assertEquals(Light.GREEN, data.envelope!!.light)
        assertEquals(193_000L, data.envelope!!.balanceMinor)
        shot("green", 0, data)
        compose.onNodeWithTag(Tags.HOME_SPENT).assertContentDescriptionEquals(
            "Осталось ${Fmt.whole(193_000, "GEL")} ₾, запас к плану ${Fmt.whole(data.envelope!!.marginMinor, "GEL")} ₾",
        )
    }
}
