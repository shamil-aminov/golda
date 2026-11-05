package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
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

/**
 * The second level of Settings and the Theme page, as ScreenshotTest draws the tabs: Golda light,
 * «Просторно», tall enough for the whole page. Stored in `app/src/test/screenshots/settings`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1300dp-xxhdpi")
class SettingsPagesShots {
    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f))

    /** One composition for every frame: compose takes setContent only once a test. */
    @Test
    fun everyPage() {
        val data = ScreenshotTest().demo()
        @Composable
        fun settings(section: SettingsSection?) =
            SettingsScreen(data, {}, {}, remember { SnackbarHostState() }, {}, {}, {}, {}, {}, section = section)
        val pages: List<Pair<String, @Composable () -> Unit>> = listOf<Pair<String, @Composable () -> Unit>>("contents" to { settings(null) }) +
            SettingsSection.entries.map { section -> section.name.lowercase() to @Composable { settings(section) } } +
            listOf<Pair<String, @Composable () -> Unit>>(
                "theme" to { ThemeScreen("golda", "LIGHT", emptyList(), {}, {}, {}, {}, {}, spacing = "ROOMY") },
                "theme_own" to { OwnThemeScreen("golda", "LIGHT", emptyList(), {}, {}, {}, {}) },
            )
        var current by mutableIntStateOf(0)
        compose.setContent {
            GoldaTheme("golda", "LIGHT", "ROOMY") {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { pages[current].second() }
            }
        }
        pages.forEachIndexed { i, (name, _) ->
            current = i
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("src/test/screenshots/settings/$name.png", roborazziOptions = options)
        }
    }
}
