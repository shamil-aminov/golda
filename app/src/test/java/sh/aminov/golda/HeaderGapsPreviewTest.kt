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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
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
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.Debts
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.label
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.BIG_FIGURE_SP
import sh.aminov.golda.ui.CardTitle
import sh.aminov.golda.ui.Gap
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HeroAmount
import sh.aminov.golda.ui.HeroTile
import sh.aminov.golda.ui.LocalSpacing
import sh.aminov.golda.ui.LocalTones
import sh.aminov.golda.ui.OperationRow
import sh.aminov.golda.ui.Pace
import sh.aminov.golda.ui.PeriodBar
import sh.aminov.golda.ui.Space
import sh.aminov.golda.ui.TextRole
import sh.aminov.golda.ui.Tnum
import sh.aminov.golda.ui.bleed
import sh.aminov.golda.ui.contrast
import sh.aminov.golda.ui.dayLabel
import sh.aminov.golda.ui.dayMonth
import sh.aminov.golda.ui.groupShape
import sh.aminov.golda.ui.inkOn
import sh.aminov.golda.ui.letters
import sh.aminov.golda.ui.overhang
import sh.aminov.golda.ui.operationColor
import sh.aminov.golda.ui.operationIcon
import sh.aminov.golda.ui.operationLine
import sh.aminov.golda.ui.paceColor
import sh.aminov.golda.ui.tileColor
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/home-type-spacing.md`): the Accounts and Home headers with the text gaps
 * five ways (as at d73381f by the boxes, only the big figure cut at its baseline, from the letters at 6/8, a step up at 10/12, and by the size of
 * the text above), and the day cards three ways (as now, the right edge aligned, as the bank does it), in
 * Golda light and Marathon dark at «Просторно», into `docs/tasks/home-type-spacing/gaps-*`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi")
class HeaderGapsPreviewTest(private val variant: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = listOf("before", "now", "up", "prop", "mix", "feed-now", "feed-aligned", "feed-bank").map { arrayOf<Any>(it) }
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
            GoldaTheme(palette, mode, "ROOMY") {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (variant.startsWith("feed")) Feed(data, variant)
                    else Column(Modifier.padding(top = Gap.s)) {
                        AccountsHeader(data, variant)
                        Spacer(Modifier.height(Space.cards))
                        HomeHeader(data, variant, null)
                        Spacer(Modifier.height(Space.cards))
                        // A long figure by the gear: it must not run under it.
                        HomeHeader(data, variant, 1_234_00)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/home-type-spacing/gaps-${variant}_$theme.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }

    @Test fun goldaLight() = shot("golda_light", "golda", "LIGHT")
    @Test fun marathonDark() = shot("marathon_dark", "marathon", "DARK")
}

private fun whole(minor: Long, code: String) = Fmt.whole(minor, code) + " " + Currencies.symbol(code)

/** The big figure's size as HeroAmount works it out for [text] in [width]. */
@Composable
private fun figureSize(text: String, width: Dp): TextUnit =
    minOf(with(LocalDensity.current) { (width * 0.94f / (text.length * 0.6f)).toSp().value }, BIG_FIGURE_SP).sp

/**
 * The gap under a text of [size]: before and now — the text level (by boxes, or from the letters);
 * up — the next step of the row (10, «Просторно» 12); prop — the capital's height over φ² (0.727 / 2.618 of
 * the size), times the density: about 26 under 94, 6 under 22, 4 under 14.
 */
@Composable
private fun under(v: String, size: TextUnit): Dp = when (v) {
    "up" -> Space.half
    "prop" -> (size.value * 0.727f / 2.618f * LocalSpacing.current.factor).dp
    else -> Space.text
}

@Composable
private fun Modifier.cut(v: String, size: TextUnit, top: Boolean = true) = when {
    v == "before" -> this
    // mix: only the big figure is cut (at its baseline); every other text keeps its box, as before.
    v == "mix" -> if (size.value >= BIG_FIGURE_SP && !top) this.letters(size, top) else this
    else -> this.letters(size, top)
}

@Composable
private fun AccountsHeader(data: AppData, v: String) {
    val total = data.states.values.sumOf { it.rubMinor }
    val ink = MaterialTheme.colorScheme.onPrimaryFixed
    val words = TextRole.caption
    val figures = TextRole.headerFigures
    val (head, tail) = Debts.advice(data.accounts, data.states)!!.split("\n", limit = 2).let { it.first() to it.getOrNull(1).orEmpty() }
    val text = data.base.whole(total)
    HeroTile(Modifier.padding(horizontal = Gap.s)) {
        BoxWithConstraints {
            val big = figureSize(text, maxWidth)
            Column {
                HeroAmount(text, ink, Modifier.cut(v, BIG_FIGURE_SP.sp, top = false), signColor = ink)
                Spacer(Modifier.height(under(v, big)))
                Text(data.others(total, data.base.code), Modifier.cut(v, figures.fontSize), style = figures, color = ink)
                Spacer(Modifier.height(under(v, figures.fontSize)))
                Text("Всего на счетах", Modifier.cut(v, words.fontSize), style = words)
                Spacer(Modifier.height(Space.block))
                Text(tail, Modifier.cut(v, figures.fontSize), style = figures, color = ink)
                Spacer(Modifier.height(under(v, figures.fontSize)))
                Text(head, Modifier.cut(v, words.fontSize), style = words)
            }
        }
    }
}

/** Home's header (В); in up and prop the gear stands on the figure's top line, its right edge on the content's. */
@Composable
private fun HomeHeader(data: AppData, v: String, left: Long?) {
    val e = data.envelope ?: return
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onPrimaryFixed
    val words = TextRole.caption
    val status = TextRole.headerFigures
    val code = e.currency
    val figure = whole(left ?: e.leftTodayMinor, code)
    val fixGear = v == "up" || v == "prop"
    HeroTile(Modifier.padding(horizontal = Gap.s), content = ink) {
        BoxWithConstraints {
            val big = figureSize(figure, maxWidth - 24.dp - Space.card)
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    // The figure cut at its capitals' top too, so the gear's top meets the digits' top.
                    Box(Modifier.weight(1f)) { HeroAmount(figure, ink, if (fixGear) Modifier.letters(big) else Modifier.cut(v, BIG_FIGURE_SP.sp, top = false), signColor = ink) }
                    if (fixGear) Spacer(Modifier.width(Space.card))
                    if (fixGear) {
                        Icon(painterResource(R.drawable.ic_settings), null, Modifier.size(24.dp), tint = scheme.onPrimaryFixedVariant)
                    } else {
                        // As the code has it: a 48 button hung half over the tile's top.
                        IconButton(onClick = {}, modifier = Modifier.overhang()) {
                            Icon(painterResource(R.drawable.ic_settings), null, tint = scheme.onPrimaryFixedVariant)
                        }
                    }
                }
                Spacer(Modifier.height(under(v, big)))
                Text("Можно сегодня", Modifier.cut(v, words.fontSize), style = words, maxLines = 1)
                Spacer(Modifier.height(Space.block))
                PeriodBar(e.timeShare, e.spentShare, paceColor(Pace.LASTS), paceColor(Pace.OVER))
                Spacer(Modifier.height(Space.text))
                Row(Modifier.fillMaxWidth().cut(v, words.fontSize)) {
                    Text(dayMonth(e.start), Modifier.weight(1f), style = words)
                    Text(dayMonth(e.end), style = words)
                }
                Spacer(Modifier.height(Space.block))
                val overInk = if (contrast(scheme.onErrorContainer, scheme.primaryFixed) >= 4.5) scheme.onErrorContainer else ink
                Text(
                    buildAnnotatedString {
                        append("Сверх нормы ")
                        withStyle(SpanStyle(color = overInk)) { append(whole(e.overNormMinor, code)) }
                    },
                    Modifier.cut(v, status.fontSize), style = status, color = ink, maxLines = 1,
                )
                Spacer(Modifier.height(under(v, status.fontSize)))
                val fact = e.burnMinor?.let { " · факт ${whole(it, code)}" }.orEmpty()
                Text("План ${whole(e.perDayMinor, code)} в день$fact", Modifier.cut(v, words.fontSize), style = words, maxLines = 1)
            }
        }
    }
}

/**
 * The feed's day cards. now: as the code has them (the day's sum 12 from the card's edge, the amounts 16).
 * aligned: the day's sum on the amounts' line. bank: the date alone at 22 in the accent, a 48 rounded
 * square for the icon, the second line the category or the kind, the amount on the name's baseline.
 */
@Composable
private fun Feed(data: AppData, v: String) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Gap.s, bottom = 40.dp)) {
        data.visibleOperations.groupBy { Ledger.localDate(it.op.timestamp, data.zone) }.forEach { (date, list) ->
            val spent = list.filter { it.op.type == OpType.EXPENSE }.sumOf { full -> -full.postings.sumOf { p -> p.rubMinor } }
            val total = spent.takeIf { it > 0 }?.let { data.dayTotal(it) }
            item {
                Spacer(Modifier.height(Space.cards))
                when (v) {
                    "feed-now" -> CardTitle(dayLabel(date, data.today), Modifier.padding(horizontal = Gap.m), quiet = true) {
                        total?.let { Text(it, style = TextRole.rowName.merge(Tnum), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                    }
                    else -> Surface(Modifier.padding(horizontal = Gap.m).bleed(Gap.s).fillMaxWidth(), shape = groupShape(0, 2), color = tileColor()) {
                        Row(Modifier.padding(start = Space.card, end = Space.card, top = Space.card, bottom = Space.half)) {
                            if (v == "feed-bank") {
                                Text(dayLabel(date, data.today), style = TextRole.groupTitle)
                            } else {
                                Text(dayLabel(date, data.today), Modifier.weight(1f).alignByBaseline(), style = TextRole.rowName.copy(fontWeight = TextRole.Accent))
                                total?.let { Text(it, Modifier.alignByBaseline(), style = TextRole.rowName.merge(Tnum), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
            list.forEachIndexed { i, op ->
                item {
                    if (v == "feed-bank") BankRow(data, op, i + 1, list.size + 1)
                    else OperationRow(data, op, null, {}, i + 1, list.size + 1, Modifier.padding(horizontal = Gap.m))
                }
            }
        }
    }
}

@Composable
private fun BankRow(data: AppData, full: OperationFull, i: Int, count: Int) {
    val line = operationLine(data, full, null)
    val category = full.op.categoryId?.let { data.categoryById[it] }
    val kind = when {
        line.transfer -> "Между своими счетами"
        full.op.type == OpType.INCOME -> category?.label() ?: "Доход"
        else -> category?.label() ?: "Без категории"
    }
    val came = line.secondary?.let { (if (line.approximate) "≈ " else "") + it }.orEmpty()
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(Modifier.padding(horizontal = Gap.m).bleed(Gap.s).fillMaxWidth(), shape = groupShape(i, count), color = tileColor()) {
        Row(
            Modifier.padding(start = Space.card, end = Space.card, top = Space.half, bottom = if (i == count - 1) Space.card else Space.half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fill = operationColor(category?.key, full.op.type, LocalTones.current)
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(fill), contentAlignment = Alignment.Center) {
                Icon(painterResource(operationIcon(category, full.op.type)), null, Modifier.size(24.dp), tint = inkOn(fill))
            }
            Spacer(Modifier.width(Space.card))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(line.title, Modifier.weight(1f).alignByBaseline(), style = TextRole.rowName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(Space.card))
                    Text(line.main, Modifier.alignByBaseline(), style = TextRole.rowName.merge(Tnum), maxLines = 1)
                }
                Spacer(Modifier.height(Space.text))
                Row {
                    Text(kind, Modifier.weight(1f).alignByBaseline(), style = TextRole.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(came, Modifier.alignByBaseline(), style = TextRole.caption, color = quiet, maxLines = 1)
                }
            }
        }
    }
}
