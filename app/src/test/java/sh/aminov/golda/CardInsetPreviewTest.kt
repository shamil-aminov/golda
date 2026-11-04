package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.LocalCardInset
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/accounts-screen.md`): the Accounts tab with the cards' inner side room as
 * now, +4 and +8, in Marathon dark and Golda light, into `docs/tasks/accounts-screen/inset-*`. Delete,
 * with `LocalCardInset` in Kit.kt, once the owner has picked.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi")
class CardInsetPreviewTest(private val extra: Int) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "+{0}")
        fun extras() = listOf(0, 4, 8).map { arrayOf<Any>(it) }
    }

    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private fun shot(theme: String, palette: String, mode: String) {
        val data = ScreenshotTest().demo()
        compose.setContent {
            GoldaTheme(palette, mode) {
                CompositionLocalProvider(LocalCardInset provides extra.dp) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { AccountsScreen(data, PaddingValues(), {}, {}, { _, _, _ -> }) }
                }
            }
        }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/accounts-screen/inset-${extra}_$theme.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }

    @Test fun marathonDark() = shot("marathon_dark", "marathon", "DARK")
    @Test fun goldaLight() = shot("golda_light", "golda", "LIGHT")
}
