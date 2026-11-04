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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
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
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.BIG_FIGURE_SP
import sh.aminov.golda.ui.Gap
import sh.aminov.golda.ui.GlyphCircle
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HeroAmount
import sh.aminov.golda.ui.HeroTile
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
import sh.aminov.golda.ui.operationColor
import sh.aminov.golda.ui.operationIcon
import sh.aminov.golda.ui.operationLine
import sh.aminov.golda.ui.paceColor
import sh.aminov.golda.ui.tileColor
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * TEMPORARY (task `docs/tasks/home-type-spacing.md`): В against В with the gaps taken from the ink, and
 * the feed's rows three ways in one build, plain and painted by level, at «Просторно», in Golda light and
 * Marathon dark, into `docs/tasks/home-type-spacing/fix-*`; and the Accounts header as now and from the ink.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi")
class HomeFixPreviewTest(private val variant: String, private val painted: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} painted={1}")
        fun cases() = listOf("c", "cfix", "l1", "l1top", "l2", "acc", "accfix").flatMap { v -> listOf(false, true).map { arrayOf<Any>(v, it) } }
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
                    if (variant.startsWith("acc")) Column(Modifier.padding(top = Gap.s)) { AccountsHeader(data, variant == "accfix", painted) }
                    else Mock(data, variant, painted)
                }
            }
        }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/home-type-spacing/fix-${variant}${if (painted) "_levels" else ""}_$theme.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }

    @Test fun goldaLight() = shot("golda_light", "golda", "LIGHT")
    @Test fun marathonDark() = shot("marathon_dark", "marathon", "DARK")
}

private val levelColors = listOf(Color(0xCCFF4D9D), Color(0xCCFF9F1C), Color(0xCC2EC4B6), Color(0xCC3A86FF))

@Composable
private fun Gap(height: Dp, level: Int, painted: Boolean) =
    Spacer(Modifier.fillMaxWidth().height(height).let { if (painted && height > 0.dp) it.background(levelColors[level]) else it })

/**
 * The text's box cut to its ink: from the cap's top (the cap is 0.727 of the size in Inter) to the last
 * baseline, so a gap above and under it is measured from the letters, not from the line's empty room.
 */
private fun Modifier.ink(size: TextUnit, top: Boolean = true) = layout { m, c ->
    val p = m.measure(c)
    val first = p[FirstBaseline]
    val last = p[LastBaseline]
    if (first == AlignmentLine.Unspecified || last == AlignmentLine.Unspecified) return@layout layout(p.width, p.height) { p.place(0, 0) }
    val cap = (size.toPx() * 0.727f).roundToInt()
    val from = if (top) (first - cap).coerceAtLeast(0) else 0
    layout(p.width, last - from) { p.place(0, -from) }
}

private fun whole(minor: Long, code: String) = Fmt.whole(minor, code) + " " + Currencies.symbol(code)

@Composable
private fun Mock(data: AppData, v: String, painted: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Gap.s, bottom = 40.dp)) {
        item { Hero(data, fix = v != "c", painted) }
        days(data, v, painted)
    }
}

/** В's header; [fix]: every gap from the ink (the figure cut at its baseline, the words at their cap and baseline). */
@Composable
private fun Hero(data: AppData, fix: Boolean, painted: Boolean) {
    val e = data.envelope ?: return
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onPrimaryFixed
    val words = TextRole.caption
    val status = TextRole.headerFigures
    val w = if (fix) Modifier.ink(words.fontSize) else Modifier
    val s = if (fix) Modifier.ink(status.fontSize) else Modifier
    val code = e.currency
    HeroTile(Modifier.padding(horizontal = Gap.s), content = ink) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) {
                HeroAmount(whole(e.leftTodayMinor, code), ink, if (fix) Modifier.ink(BIG_FIGURE_SP.sp, top = false) else Modifier, signColor = ink)
            }
            Icon(painterResource(R.drawable.ic_settings), null, Modifier.size(24.dp), tint = ink)
        }
        Gap(Space.text, 0, painted)
        Text("Можно сегодня", w, style = words, color = ink, maxLines = 1)
        Gap(Space.block, 1, painted)
        PeriodBar(e.timeShare, e.spentShare, paceColor(Pace.LASTS), paceColor(Pace.OVER))
        Gap(Space.text, 0, painted)
        Row(Modifier.fillMaxWidth().then(w)) {
            Text(dayMonth(e.start), Modifier.weight(1f), style = words, color = ink)
            Text(dayMonth(e.end), style = words, color = ink)
        }
        Gap(Space.block, 1, painted)
        val overInk = if (contrast(scheme.onErrorContainer, scheme.primaryFixed) >= 4.5) scheme.onErrorContainer else ink
        Text(
            buildAnnotatedString {
                append("Сверх нормы ")
                withStyle(SpanStyle(color = overInk)) { append(whole(e.overNormMinor, code)) }
            },
            s, style = status, color = ink, maxLines = 1,
        )
        Gap(Space.text, 0, painted)
        val fact = e.burnMinor?.let { " · факт ${whole(it, code)}" }.orEmpty()
        Text("План ${whole(e.perDayMinor, code)} в день$fact", w, style = words, color = ink, maxLines = 1)
    }
}

