package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextOverflow
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
import sh.aminov.golda.domain.Debts
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.AccountsScreen
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.CardCorner
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HeroAmount
import sh.aminov.golda.ui.TextRole
import sh.aminov.golda.ui.accountSubline
import sh.aminov.golda.ui.tileColor
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/spacing.md`): the Accounts tab with every vertical gap set by its level,
 * as now and four ways (×2, the golden ratio, ×1.5, the Gap scale kept strictly), plain and with each gap painted in its level's
 * colour, into `docs/tasks/spacing/`. Delete once one is built in.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi")
class SpacingPreviewTest(private val variant: String, private val painted: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} painted={1}")
        fun variants() = listOf("now", "a", "b", "c", "d").flatMap { listOf(arrayOf<Any>(it, false), arrayOf<Any>(it, true)) }
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
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    // The real screen for "now", unpainted; its gaps copied into the mock to paint them.
                    if (variant == "now" && !painted) AccountsScreen(data, PaddingValues(), {}, {}, { _, _, _ -> })
                    else Mock(data, specs.getValue(variant), painted)
                }
            }
        }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/spacing/${variant}${if (painted) "_levels" else ""}_$theme.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }

    @Test fun marathonDark() = shot("marathon_dark", "marathon", "DARK")
    @Test fun goldaLight() = shot("golda_light", "golda", "LIGHT")
}

/**
 * Every vertical gap on the tab, by its level: 0 text from text in one block, 1 block from block in a
 * card (half of it over and under each row), 2 the card's own padding, 3 card from card.
 */
private class Spec(
    val text: Dp, val textHeroCaption: Dp, val textHeroOthers: Dp, val block: Dp, val heroBlock: Dp,
    val padTop: Dp, val padBottom: Dp, val heroPad: Dp, val cards: Dp,
)

private val specs = mapOf(
    // As the code has it now, at «Обычно»: rows 10 + 8 over and under (36 between two rows' texts).
    "now" to Spec(text = 0.dp, textHeroCaption = 8.dp, textHeroOthers = 8.dp, block = 36.dp, heroBlock = 16.dp, padTop = 12.dp, padBottom = 18.dp, heroPad = 24.dp, cards = 40.dp),
    // ×2: 4 · 8 · 16 · 32.
    "a" to Spec(text = 4.dp, textHeroCaption = 4.dp, textHeroOthers = 4.dp, block = 16.dp, heroBlock = 16.dp, padTop = 16.dp, padBottom = 16.dp, heroPad = 16.dp, cards = 32.dp),
    // The golden ratio from 16: 4 · 6 · 10 · 16 · 26 · 42; a block is 2 × 10.
    "b" to Spec(text = 6.dp, textHeroCaption = 6.dp, textHeroOthers = 6.dp, block = 20.dp, heroBlock = 20.dp, padTop = 16.dp, padBottom = 16.dp, heroPad = 16.dp, cards = 26.dp),
    // ×1.5 from 16: 5 · 7 · 11 · 16 · 24 · 36; a block is 2 × 11.
    "c" to Spec(text = 5.dp, textHeroCaption = 5.dp, textHeroOthers = 5.dp, block = 22.dp, heroBlock = 22.dp, padTop = 16.dp, padBottom = 16.dp, heroPad = 16.dp, cards = 24.dp),
    // The app's own Gap scale, kept strictly: 4 · 8 · 16 · 24 · 40.
    "d" to Spec(text = 4.dp, textHeroCaption = 4.dp, textHeroOthers = 4.dp, block = 24.dp, heroBlock = 24.dp, padTop = 16.dp, padBottom = 16.dp, heroPad = 16.dp, cards = 40.dp),
)

private val levelColors = listOf(Color(0xCCFF4D9D), Color(0xCCFF9F1C), Color(0xCC2EC4B6), Color(0xCC3A86FF))

/** A gap of [height] at [level]: painted in its colour when [painted]. */
@Composable
private fun Gap(height: Dp, level: Int, painted: Boolean) =
    Spacer(Modifier.fillMaxWidth().height(height).let { if (painted && height > 0.dp) it.background(levelColors[level]) else it })

@Composable
private fun Mock(data: AppData, s: Spec, painted: Boolean) {
    val edge = 8.dp
    val side = 16.dp
    val ink = MaterialTheme.colorScheme.onPrimaryFixed
    val total = data.states.values.sumOf { it.rubMinor }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = edge, bottom = 40.dp)) {
        item {
            Surface(Modifier.padding(horizontal = edge).fillMaxWidth(), shape = RoundedCornerShape(CardCorner), color = MaterialTheme.colorScheme.primaryFixed, contentColor = ink) {
                Column(Modifier.padding(horizontal = side)) {
                    Gap(s.heroPad, 2, painted)
                    Text("Всего на счетах", style = TextRole.caption, color = ink)
                    Gap(s.textHeroCaption, 0, painted)
                    HeroAmount(data.base.whole(total), ink, signColor = ink)
                    Gap(s.textHeroOthers, 0, painted)
                    Text(data.others(total, data.base.code), style = TextRole.headerFigures, color = ink)
                    Debts.advice(data.accounts, data.states)?.let { advice ->
                        Gap(s.heroBlock, 1, painted)
                        val (head, tail) = advice.split("\n", limit = 2).let { it.first() to it.getOrNull(1) }
                        Text(head, style = TextRole.caption, color = ink)
                        Gap(s.text, 0, painted)
                        tail?.let { Text(it, style = TextRole.headerFigures, color = ink) }
                    }
                    Gap(s.heroPad, 2, painted)
                }
            }
        }
        group("Счета", data.accounts.map { a ->
            val st = data.states.getValue(a.id)
            Triple(a.name, accountSubline(data, st), Fmt.amount(st.balanceMinor, st.currency))
        }, s, painted, edge, side)
        group("Регулярные платежи", data.obligations.map { Triple(it.name, "${it.dayOfMonth} числа", Fmt.amount(it.amountMinor, it.currency)) }, s, painted, edge, side)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.group(
    title: String, rows: List<Triple<String, String, String>>, s: Spec, painted: Boolean, edge: Dp, side: Dp,
) {
    item { Gap(s.cards, 3, painted) }
    item {
        Surface(Modifier.padding(horizontal = edge).fillMaxWidth(), shape = RoundedCornerShape(CardCorner), color = tileColor()) {
            Column {
                // The title and its "+" on one 32 line, so the card's padding is measured to that line's top.
                Box(Modifier.padding(horizontal = side)) { Gap(s.padTop, 2, painted) }
                Row(Modifier.padding(start = side, end = 12.dp).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = TextRole.groupTitle)
                    FilledTonalIconButton(onClick = {}, modifier = Modifier.size(32.dp)) { Icon(painterResource(R.drawable.ic_add), null, Modifier.size(20.dp)) }
                }
                rows.forEachIndexed { i, (name, sub, money) ->
                    // From the title, and from the row above: one block.
                    Box(Modifier.padding(horizontal = side)) { Gap(s.block, 1, painted) }
                    Row(Modifier.padding(horizontal = side), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = TextRole.rowName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Gap(s.text, 0, painted)
                            Text(sub, style = TextRole.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(money, style = TextRole.rowAmount, maxLines = 1)
                    }
                    if (i == rows.lastIndex) Box(Modifier.padding(horizontal = side)) { Gap(s.padBottom, 2, painted) }
                }
            }
        }
    }
}
