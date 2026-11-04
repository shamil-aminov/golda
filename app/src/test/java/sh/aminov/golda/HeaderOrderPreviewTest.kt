package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import sh.aminov.golda.domain.Debts
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.Gap
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HeroAmount
import sh.aminov.golda.ui.HeroTile
import sh.aminov.golda.ui.Space
import sh.aminov.golda.ui.TextRole
import sh.aminov.golda.ui.VSpace
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/accounts-screen.md`): the Accounts header with its labels under their
 * figures, two ways against the header as it is, in Marathon and Midnight dark at «Обычно» and
 * «Просторно», into `docs/tasks/accounts-screen/order-*`. Delete once one is picked.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h420dp-xxhdpi")
class HeaderOrderPreviewTest(private val variant: String, private val spacing: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun cases() = listOf("now", "r1", "r2").flatMap { v -> listOf("NORMAL", "ROOMY").map { arrayOf<Any>(v, it) } }
    }

    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private fun shot(theme: String, palette: String) {
        val data = ScreenshotTest().demo()
        compose.setContent {
            GoldaTheme(palette, "DARK", spacing) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Column(Modifier.padding(top = Gap.s)) { Header(data, variant) }
                }
            }
        }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/accounts-screen/order-${variant}_${spacing.lowercase()}_$theme.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }

    @Test fun marathonDark() = shot("marathon_dark", "marathon")
    @Test fun midnightDark() = shot("midnight_dark", "midnight")
}

/**
 * now: each label over its figure. r1: each label under its figure, step by step (the total, its label;
 * the other currencies; the interest, its label). r2: the figures in one block and its label closing
 * it (the total, the other currencies, «Всего на счетах»), then the interest and its label.
 */
@Composable
private fun Header(data: AppData, v: String) {
    val total = data.states.values.sumOf { it.rubMinor }
    val ink = MaterialTheme.colorScheme.onPrimaryFixed
    val words = TextRole.caption
    val figures = TextRole.headerFigures
    val (head, tail) = Debts.advice(data.accounts, data.states)!!.split("\n", limit = 2).let { it.first() to it.getOrNull(1).orEmpty() }
    val others = data.others(total, data.base.code)
    HeroTile(Modifier.padding(horizontal = Gap.s)) {
        when (v) {
            "now" -> {
                Text("Всего на счетах", style = words, color = ink)
                VSpace(Space.text)
                HeroAmount(data.base.whole(total), ink, signColor = ink)
                VSpace(Space.text)
                Text(others, style = figures, color = ink)
                VSpace(Space.block)
                Text(head, style = words, color = ink)
                VSpace(Space.text)
                Text(tail, style = figures, color = ink)
            }
            "r1" -> {
                HeroAmount(data.base.whole(total), ink, signColor = ink)
                VSpace(Space.text)
                Text("Всего на счетах", style = words, color = ink)
                VSpace(Space.block)
                Text(others, style = figures, color = ink)
                VSpace(Space.block)
                Text(tail, style = figures, color = ink)
                VSpace(Space.text)
                Text(head, style = words, color = ink)
            }
            else -> {
                HeroAmount(data.base.whole(total), ink, signColor = ink)
                VSpace(Space.text)
                Text(others, style = figures, color = ink)
                VSpace(Space.text)
                Text("Всего на счетах", style = words, color = ink)
                VSpace(Space.block)
                Text(tail, style = figures, color = ink)
                VSpace(Space.text)
                Text(head, style = words, color = ink)
            }
        }
    }
}
