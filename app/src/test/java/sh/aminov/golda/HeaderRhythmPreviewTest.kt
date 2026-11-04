package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
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
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.Debts
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.AccountsScreen
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.Gap
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HeroAmount
import sh.aminov.golda.ui.HeroTile
import sh.aminov.golda.ui.HomeScreen
import sh.aminov.golda.ui.Pace
import sh.aminov.golda.ui.PeriodBar
import sh.aminov.golda.ui.TextRole
import sh.aminov.golda.ui.contrast
import sh.aminov.golda.ui.dayMonth
import sh.aminov.golda.ui.hang
import sh.aminov.golda.ui.heroFigureSize
import sh.aminov.golda.ui.letters
import sh.aminov.golda.ui.paceColor
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/home-type-spacing.md`): the Home and Accounts headers with exactly two optical
 * gaps — a caption and its value, block and block — every text cut to its letters, three pairs (and the
 * headers as they are), in Golda light at «Просторно» and «Обычно», into `docs/tasks/home-type-spacing/rhythm-*`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h900dp-xxhdpi")
class HeaderRhythmPreviewTest(private val variant: String, private val spacing: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun cases() = listOf("now", "p1", "p2", "p3").flatMap { v -> listOf("ROOMY", "NORMAL").map { arrayOf<Any>(v, it) } }

        /** (caption, block) at «Обычно» on the φ row; «Просторно» is ×1.25. */
        val pairs = mapOf("p1" to (8 to 21), "p2" to (13 to 21), "p3" to (13 to 34))
    }

    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    @Test fun goldaLight() {
        val data = ScreenshotTest().demo()
        val factor = if (spacing == "ROOMY") 1.25f else 1f
        val (t, b) = pairs[variant]?.let { (it.first * factor).dp to (it.second * factor).dp } ?: (0.dp to 0.dp)
        compose.setContent {
            GoldaTheme("golda", "LIGHT", spacing) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (variant == "now") {
                        Column {
                            Box(Modifier.height(380.dp)) { HomeScreen(data, PaddingValues(), rememberLazyListState(), {}, {}, onEdit = {}) }
                            Box(Modifier.height(330.dp)) { AccountsScreen(data, PaddingValues(), {}, {}, { _, _, _ -> }) }
                        }
                    } else {
                        Column(Modifier.padding(top = Gap.s)) {
                            Box(Modifier.height(372.dp)) { HomeHeader(data, t, b) }
                            Box(Modifier.height(330.dp)) { AccountsHeader(data, t, b) }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/home-type-spacing/rhythm-${variant}_${spacing.lowercase()}_golda_light.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }
}

private fun whole(minor: Long, code: String) = Fmt.whole(minor, code) + " " + Currencies.symbol(code)

@Composable
private fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
private fun HomeHeader(data: AppData, t: Dp, b: Dp) {
    val e = data.envelope ?: return
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onPrimaryFixed
    val code = e.currency
    val figure = whole(e.leftTodayMinor, code)
    HeroTile(Modifier.padding(horizontal = Gap.s), content = ink) {
        val words = TextRole.caption
        val status = TextRole.headerFigures
        Row {
            Text("Можно сегодня", Modifier.weight(1f).letters(words.fontSize), style = words, maxLines = 1)
            IconButton(onClick = {}, modifier = Modifier.hang()) {
                Icon(painterResource(R.drawable.ic_settings), null, tint = scheme.onPrimaryFixedVariant)
            }
        }
        Gap(t)
        BoxWithConstraints { HeroAmount(figure, ink, Modifier.letters(heroFigureSize(figure, maxWidth)), signColor = ink) }
        Gap(b)
        PeriodBar(e.timeShare, e.spentShare, paceColor(Pace.LASTS), paceColor(Pace.OVER))
        Gap(t)
        Row(Modifier.fillMaxWidth().letters(words.fontSize)) {
            Text(dayMonth(e.start), Modifier.weight(1f), style = words)
            Text(dayMonth(e.end), style = words)
        }
        Gap(b)
        val overInk = if (contrast(scheme.onErrorContainer, scheme.primaryFixed) >= 4.5) scheme.onErrorContainer else ink
        Text(
            buildAnnotatedString {
                append("Сверх нормы ")
                withStyle(SpanStyle(color = overInk)) { append(whole(e.overNormMinor, code)) }
            },
            Modifier.letters(status.fontSize), style = status, color = ink, maxLines = 1,
        )
        Gap(t)
        val fact = e.burnMinor?.let { " · факт ${whole(it, code)}" }.orEmpty()
        Text("План ${whole(e.perDayMinor, code)} в день$fact", Modifier.letters(words.fontSize), style = words, maxLines = 1)
    }
}

@Composable
private fun AccountsHeader(data: AppData, t: Dp, b: Dp) {
    val total = data.states.values.sumOf { it.rubMinor }
    val ink = MaterialTheme.colorScheme.onPrimaryFixed
    val (head, tail) = Debts.advice(data.accounts, data.states)!!.split("\n", limit = 2).let { it.first() to it.getOrNull(1).orEmpty() }
    val text = data.base.whole(total)
    HeroTile(Modifier.padding(horizontal = Gap.s)) {
        val words = TextRole.caption
        val figures = TextRole.headerFigures
        Text("Всего на счетах", Modifier.letters(words.fontSize), style = words)
        Gap(t)
        BoxWithConstraints { HeroAmount(text, ink, Modifier.letters(heroFigureSize(text, maxWidth)), signColor = ink) }
        Gap(b)
        Text(data.others(total, data.base.code), Modifier.letters(figures.fontSize), style = figures, color = ink)
        Gap(b)
        Text(head, Modifier.letters(words.fontSize), style = words)
        Gap(t)
        Text(tail, Modifier.letters(figures.fontSize), style = figures, color = ink)
    }
}
