package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.domain.Debts
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.BIG_FIGURE_SP
import sh.aminov.golda.ui.Gap
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HeroAmount
import sh.aminov.golda.ui.HeroTile
import sh.aminov.golda.ui.HomeScreen
import sh.aminov.golda.ui.Space
import sh.aminov.golda.ui.TextRole
import sh.aminov.golda.ui.VSpace
import sh.aminov.golda.ui.letters
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/home-type-spacing.md`): the Accounts header with its first block laid out
 * five ways against Home's, in Golda light at «Просторно», into `docs/tasks/home-type-spacing/plate-*`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h420dp-xxhdpi")
class AccountsPlatePreviewTest(private val variant: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = listOf("home", "now", "sh1", "sh2a", "sh2b", "sh3").map { arrayOf<Any>(it) }
    }

    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    @Test fun goldaLight() {
        val data = ScreenshotTest().demo()
        compose.setContent {
            GoldaTheme("golda", "LIGHT", "ROOMY") {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (variant == "home") HomeScreen(data, PaddingValues(), rememberLazyListState(), {}, {}, onEdit = {})
                    else Column(Modifier.padding(top = Gap.s)) { Plate(data, variant) }
                }
            }
        }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/home-type-spacing/plate-${variant}_golda_light.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }
}

/**
 * now: the figure, the other currencies and «Всего на счетах» a text apart each. sh1: the other
 * currencies a block under the figure, their caption a text under them. sh2a: the other currencies a
 * caption, on «Всего на счетах»'s line. sh2b: the same on a line of their own under it. sh3: «Всего на
 * счетах» a text under the figure, the other currencies a block under it, the interest a block under them.
 */
@Composable
private fun Plate(data: AppData, v: String) {
    val total = data.states.values.sumOf { it.rubMinor }
    val ink = MaterialTheme.colorScheme.onPrimaryFixed
    val figures = TextRole.headerFigures
    val (head, tail) = Debts.advice(data.accounts, data.states)!!.split("\n", limit = 2).let { it.first() to it.getOrNull(1).orEmpty() }
    val others = data.others(total, data.base.code)
    HeroTile(Modifier.padding(horizontal = Gap.s)) {
        val words = TextRole.caption
        HeroAmount(data.base.whole(total), ink, Modifier.letters(BIG_FIGURE_SP.sp, top = false), signColor = ink)
        when (v) {
            "now" -> {
                VSpace(Space.text); Text(others, style = figures, color = ink)
                VSpace(Space.text); Text("Всего на счетах", style = words)
            }
            "sh1" -> {
                VSpace(Space.block); Text(others, style = figures, color = ink)
                VSpace(Space.text); Text("Всего на счетах", style = words)
            }
            "sh2a" -> {
                VSpace(Space.text); Text("Всего на счетах · $others", style = words)
            }
            "sh2b" -> {
                VSpace(Space.text); Text("Всего на счетах", style = words)
                Text(others, style = words)
            }
            else -> {
                VSpace(Space.text); Text("Всего на счетах", style = words)
                VSpace(Space.block); Text(others, style = figures, color = ink)
            }
        }
        VSpace(Space.block)
        Text(tail, style = figures, color = ink)
        VSpace(Space.text)
        Text(head, style = words)
    }
}
