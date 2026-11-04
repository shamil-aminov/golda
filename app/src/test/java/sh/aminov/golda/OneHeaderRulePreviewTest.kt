package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.AccountsScreen
import sh.aminov.golda.ui.AnalyticsScreen
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HeaderRule
import sh.aminov.golda.ui.HomeScreen
import sh.aminov.golda.ui.LocalHeaderRule
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/one-header-rule.md`): Home, Accounts and Insights with every group's title
 * placed by one rule, four ways, at the default density, drawn tall into `docs/tasks/one-header-rule/`.
 * Delete, with `HeaderRule` in Kit.kt, once one is built in.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1500dp-xxhdpi")
class OneHeaderRulePreviewTest(private val rule: HeaderRule) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun rules() = HeaderRule.entries.map { arrayOf<Any>(it) }
    }

    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private val data by lazy { ScreenshotTest().demo() }
    private val letter get() = mapOf(HeaderRule.NOW to "a", HeaderRule.INSIDE to "b", HeaderRule.OUTSIDE to "c", HeaderRule.MINE to "d").getValue(rule)

    private fun shot(screen: String, dark: Boolean, content: @Composable () -> Unit) {
        val (palette, mode) = if (dark) "midnight" to "DARK" else "golda" to "SYSTEM"
        compose.setContent {
            GoldaTheme(palette, mode) {
                CompositionLocalProvider(LocalHeaderRule provides rule) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/one-header-rule/${letter}_${screen}_${if (dark) "dark" else "light"}.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }

    @Composable private fun home() = HomeScreen(data, PaddingValues(), rememberLazyListState(), {}, {}, onEdit = {})
    @Composable private fun accounts() = AccountsScreen(data, PaddingValues(), {}, {}, { _, _, _ -> })
    @Composable private fun insights() = AnalyticsScreen(data, PaddingValues()) {}

    @Test fun homeLight() = shot("home", false) { home() }
    @Test fun homeDark() = shot("home", true) { home() }
    @Test fun accountsLight() = shot("accounts", false) { accounts() }
    @Test fun accountsDark() = shot("accounts", true) { accounts() }
    @Test fun insightsLight() = shot("insights", false) { insights() }
    @Test fun insightsDark() = shot("insights", true) { insights() }
}
