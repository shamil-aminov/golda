package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import sh.aminov.golda.ui.AnalyticsScreen
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HomeScreen
import sh.aminov.golda.ui.Palette
import sh.aminov.golda.ui.ThemeScreen
import java.util.TimeZone

/**
 * The bold themes as ScreenshotTest draws the calm ones: Home and Аналитика in both halves of each,
 * and the Theme page on pure black. Stored in `app/src/test/screenshots/bold`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BoldThemesShots {
    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f))

    /** One composition for every frame: compose takes setContent only once a test. */
    @Test
    fun everyBoldTheme() {
        val data = ScreenshotTest().demo()
        val bold = Palette.entries.filter { it.bold }
        var tones by mutableStateOf(bold.first().light)
        var home by mutableStateOf(true)
        compose.setContent {
            GoldaTheme(tones) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (home) HomeScreen(data, PaddingValues(), rememberLazyListState(), {}, {}, onEdit = {})
                    else AnalyticsScreen(data, PaddingValues()) {}
                }
            }
        }
        listOf(true, false).forEach { h ->
            bold.forEach { p ->
                listOf(false, true).forEach { dark ->
                    tones = p.tones(dark)
                    home = h
                    compose.waitForIdle()
                    val name = "${p.key}_${if (dark) "dark" else "light"}_${if (h) "home" else "insights"}"
                    compose.onRoot().captureRoboImage("src/test/screenshots/bold/$name.png", roborazziOptions = options)
                }
            }
        }
    }

    /** The Theme page in Марафон's dark half with Чистый чёрный on, tall enough for the bold chips. */
    @Test
    @Config(qualifiers = "w411dp-h1300dp-xxhdpi")
    fun themePageOnBlack() {
        compose.setContent {
            GoldaTheme(Palette.MARATHON.dark.onPage(Color.Black)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    ThemeScreen("marathon", "DARK", emptyList(), {}, {}, {}, {}, {}, oled = true)
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/bold/theme_oled.png", roborazziOptions = options)
    }
}
