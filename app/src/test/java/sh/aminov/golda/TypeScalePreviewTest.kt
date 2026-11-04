package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.AccountsScreen
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.Gap
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.GroupHeader
import sh.aminov.golda.ui.GroupRow
import sh.aminov.golda.ui.HeroAmount
import sh.aminov.golda.ui.HeroTile
import sh.aminov.golda.ui.Tnum
import sh.aminov.golda.ui.VSpace
import sh.aminov.golda.ui.accountSubline
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/type-scale.md`): the Accounts tab in four sizes and two weights, three
 * ways (base/accent 400/600, 500/700, 500/600), against the screen as it is, into `docs/tasks/type-scale/`.
 * Inter is one variable file, so any weight can be drawn: here every weight under 600 becomes the base
 * and every one from 600 the accent. Delete once one is built in.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi")
class TypeScalePreviewTest(private val variant: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants() = listOf("now", "a", "b", "c").map { arrayOf<Any>(it) }
    }

    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private val weights = mapOf("a" to (400 to 600), "b" to (500 to 700), "c" to (500 to 600))

    private fun shot(theme: String, palette: String, mode: String) {
        val data = ScreenshotTest().demo()
        compose.setContent {
            GoldaTheme(palette, mode) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    val w = weights[variant]
                    if (w == null) {
                        AccountsScreen(data, PaddingValues(), {}, {}, { _, _, _ -> })
                    } else {
                        MaterialTheme(typography = MaterialTheme.typography.twoWeights(family(w.first, w.second))) { Mock(data) }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/type-scale/${variant}_$theme.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }

    @Test fun marathonDark() = shot("marathon_dark", "marathon", "DARK")
    @Test fun goldaLight() = shot("golda_light", "golda", "LIGHT")
}

/** Inter where a weight under 600 draws as [base] and one from 600 as [accent]: two weights, whatever the code asks. */
@OptIn(ExperimentalTextApi::class)
private fun family(base: Int, accent: Int) = FontFamily(
    (100..900 step 100).map { asked ->
        val drawn = if (asked < 600) base else accent
        Font(R.font.inter, FontWeight(asked), variationSettings = FontVariation.Settings(FontVariation.weight(drawn)))
    },
)

private fun TextStyle.on(family: FontFamily) = copy(fontFamily = family)

private fun Typography.twoWeights(family: FontFamily) = copy(
    displayLargeEmphasized = displayLargeEmphasized.on(family),
    titleLarge = titleLarge.on(family),
    bodyLarge = bodyLarge.on(family),
    bodyMedium = bodyMedium.on(family),
    labelLarge = labelLarge.on(family),
    titleMedium = titleMedium.on(family),
)

/**
 * The tab by four sizes and two weights. 14 base: every word beside a figure and every second line;
 * 16 base: a row's name; 22 base: a row's amount; 22 accent: a group's title and the header's figures;
 * the big figure in the base. The regular payments are rows like the accounts.
 */
@Composable
private fun Mock(data: AppData) {
    val type = MaterialTheme.typography
    val words = type.bodyMedium.merge(Tnum).copy(fontWeight = FontWeight.Normal)
    val name = type.bodyLarge.copy(fontWeight = FontWeight.Normal)
    val amount = type.titleLarge.merge(Tnum).copy(fontWeight = FontWeight.Normal)
    val accent = type.titleLarge.merge(Tnum).copy(fontWeight = FontWeight.SemiBold)
    val ink = MaterialTheme.colorScheme.onPrimaryFixed
    val total = data.states.values.sumOf { it.rubMinor }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Gap.s, bottom = Gap.xl)) {
        item {
            HeroTile(Modifier.padding(horizontal = Gap.s)) {
                Text("Всего на счетах", style = words, color = ink, maxLines = 1)
                VSpace(Gap.s)
                HeroAmount(data.base.whole(total), ink, signColor = ink)
                VSpace(Gap.s)
                Text(data.others(total, data.base.code), style = accent, color = ink)
                Debts.advice(data.accounts, data.states)?.let { advice ->
                    VSpace(Gap.m)
                    val (head, tail) = advice.split("\n", limit = 2).let { it.first() to it.getOrNull(1) }
                    Text(head, style = words, color = ink)
                    tail?.let { Text(it, style = accent, color = ink) }
                }
            }
        }
        item { GroupHeader("Счета", "Добавить счёт", {}, Modifier.padding(horizontal = Gap.m).padding(top = Gap.xl)) }
        data.accounts.forEachIndexed { i, a ->
            item {
                val s = data.states.getValue(a.id)
                Line(a.name, accountSubline(data, s), Fmt.amount(s.balanceMinor, s.currency), i + 1, data.accounts.size + 1, name, words, amount)
            }
        }
        item { GroupHeader("Регулярные платежи", "Добавить платёж", {}, Modifier.padding(horizontal = Gap.m).padding(top = Gap.xl)) }
        data.obligations.forEachIndexed { i, p ->
            item { Line(p.name, "${p.dayOfMonth} числа", Fmt.amount(p.amountMinor, p.currency), i + 1, data.obligations.size + 1, name, words, amount) }
        }
    }
}

@Composable
private fun Line(title: String, sub: String, money: String, i: Int, count: Int, name: TextStyle, words: TextStyle, amount: TextStyle) = GroupRow(
    i, count, Modifier.padding(horizontal = Gap.m), onClick = {},
    supporting = { Text(sub, style = words, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    trailing = { Text(money, style = amount, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) },
) { Text(title, style = name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
