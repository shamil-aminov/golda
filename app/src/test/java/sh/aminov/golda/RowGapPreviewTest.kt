package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.AccountsScreen
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HomeScreen
import sh.aminov.golda.ui.LocalRowGap
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/screen-rhythm.md`): Home and Accounts at «Просторно» with the row-to-row gap
 * four ways, into `docs/tasks/screen-rhythm/rowgap-*`. The 48 circle stands about 6 over and under a row's
 * letters (36), so circle to circle is the letters' gap less 12: r1 and r2 set the circles' gap to 14 and 21.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1300dp-xxhdpi")
class RowGapPreviewTest(private val name: String) {
    companion object {
        /** The letters' gap at «Просторно» for each: r0 as now, r1 circles 14, r2 circles 21, r3 letters 32. */
        val gaps = mapOf("r0" to 21f, "r1" to 26.2f, "r2" to 33.2f, "r3" to 32f)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = gaps.keys.flatMap { g -> listOf("home", "accounts").map { arrayOf<Any>("${g}_$it") } }
    }

    @get:Rule val compose = createComposeRule()

    @Test fun shot() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC")); I18n.russian = true
        val data = ScreenshotTest().demo()
        val gap = gaps.getValue(name.substringBefore('_')).dp
        compose.setContent {
            GoldaTheme("golda", "LIGHT", "ROOMY") {
                CompositionLocalProvider(LocalRowGap provides gap) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        if (name.endsWith("home")) HomeScreen(data, PaddingValues(), rememberLazyListState(), {}, {}, onEdit = {})
                        else AccountsScreen(data, PaddingValues(), {}, {}, { _, _, _ -> })
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/tasks/screen-rhythm/rowgap-$name.png")
    }
}
