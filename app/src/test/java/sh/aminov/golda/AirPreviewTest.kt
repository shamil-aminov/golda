package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
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
import sh.aminov.golda.ui.Air
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HomeScreen
import sh.aminov.golda.ui.LocalAir
import sh.aminov.golda.ui.SettingsScreen
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/air.md`): Accounts, Home and Settings with the rows and the group headers
 * given more air, three ways, drawn into `docs/tasks/air/` for the owner to pick from. Delete, with
 * `LocalAir` in Kit.kt, once the chosen air is built in.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AirPreviewTest(private val name: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun airs() = listOf("a", "b", "c").map { arrayOf<Any>(it) }
    }

    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private val data by lazy { ScreenshotTest().demo() }

    private val air = mapOf("a" to Air(), "b" to Air(row = 8.dp, underHeader = 8.dp), "c" to Air(row = 12.dp, underHeader = 16.dp)).getValue(name)

    private fun shot(screen: String, dark: Boolean, content: @Composable () -> Unit) {
        val (palette, mode) = if (dark) "midnight" to "DARK" else "golda" to "SYSTEM"
        compose.setContent {
            GoldaTheme(palette, mode) {
                CompositionLocalProvider(LocalAir provides air) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/air/${name}_${screen}_${if (dark) "dark" else "light"}.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }

    @Composable private fun accounts() = AccountsScreen(data, PaddingValues(), {}, {}, { _, _, _ -> })
    @Composable private fun home() = HomeScreen(data, PaddingValues(), rememberLazyListState(), {}, {}, onEdit = {})
    @Composable private fun settings() = SettingsScreen(data, {}, {}, remember { SnackbarHostState() }, {}, {}, {}, {}, {})

    @Test fun accountsLight() = shot("accounts", false) { accounts() }
    @Test fun accountsDark() = shot("accounts", true) { accounts() }
    @Test fun homeLight() = shot("home", false) { home() }
    @Test fun homeDark() = shot("home", true) { home() }
    @Test fun settingsLight() = shot("settings", false) { settings() }
    @Test fun settingsDark() = shot("settings", true) { settings() }
}