/** Accounts' header (Р2) as in the code now, and with the gaps from the ink. */
@Composable
private fun AccountsHeader(data: AppData, fix: Boolean, painted: Boolean) {
    val total = data.states.values.sumOf { it.rubMinor }
    val ink = MaterialTheme.colorScheme.onPrimaryFixed
    val words = TextRole.caption
    val figures = TextRole.headerFigures
    val w = if (fix) Modifier.ink(words.fontSize) else Modifier
    val f = if (fix) Modifier.ink(figures.fontSize) else Modifier
    val (head, tail) = Debts.advice(data.accounts, data.states)!!.split("\n", limit = 2).let { it.first() to it.getOrNull(1).orEmpty() }
    HeroTile(Modifier.padding(horizontal = Gap.s)) {
        // The total shrinks to fit: its size is the hero's own, here the 94 only bounds it.
        HeroAmount(data.base.whole(total), ink, if (fix) Modifier.ink(BIG_FIGURE_SP.sp, top = false) else Modifier, signColor = ink)
        Gap(Space.text, 0, painted)
        Text(data.others(total, data.base.code), f, style = figures, color = ink)
        Gap(Space.text, 0, painted)
        Text("Всего на счетах", w, style = words, color = ink)
        Gap(Space.block, 1, painted)
        Text(tail, f, style = figures, color = ink)
        Gap(Space.text, 0, painted)
        Text(head, w, style = words, color = ink)
    }
}

/**
 * The feed. c, cfix: the rows as В (the code's rows, amounts 16). l1: every row two lines on the left —
 * the account when there is no note, what a transfer came to after it — and one amount on the right, the
 * icon in the middle. l1top: as l1, the icon on the name's line. l2: two lines on both sides, the right
 * one's second line what it came to, or empty room.
 */
private fun LazyListScope.days(data: AppData, v: String, painted: Boolean) {
    data.visibleOperations.groupBy { Ledger.localDate(it.op.timestamp, data.zone) }.forEach { (date, list) ->
        val spent = list.filter { it.op.type == OpType.EXPENSE }.sumOf { full -> -full.postings.sumOf { p -> p.rubMinor } }
        val total = spent.takeIf { it > 0 }?.let { data.dayTotal(it) }
        item {
            Gap(Space.cards, 3, painted)
            DayTitle(dayLabel(date, data.today), total, painted)
        }
        list.forEachIndexed { i, op ->
            item {
                if (v == "c" || v == "cfix") Base { OperationRow(data, op, null, {}, i + 1, list.size + 1, Modifier.padding(horizontal = Gap.m)) }
                else FeedRow(data, op, i + 1, list.size + 1, v, painted)
            }
        }
    }
}

/** В's rows: the code's, the words in the base weight. */
@Composable
private fun Base(content: @Composable () -> Unit) {
    val t = MaterialTheme.typography
    MaterialTheme(typography = t.copy(bodyLarge = t.bodyLarge.copy(fontWeight = TextRole.Base), bodyMedium = t.bodyMedium.copy(fontWeight = TextRole.Base), titleMedium = t.titleMedium.copy(fontWeight = TextRole.Base))) { content() }
}

/** The day's date and its sum on one baseline, half a block over the first row. */
@Composable
private fun DayTitle(title: String, total: String?, painted: Boolean) {
    Surface(Modifier.padding(horizontal = Gap.m).bleed(Gap.s).fillMaxWidth(), shape = groupShape(0, 2), color = tileColor()) {
        Column(Modifier.padding(horizontal = Space.card)) {
            Gap(Space.card, 2, painted)
            Row {
                Text(title, Modifier.weight(1f).alignByBaseline(), style = TextRole.rowName.copy(fontWeight = TextRole.Accent))
                total?.let { Text(it, Modifier.alignByBaseline(), style = TextRole.rowName.merge(Tnum), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Gap(Space.half, 1, painted)
        }
    }
}

@Composable
private fun FeedRow(data: AppData, full: OperationFull, i: Int, count: Int, v: String, painted: Boolean) {
    val line = operationLine(data, full, null)
    val category = full.op.categoryId?.let { data.categoryById[it] }
    val out = full.postings.firstOrNull { it.amountMinor < 0 } ?: full.postings.first()
    val account = data.accountById[out.accountId]?.name
    val came = line.secondary?.let { (if (line.approximate) "≈ " else "") + it }
    val left = line.supporting ?: account.orEmpty()
    val sub = if (v == "l2") left else listOfNotNull(left.ifEmpty { null }, came).joinToString(" · ")
    val ink = if (line.transfer) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(Modifier.padding(horizontal = Gap.m).bleed(Gap.s).fillMaxWidth(), shape = groupShape(i, count), color = tileColor()) {
        Column(Modifier.padding(horizontal = Space.card)) {
            Gap(if (i == 0) Space.card else Space.half, if (i == 0) 2 else 1, painted)
            Row(verticalAlignment = if (v == "l1top") Alignment.Top else Alignment.CenterVertically) {
                val fill = operationColor(category?.key, full.op.type, LocalTones.current)
                GlyphCircle(operationIcon(category, full.op.type), color = fill, ink = inkOn(fill))
                Spacer(Modifier.width(Space.card))
                Column(Modifier.weight(1f)) {
                    Text(line.title, style = TextRole.rowName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Gap(Space.text, 0, painted)
                    Text(sub, style = TextRole.caption, color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(Space.card))
                Column(Modifier.align(Alignment.Top), horizontalAlignment = Alignment.End) {
                    Text(line.main, style = TextRole.rowName.merge(Tnum), color = ink, maxLines = 1)
                    if (v == "l2") {
                        // Not the full width: the column on the right is as wide as its figures.
                        Spacer(Modifier.width(24.dp).height(Space.text).let { if (painted) it.background(levelColors[0]) else it })
                        Text(came.orEmpty(), style = TextRole.caption, color = quiet, maxLines = 1)
                    }
                }
            }
            Gap(if (i == count - 1) Space.card else Space.half, if (i == count - 1) 2 else 1, painted)
        }
    }
}
