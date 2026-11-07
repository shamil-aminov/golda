package sh.aminov.golda

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
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
        val data = aheadOfPlan()
        shot("green", 0, data)
        compose.onNodeWithTag(Tags.HOME_SPENT).assertContentDescriptionEquals(
            "Осталось ${Fmt.whole(193_000, "GEL")} ₾, запас к плану ${Fmt.whole(data.envelope!!.marginMinor, "GEL")} ₾",
        )
    }

    private fun aheadOfPlan(): AppData {
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
        return data
    }

    private fun barShot(name: String, ahead: Boolean = false, difference: Boolean = false, motion: String = "QUIET", experiments: Boolean = true) {
        val demo = if (ahead) aheadOfPlan() else ScreenshotTest().demo()
        val data = AppData(demo.settings.copy(barMotion = motion, barDifferenceEmphasis = difference), demo.accounts, demo.operations, demo.categories, emptyList(), demo.obligations, demo.today)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalBarExperiments provides experiments) {
                GoldaTheme("golda", "LIGHT", "ROOMY") {
                    HomeScreen(data, androidx.compose.foundation.layout.PaddingValues(), rememberLazyListState(), {}, onEdit = {})
                }
            }
        }
        compose.onNodeWithTag(Tags.HOME_CARD).captureRoboImage("build/codex-batch1/$name.png")
    }

    @Test fun accentBehindCurrent() = barShot("accent_0")
    @Test fun accentBehindInverted() = barShot("accent_1", difference = true)
    @Test fun accentAheadCurrent() = barShot("accent_2", ahead = true)
    @Test fun accentAheadInverted() = barShot("accent_3", ahead = true, difference = true)
    @Test fun waveBehind() = barShot("wave_behind", motion = "WAVE", difference = true)
    @Test fun waveAhead() = barShot("wave_ahead", ahead = true, motion = "WAVE", difference = true)
    @Test fun shimmerBehind() = barShot("shimmer_behind", motion = "SHIMMER", difference = true)
    @Test fun shimmerAhead() = barShot("shimmer_ahead", ahead = true, motion = "SHIMMER", difference = true)
    @Test fun productionIgnoresExperiments() = barShot("bar_production", motion = "SHIMMER", difference = true, experiments = false)

    private fun setAnimatorScale(value: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        val scale = android.provider.Settings.Global.ANIMATOR_DURATION_SCALE
        android.provider.Settings.Global.putFloat(resolver, scale, value)
        // ShadowSettings stores the value but does not send the real SettingsProvider notification.
        resolver.notifyChange(android.provider.Settings.Global.getUriFor(scale), null)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test fun reducedMotionChangesWhileHomeIsVisible() {
        setAnimatorScale(1f)
        try {
            barShot("bar_motion_enabled", motion = "SHIMMER", difference = true)
            compose.runOnIdle { setAnimatorScale(0f) }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag(Tags.HOME_CARD).captureRoboImage("build/codex-batch1/bar_motion_disabled.png")
        } finally {
            setAnimatorScale(1f)
        }
    }

    @Test fun themeOffersBothDevChoices() {
        val motion = mutableStateOf("QUIET")
        val difference = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalBarExperiments provides true) {
                GoldaTheme("golda", "LIGHT", "ROOMY") {
                    ThemeScreen("golda", "LIGHT", emptyList(), {}, {}, {}, {}, {},
                        barMotion = motion.value, onBarMotion = { motion.value = it },
                        barDifferenceEmphasis = difference.value, onBarDifferenceEmphasis = { difference.value = it })
                }
            }
        }
        compose.onNodeWithText("Для разработки").assertIsDisplayed()
        compose.onNodeWithText("Полоса").performClick()
        compose.onNodeWithText("Волна").performClick()
        compose.onNodeWithText("Волна").assertIsDisplayed()
        assertEquals("WAVE", motion.value)
        compose.onNodeWithText("Акцент").performClick()
        compose.onNodeWithText("Разница").performClick()
        compose.onNodeWithText("Разница").assertIsDisplayed()
        assertEquals(true, difference.value)
    }

    @Test fun themeHidesDevChoicesByDefault() {
        compose.setContent {
            GoldaTheme {
                ThemeScreen("golda", "LIGHT", emptyList(), {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Для разработки").assertDoesNotExist()
        compose.onNodeWithText("Полоса").assertDoesNotExist()
        compose.onNodeWithText("Акцент").assertDoesNotExist()
    }

    @Test fun motionOnlyTouchesTheGapAndStopsWithTheSystemSetting() {
        val demo = mutableStateOf(ScreenshotTest().demo())
        val motion = mutableStateOf("QUIET")
        val difference = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        setAnimatorScale(1f)
        try {
            compose.setContent {
                CompositionLocalProvider(LocalBarExperiments provides true) {
                    GoldaTheme("golda", "LIGHT", "ROOMY") {
                        val source = demo.value
                        val data = AppData(source.settings.copy(barMotion = motion.value, barDifferenceEmphasis = difference.value),
                            source.accounts, source.operations, source.categories, emptyList(), source.obligations, source.today)
                        HomeScreen(data, androidx.compose.foundation.layout.PaddingValues(), rememberLazyListState(), {}, onEdit = {})
                    }
                }
            }
            for (source in listOf(ScreenshotTest().demo(), aheadOfPlan())) {
                for (emphasis in listOf(false, true)) {
                    compose.runOnIdle { demo.value = source; difference.value = emphasis; motion.value = "QUIET" }
                    compose.mainClock.advanceTimeByFrame()
                    val quiet = compose.onNodeWithTag(Tags.HOME_SPENT).captureToImage().toPixelMap()
                    for (style in listOf("WAVE", "SHIMMER")) {
                        compose.runOnIdle { motion.value = style }
                        compose.mainClock.advanceTimeBy(300)
                        val moving = compose.onNodeWithTag(Tags.HOME_SPENT).captureToImage().toPixelMap()
                        val start = quiet.width * source.envelope!!.solidLeftShare
                        val end = quiet.width * source.envelope!!.ghostLeftShare
                        var changed = 0
                        for (x in 0 until quiet.width) {
                            for (y in 0 until quiet.height) {
                                if (quiet[x, y] != moving[x, y]) {
                                    assertTrue("$style changed a pixel outside the gap at $x,$y", x >= start.toInt() && x <= end.toInt())
                                    changed++
                                }
                            }
                        }
                        assertTrue("$style must draw a visible effect", changed > 0)
                        compose.runOnIdle { setAnimatorScale(0f) }
                        compose.mainClock.advanceTimeByFrame()
                        val stopped = compose.onNodeWithTag(Tags.HOME_SPENT).captureToImage().toPixelMap()
                        for (x in 0 until quiet.width) {
                            for (y in 0 until quiet.height) assertEquals("$style must become Quiet at $x,$y", quiet[x, y], stopped[x, y])
                        }
                        compose.runOnIdle { setAnimatorScale(1f) }
                        compose.mainClock.advanceTimeByFrame()
                    }
                }
            }
        } finally {
            setAnimatorScale(1f)
        }
    }
}
