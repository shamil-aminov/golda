package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import sh.aminov.golda.ui.actionColor
import sh.aminov.golda.ui.dayLabel
import sh.aminov.golda.ui.hang
import sh.aminov.golda.ui.onActionColor
import sh.aminov.golda.ui.onPickedColor
import sh.aminov.golda.ui.pickedColor
import sh.aminov.golda.ui.softShadow
import sh.aminov.golda.ui.tileInk
import sh.aminov.golda.ui.wholePercent
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import sh.aminov.golda.ui.bleed
import sh.aminov.golda.ui.dayMonth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.data.OpType
import sh.aminov.golda.domain.Analytics
import sh.aminov.golda.data.Bucket
import sh.aminov.golda.domain.Buckets
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.PeriodKind
import sh.aminov.golda.domain.Report
import sh.aminov.golda.domain.ReviewPrompt
import sh.aminov.golda.domain.label
import sh.aminov.golda.ui.AnalyticsScreen
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.CardCorner
import sh.aminov.golda.ui.CardTitle
import sh.aminov.golda.ui.ChoiceGroup
import sh.aminov.golda.ui.ChoiceText
import sh.aminov.golda.ui.CollapsingHeader
import sh.aminov.golda.ui.DayBars
import sh.aminov.golda.ui.DayPart
import sh.aminov.golda.ui.Gap
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.GoldaToolbar
import sh.aminov.golda.ui.GroupRow
import sh.aminov.golda.ui.HeaderSpace
import sh.aminov.golda.ui.HeroAmount
import sh.aminov.golda.ui.HeroTile
import sh.aminov.golda.ui.ListSpace
import sh.aminov.golda.ui.LocalTones
import sh.aminov.golda.ui.Mosaic
import sh.aminov.golda.ui.MosaicItem
import sh.aminov.golda.ui.OperationRow
import sh.aminov.golda.ui.RowIcon
import sh.aminov.golda.ui.TextRole
import sh.aminov.golda.ui.ToolbarTab
import sh.aminov.golda.ui.VSpace
import sh.aminov.golda.ui.VoiceControl
import sh.aminov.golda.ui.VoiceState
import sh.aminov.golda.ui.aboveActions
import sh.aminov.golda.ui.bucketLabel
import sh.aminov.golda.ui.categoryColor
import sh.aminov.golda.ui.categoryIcon
import sh.aminov.golda.ui.heroFigureSize
import sh.aminov.golda.ui.leitmotifColor
import sh.aminov.golda.ui.letters
import sh.aminov.golda.ui.iceColor
import sh.aminov.golda.ui.onIceColor
import sh.aminov.golda.ui.overhang
import sh.aminov.golda.ui.tileColor
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/analytics-contours.md`): Insights as it is, and its first contour two ways
 * with their second contours, in Golda light at «Просторно», into `docs/tasks/analytics-contours/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1700dp-xxhdpi")
class AnalyticsContoursPreviewTest(private val name: String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = listOf("now", "a1", "a2", "a3", "b1", "b2").map { arrayOf<Any>(it) }
    }

    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    @Test fun shot() {
        val data = ScreenshotTest().demo()
        compose.setContent {
            GoldaTheme("golda", "LIGHT", "ROOMY") {
                val island = name == "now" || name.endsWith("1")
                Scaffold(
                    floatingActionButton = { if (island) Island() },
                    floatingActionButtonPosition = FabPosition.Center,
                ) { padding ->
                    when (name) {
                        "now" -> AnalyticsScreen(data, padding) {}
                        "a1" -> FirstA(data, padding)
                        "a2" -> CategoriesPage(data)
                        "a3" -> CategoryPage(data)
                        "b1" -> FirstB(data, padding)
                        else -> DaysPage(data)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/tasks/analytics-contours/$name.png")
    }
}

@Composable
private fun Island() = GoldaToolbar(
    tabs = listOf(
        ToolbarTab(R.drawable.ic_home, R.drawable.ic_home_fill, "Главная"),
        ToolbarTab(R.drawable.ic_wallet, R.drawable.ic_wallet_fill, "Счета"),
        ToolbarTab(R.drawable.ic_chart, R.drawable.ic_chart_fill, "Аналитика"),
    ),
    selected = 2, position = { 2f }, onSelect = {}, voice = VoiceControl(VoiceState.Idle, {}), onAdd = {},
)

private class Ctx(val data: AppData, val report: Report, val title: String)

private fun ctx(data: AppData): Ctx {
    val period = Analytics.period(PeriodKind.MONTH, data.today)
    val report = Analytics.report(data.operations, data.accountById, data.categoryById, period, data.today, data.zone, data.rates)
    return Ctx(data, report, ReviewPrompt.title(period, data.today))
}

/** The header as Home's and Accounts': the caption over the figure, a block, a 22 value under its caption. */
@Composable
private fun Header(c: Ctx, buckets: Boolean) {
    val ink = MaterialTheme.colorScheme.onPrimaryFixed
    HeroTile(Modifier.padding(horizontal = Gap.s), vertical = HeaderSpace.edge) {
        val words = TextRole.caption
        val figures = TextRole.headerFigures
        // The caption as now ("За 30 дней"), and the way to Gemini's review on its line, as Home's gear.
        val recent = c.title.startsWith("последние ")
        Row(Modifier.letters(words.fontSize)) {
            Text(if (recent) "За " + c.title.substringAfter(' ') else "Траты за ${c.title}", Modifier.weight(1f), style = words)
            Button(
                onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = iceColor(), contentColor = onIceColor()),
                contentPadding = PaddingValues(horizontal = Gap.m), modifier = Modifier.overhang(0.dp).height(40.dp),
            ) { Text("Разбор", style = MaterialTheme.typography.labelLarge) }
        }
        VSpace(HeaderSpace.figureCaption)
        val figure = c.data.base.whole(c.report.spentRub)
        BoxWithConstraints { HeroAmount(figure, ink, Modifier.letters(heroFigureSize(figure, maxWidth)), signColor = ink) }
        VSpace(HeaderSpace.block)
        if (buckets) {
            // В: the period's three kinds of spending, each a value under its caption, side by side.
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.m)) {
                listOf(Bucket.DAILY to c.report.dailyRub, Bucket.REGULAR to c.report.regularRub, Bucket.BIG to c.report.bigRub).forEach { (b, rub) ->
                    Column(Modifier.weight(1f)) {
                        Text(bucketLabel(b), Modifier.letters(words.fontSize), style = words, maxLines = 1)
                        VSpace(HeaderSpace.valueCaption)
                        Text(c.data.base.whole(rub), Modifier.letters(figures.fontSize), style = figures, color = ink, maxLines = 1)
                    }
                }
            }
        } else {
            Text("В среднем в день", Modifier.letters(words.fontSize), style = words)
            VSpace(HeaderSpace.valueCaption)
            Text("≈ " + c.data.base.approx(c.report.averagePerDayRub), Modifier.letters(figures.fontSize), style = figures, color = ink)
        }
    }
}

@Composable
private fun Period() = ChoiceGroup(
    options = listOf(PeriodKind.WEEK, PeriodKind.MONTH, PeriodKind.CUSTOM), selected = PeriodKind.MONTH, onSelect = {},
    modifier = Modifier.fillMaxWidth().padding(horizontal = Gap.s), height = 40.dp,
) {
    when (it) {
        PeriodKind.WEEK -> ChoiceText("Неделя")
        PeriodKind.MONTH -> ChoiceText("Месяц")
        PeriodKind.CUSTOM -> Icon(painterResource(R.drawable.ic_calendar), null, Modifier.size(20.dp))
    }
}

@Composable
private fun Chevron() = Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)

private fun LazyListScope.categoryRows(c: Ctx, take: Int, extraFirst: Int = 1) {
    val total = c.report.spentRub.coerceAtLeast(1)
    val shown = c.report.categories.take(take)
    shown.forEachIndexed { i, spend ->
        item {
            val cat = spend.categoryId?.let { c.data.categoryById[it] }
            val colour = cat?.key?.let { categoryColor(it, LocalTones.current) } ?: leitmotifColor(null, LocalTones.current)
            GroupRow(
                i + extraFirst, shown.size + extraFirst, Modifier.padding(horizontal = Gap.m), onClick = {},
                leading = { RowIcon(categoryIcon(cat?.key), colour) },
                supporting = { Text(wholePercent(spend.rubMinor.toDouble() / total), style = TextRole.caption) },
                trailing = { Text(c.data.base.whole(spend.rubMinor), style = TextRole.rowAmount, color = MaterialTheme.colorScheme.onSurface) },
            ) { Text(cat?.label() ?: "Без категории", style = TextRole.rowName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun Bars(c: Ctx) {
    val tones = LocalTones.current
    val stacks = c.data.operations.filter { it.op.type == OpType.EXPENSE }
        .groupBy { Ledger.localDate(it.op.timestamp, c.data.zone) }
        .mapValues { (_, ops) ->
            ops.groupBy { it.op.categoryId?.let(c.data.categoryById::get) }.map { (cat, list) ->
                DayPart(cat?.key?.let { categoryColor(it, tones) } ?: leitmotifColor(null, tones), cat?.label() ?: "Без категории", -list.sumOf { o -> o.postings.sumOf { p -> p.rubMinor } })
            }.filter { it.rub > 0 }.sortedByDescending { it.rub }
        }
    DayBars(Analytics.bars(c.report.days), c.data.today, PeriodKind.MONTH, null, c.data.base, stacks)
}

/** A card with no rows of its own: a title, then [body] a title's step under it, the card's edge round it. */
@Composable
private fun BodyCard(title: String, body: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = Gap.s)) {
        Surface(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(CardCorner), color = tileColor()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = ListSpace.edge)) {
                Row { Text(title, Modifier.weight(1f).letters(TextRole.groupTitle.fontSize), style = TextRole.groupTitle); Box(Modifier.size(0.dp)) { } ; Chevron() }
                VSpace(ListSpace.titleToRow)
                body()
            }
        }
    }
}

/** А: the header (total, a day on average), the period, «Категории» (the top four), «По дням», income and exchange. */
@Composable
private fun FirstA(data: AppData, padding: PaddingValues) {
    val c = ctx(data)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.aboveActions()) {
        item { Header(c, buckets = false) }
        item { VSpace(ListSpace.cards); Period() }
        item { VSpace(ListSpace.cards); CardTitle("Категории", Modifier.padding(horizontal = Gap.m)) { Chevron() } }
        categoryRows(c, 4)
        item { VSpace(ListSpace.cards); BodyCard("По дням") { Bars(c) } }
        incomeRows(c)
    }
}

private fun LazyListScope.incomeRows(c: Ctx) {
    item { VSpace(ListSpace.cards); CardTitle("Доходы и обмен", Modifier.padding(horizontal = Gap.m), icons = false) }
    val rows = listOf("Доходы" to c.data.base.approx(c.report.incomeRub), "Потери на обмене" to c.data.base.approx(c.report.fxLossRub))
    rows.forEachIndexed { i, (l, v) ->
        item {
            GroupRow(i + 1, rows.size + 1, Modifier.padding(horizontal = Gap.m)) {
                Row { Text(l, Modifier.weight(1f), style = TextRole.rowName); Text(v, style = TextRole.rowAmount) }
            }
        }
    }
}

/** Б: the header with the three kinds of spending, the period, the mosaic as the categories' card, «По дням» as a row. */
@Composable
private fun FirstB(data: AppData, padding: PaddingValues) {
    val c = ctx(data)
    val tones = LocalTones.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.aboveActions()) {
        item { Header(c, buckets = true) }
        item { VSpace(ListSpace.cards); Period() }
        item {
            VSpace(ListSpace.cards)
            val items = c.report.categories.take(6).map { s ->
                val cat = s.categoryId?.let { c.data.categoryById[it] }
                MosaicItem(cat?.label() ?: "Без категории", c.data.base.whole(s.rubMinor), s.rubMinor, cat?.key?.let { categoryColor(it, tones) } ?: leitmotifColor(null, tones), ids = listOf(s.categoryId))
            }
            BodyCard("Категории") { Mosaic(items) }
        }
        item { VSpace(ListSpace.cards); CardTitle("Подробнее", Modifier.padding(horizontal = Gap.m), icons = false) }
        val rows = listOf("По дням" to "≈ ${c.data.base.approx(c.report.averagePerDayRub)} в день", "Доходы и обмен" to c.data.base.approx(c.report.incomeRub))
        rows.forEachIndexed { i, (l, v) ->
            item {
                GroupRow(i + 1, rows.size + 1, Modifier.padding(horizontal = Gap.m), onClick = {}, supporting = { Text(v, style = TextRole.caption) }, trailing = { Chevron() }, trailingOnTitle = false) {
                    Text(l, style = TextRole.rowName)
                }
            }
        }
    }
}

/** A second contour: back and its title up top (no main button at the bottom), the page under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Second(title: String, body: LazyListScope.() -> Unit) {
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(Modifier.nestedScroll(bar.nestedScrollConnection), topBar = { CollapsingHeader(title, {}, bar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 40.dp)) { body() }
    }
}

/** А, the second contour: every category, the mosaic over the list. */
@Composable
private fun CategoriesPage(data: AppData) {
    val c = ctx(data)
    val tones = LocalTones.current
    Second("Категории") {
        item {
            val items = c.report.categories.take(6).map { s ->
                val cat = s.categoryId?.let { c.data.categoryById[it] }
                MosaicItem(cat?.label() ?: "Без категории", c.data.base.whole(s.rubMinor), s.rubMinor, cat?.key?.let { categoryColor(it, tones) } ?: leitmotifColor(null, tones), ids = listOf(s.categoryId))
            }
            Mosaic(items, Modifier.padding(horizontal = Gap.s))
        }
        item { VSpace(ListSpace.cards); CardTitle("Все категории", Modifier.padding(horizontal = Gap.m)) }
        categoryRows(c, c.report.categories.size)
    }
}

/** А, one level down from a category: its purchases of the period, the feed's rows. */
@Composable
private fun CategoryPage(data: AppData) {
    val c = ctx(data)
    val top = c.report.categories.first()
    val ops = data.operations.filter { it.op.type == OpType.EXPENSE && it.op.categoryId == top.categoryId }.sortedByDescending { it.op.timestamp }
    val name = top.categoryId?.let { data.categoryById[it]?.label() } ?: "Без категории"
    Second(name) {
        item { CardTitle("${data.base.whole(top.rubMinor)} за ${c.title}", Modifier.padding(horizontal = Gap.m)) }
        ops.forEachIndexed { i, op -> item { OperationRow(data, op, null, {}, i + 1, ops.size + 1, Modifier.padding(horizontal = Gap.m)) } }
    }
}

/** Б, the second contour «По дням»: the bars, and under them the days by what they cost. */
@Composable
private fun DaysPage(data: AppData) {
    val c = ctx(data)
    Second("По дням") {
        item { BodyCard("≈ ${c.data.base.approx(c.report.averagePerDayRub)} в день") { Bars(c) } }
        val days = c.report.days.filter { it.second > 0 }.sortedByDescending { it.second }.take(6)
        item { VSpace(ListSpace.cards); CardTitle("Дороже всего", Modifier.padding(horizontal = Gap.m), icons = false) }
        days.forEachIndexed { i, (d, rub) ->
            item {
                GroupRow(i + 1, days.size + 1, Modifier.padding(horizontal = Gap.m), onClick = {}) {
                    Row { Text(d.dayOfMonth.toString() + " " + d.month.getDisplayName(java.time.format.TextStyle.FULL, I18n.locale), Modifier.weight(1f), style = TextRole.rowName); Text(data.base.whole(rub), style = TextRole.rowAmount) }
                }
            }
        }
    }
}

/**
 * Round two, after the owner's notes: the period behind a calendar in the header, the three kinds of
 * spending as a compact mosaic, the review moved three ways, and «По дням» one level down, against
 * Insights as it is, in Golda light at «Просторно», into `docs/tasks/analytics-contours/r2-*`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class AnalyticsRound2PreviewTest(private val name: String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = listOf("now", "v1", "v2", "menu", "filter", "days", "p1", "p2", "p3").map { arrayOf<Any>(it) }
    }

    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    @Test fun shot() {
        val data = ScreenshotTest().demo()
        compose.setContent {
            GoldaTheme("golda", "LIGHT", "ROOMY") {
                Scaffold(
                    floatingActionButton = {
                        when (name) {
                            "days", "p3" -> {}
                            "p1" -> FiveSlots()
                            else -> Island()
                        }
                    },
                    floatingActionButtonPosition = FabPosition.Center,
                ) { padding ->
                    when (name) {
                        "now" -> AnalyticsScreen(data, padding) {}
                        "v1" -> Round2(data, padding, strip = true, bars = true)
                        "v2", "p1" -> Round2(data, padding, strip = false, bars = false)
                        "menu" -> Box { Round2(data, padding, strip = false, bars = false); PeriodMenu() }
                        "filter" -> Round2(data, padding, strip = false, bars = false, filter = Bucket.DAILY)
                        "days" -> DaysContour(data)
                        "p2" -> Round2(data, padding, strip = false, bars = false, reviewRow = true)
                        else -> CategoriesWithReview(data)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/tasks/analytics-contours/r2-$name.png")
    }
}

/** The header with no period strip: the calendar on the caption's line, as Home's gear. */
@Composable
private fun Header2(c: Ctx) {
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onPrimaryFixed
    HeroTile(Modifier.padding(horizontal = Gap.s), vertical = HeaderSpace.edge) {
        val words = TextRole.caption
        val figures = TextRole.headerFigures
        Row(Modifier.letters(words.fontSize)) {
            Text(if (c.title.startsWith("последние ")) "За " + c.title.substringAfter(' ') else "Траты за ${c.title}", Modifier.weight(1f), style = words)
            IconButton(onClick = {}, modifier = Modifier.hang()) { Icon(painterResource(R.drawable.ic_calendar), null, tint = scheme.onPrimaryFixedVariant) }
        }
        VSpace(HeaderSpace.figureCaption)
        val figure = c.data.base.whole(c.report.spentRub)
        BoxWithConstraints { HeroAmount(figure, ink, Modifier.letters(heroFigureSize(figure, maxWidth)), signColor = ink) }
        VSpace(HeaderSpace.block)
        Text("В среднем в день", Modifier.letters(words.fontSize), style = words)
        VSpace(HeaderSpace.valueCaption)
        Text("≈ " + c.data.base.approx(c.report.averagePerDayRub), Modifier.letters(figures.fontSize), style = figures, color = ink)
    }
}

/** The dropdown behind the calendar, drawn in place: a real menu is a popup, which a shot of the root leaves out. */
@Composable
private fun PeriodMenu() {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().padding(top = 62.dp, end = 24.dp), contentAlignment = Alignment.TopEnd) {
        Surface(Modifier.width(208.dp).softShadow(shape), shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(vertical = Gap.s)) {
                listOf("Неделя" to false, "Месяц" to true, "Выбрать даты" to false).forEach { (label, on) ->
                    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = Gap.m), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = TextRole.rowName)
                        if (on) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

private class Kind(val bucket: Bucket, val rub: Long, val colour: androidx.compose.ui.graphics.Color)

/** One kind of spending: its name and sum inside, the picked one in the picked colour, as a filter. */
@Composable
private fun KindTile(k: Kind, on: Boolean, base: sh.aminov.golda.domain.Base, modifier: Modifier) {
    val fill = if (on) pickedColor() else k.colour
    val ink = if (on) onPickedColor() else tileInk(k.colour)
    Box(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(fill).padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
        Column {
            Text(bucketLabel(k.bucket), Modifier.letters(TextRole.caption.fontSize), style = TextRole.caption, color = ink, maxLines = 1)
            VSpace(Gap.s)
            Text(base.whole(k.rub), Modifier.letters(TextRole.rowAmount.fontSize), style = TextRole.rowAmount, color = ink, maxLines = 1)
        }
    }
}

/**
 * The three kinds as a mosaic, the biggest first. [strip]: one row 88 high, each at least 112 wide so its
 * words fit, the width by share above that. Else a block 136 high: the biggest on the left by its share,
 * the two others stacked by theirs, the smaller at least 56 high.
 */
@Composable
private fun KindMosaic(report: Report, base: sh.aminov.golda.domain.Base, strip: Boolean, filter: Bucket?) {
    val tones = LocalTones.current
    val kinds = listOf(Bucket.DAILY to report.dailyRub, Bucket.REGULAR to report.regularRub, Bucket.BIG to report.bigRub)
        .sortedByDescending { it.second }.mapIndexed { i, (b, rub) -> Kind(b, rub, leitmotifColor(i, tones)) }
    val total = kinds.sumOf { it.rub }.coerceAtLeast(1).toFloat()
    val gap = 4.dp
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Gap.s)) {
        if (strip) {
            val room = maxWidth - gap * 2
            val least = 112.dp
            val free = room - least * 3
            Row(Modifier.height(88.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                kinds.forEach { KindTile(it, it.bucket == filter, base, Modifier.width(least + free * (it.rub / total)).height(88.dp)) }
            }
        } else {
            val high = 136.dp
            val left = (maxWidth - gap) * (kinds[0].rub / total)
            val rest = (kinds[1].rub + kinds[2].rub).coerceAtLeast(1).toFloat()
            val low = maxOf((high - gap) * (kinds[2].rub / rest), 56.dp)
            Row(Modifier.height(high), horizontalArrangement = Arrangement.spacedBy(gap)) {
                KindTile(kinds[0], kinds[0].bucket == filter, base, Modifier.width(left).height(high))
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    KindTile(kinds[1], kinds[1].bucket == filter, base, Modifier.fillMaxWidth().height(high - gap - low))
                    KindTile(kinds[2], kinds[2].bucket == filter, base, Modifier.fillMaxWidth().height(low))
                }
            }
        }
    }
}

@Composable
private fun Round2(data: AppData, padding: PaddingValues, strip: Boolean, bars: Boolean, filter: Bucket? = null, reviewRow: Boolean = false) {
    val all = ctx(data)
    val only = data.operations.filter { filter == null || (it.op.type == OpType.EXPENSE && Buckets.of(it.op, data.categoryById) == filter) }
    val c = Ctx(data, Analytics.report(only, data.accountById, data.categoryById, all.report.period, data.today, data.zone, data.rates), all.title)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.aboveActions()) {
        item { Header2(c) }
        item { VSpace(ListSpace.cards); KindMosaic(all.report, data.base, strip, filter) }
        item { VSpace(ListSpace.cards); CardTitle("Категории", Modifier.padding(horizontal = Gap.m)) { Chevron() } }
        categoryRows(c, 4)
        if (bars) {
            item { VSpace(ListSpace.cards); BodyCard("По дням") { Bars(c) } }
        } else {
            item {
                VSpace(ListSpace.cards)
                val last = c.report.days.lastOrNull { it.second > 0 }
                GroupRow(
                    0, 1, Modifier.padding(horizontal = Gap.m), onClick = {},
                    leading = { RowIcon(R.drawable.ic_chart) },
                    supporting = { Text(last?.let { dayLabel(it.first, data.today) + " " + data.base.whole(it.second) }.orEmpty(), style = TextRole.caption) },
                    trailing = { Chevron() }, trailingOnTitle = false,
                ) { Text("По дням", style = TextRole.rowName) }
            }
        }
        if (reviewRow) {
            item {
                VSpace(ListSpace.cards)
                GroupRow(
                    0, 1, Modifier.padding(horizontal = Gap.m), onClick = {},
                    leading = { RowIcon(R.drawable.ic_send, iceColor()) },
                    trailing = { Chevron() }, trailingOnTitle = false,
                ) { Text("Разбор", style = TextRole.rowName) }
            }
        }
        // Income and exchange are not spending: no filter narrows them, as now.
        incomeRows(all)
    }
}

/** The island with a fifth slot for the review: the same width, every slot narrower. */
@Composable
private fun FiveSlots() {
    val scheme = MaterialTheme.colorScheme
    val slot = (72.dp * 4) / 5
    Row(horizontalArrangement = Arrangement.spacedBy(Gap.s), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.height(60.dp).softShadow(CircleShape), shape = CircleShape, color = scheme.surfaceContainerHigh, contentColor = scheme.onSurfaceVariant) {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(R.drawable.ic_home, R.drawable.ic_wallet, R.drawable.ic_chart_fill).forEachIndexed { i, icon ->
                    Box(Modifier.size(slot, 48.dp).background(if (i == 2) pickedColor() else androidx.compose.ui.graphics.Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(icon), null, Modifier.size(26.dp), tint = if (i == 2) onPickedColor() else scheme.onSurfaceVariant)
                    }
                }
                VerticalDivider(Modifier.height(24.dp).padding(horizontal = Gap.xs), color = scheme.outlineVariant)
                Box(Modifier.size(slot, 48.dp), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_add), null, Modifier.size(26.dp)) }
                Box(Modifier.size(slot, 48.dp), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_send), null, Modifier.size(26.dp), tint = onIceColor()) }
            }
        }
        Surface(Modifier.size(60.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(CardCorner), color = actionColor(), contentColor = onActionColor()) {
            Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_mic), null, Modifier.size(32.dp)) }
        }
    }
}

/** «По дням» one level down: the bars, the picked day's purchases under them, then the dearest days. */
@Composable
private fun DaysContour(data: AppData) {
    val c = ctx(data)
    val picked = c.report.days.last { it.second > 0 }.first
    val ops = data.visibleOperations.filter { Ledger.localDate(it.op.timestamp, data.zone) == picked }
    Second("По дням") {
        item {
            Surface(Modifier.padding(horizontal = Gap.s).fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(CardCorner), color = tileColor()) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = ListSpace.edge)) { Bars(c) }
            }
        }
        item { VSpace(ListSpace.cards); CardTitle(dayLabel(picked, data.today), Modifier.padding(horizontal = Gap.m)) }
        ops.forEachIndexed { i, op -> item { OperationRow(data, op, null, {}, i + 1, ops.size + 1, Modifier.padding(horizontal = Gap.m)) } }
        val dear = c.report.days.filter { it.second > 0 }.sortedByDescending { it.second }.take(3)
        item { VSpace(ListSpace.cards); CardTitle("Дороже всего", Modifier.padding(horizontal = Gap.m), icons = false) }
        dear.forEachIndexed { i, (d, rub) ->
            item {
                GroupRow(i + 1, dear.size + 1, Modifier.padding(horizontal = Gap.m), onClick = {}, trailing = { Text(data.base.whole(rub), style = TextRole.rowAmount, color = MaterialTheme.colorScheme.onSurface) }, trailingOnTitle = false) {
                    Text(dayLabel(d, data.today), style = TextRole.rowName)
                }
            }
        }
    }
}

/** The review as an action one level down: «Разбор» up top on «Категории». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesWithReview(data: AppData) {
    val c = ctx(data)
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = {
            CollapsingHeader("Категории", {}, bar) {
                Button(
                    onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = iceColor(), contentColor = onIceColor()),
                    contentPadding = PaddingValues(horizontal = Gap.m), modifier = Modifier.padding(end = Gap.s).height(40.dp),
                ) { Text("Разбор", style = MaterialTheme.typography.labelLarge) }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 40.dp)) {
            item {
                val tones = LocalTones.current
                val items = c.report.categories.map { s ->
                    val cat = s.categoryId?.let { data.categoryById[it] }
                    MosaicItem(cat?.label() ?: "Без категории", data.base.whole(s.rubMinor), s.rubMinor, cat?.key?.let { categoryColor(it, tones) } ?: leitmotifColor(null, tones), ids = listOf(s.categoryId))
                }
                Mosaic(items, Modifier.padding(horizontal = Gap.s))
            }
            item { VSpace(ListSpace.cards); CardTitle("Все категории", Modifier.padding(horizontal = Gap.m)) }
            categoryRows(c, c.report.categories.size)
        }
    }
}

/**
 * Round three: everything under the kinds' mosaic. One living list the mosaic rebuilds, its title five
 * ways; the categories with no page of their own; the days with none; and two whole screens of my own,
 * beside Insights as it is, in Golda light at «Просторно», into `docs/tasks/analytics-contours/r3-*`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class AnalyticsRound3PreviewTest(private val name: String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = listOf(
            "now", "t1", "t2", "t3", "t4", "t5", "reg", "big",
            "k1", "k1open", "k2", "k3", "d1", "d2", "d2reg", "o2", "o2day",
            "c1", "c1reg", "c1daily", "c1regday", "c2", "c2reg", "c2days", "c2regdays", "c2dailydays", "c2bigdays", "c2regall", "c2bigall", "c3", "c3reg",
        ).map { arrayOf<Any>(it) }
    }

    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    @Test fun shot() {
        val data = ScreenshotTest().demo()
        compose.setContent {
            GoldaTheme("golda", "LIGHT", "ROOMY") {
                Scaffold(
                    floatingActionButton = { if (!name.startsWith("d2")) Island() },
                    floatingActionButtonPosition = FabPosition.Center,
                ) { padding ->
                    when (name) {
                        "now" -> AnalyticsScreen(data, padding) {}
                        "t1", "k1" -> Screen3(data, padding, "Все траты")
                        "t2" -> Screen3(data, padding, "Всего")
                        "t3" -> Screen3(data, padding, "Итого")
                        "t4" -> Screen3(data, padding, "Куда ушло")
                        "t5" -> Screen3(data, padding, "Все категории")
                        "reg" -> Screen3(data, padding, "Все траты", filter = Bucket.REGULAR)
                        "big" -> Screen3(data, padding, "Все траты", filter = Bucket.BIG)
                        "k1open" -> Screen3(data, padding, "Все траты", list = Rows.OPEN)
                        "k2" -> Screen3(data, padding, "Все траты", list = Rows.ALL)
                        "k3" -> Screen3(data, padding, "Все траты", list = Rows.PAGE)
                        "d1" -> Screen3(data, padding, "Все траты", days = Days.INLINE)
                        "d2" -> Box { Screen3(data, padding, "Все траты"); DaysSheet(data) }
                        "d2reg" -> Box { Screen3(data, padding, "Все траты", filter = Bucket.REGULAR); DaysSheet(data, Bucket.REGULAR) }
                        "c1" -> Screen3(data, padding, "Все траты", days = Days.WHOLE)
                        "c1reg" -> Screen3(data, padding, "Все траты", filter = Bucket.REGULAR, days = Days.WHOLE)
                        "c1daily" -> Screen3(data, padding, "Все траты", filter = Bucket.DAILY, days = Days.WHOLE)
                        "c1regday" -> Screen3(data, padding, "Все траты", filter = Bucket.REGULAR, days = Days.WHOLE, dayPicked = true)
                        "c2" -> Screen3(data, padding, "Все траты", days = Days.TABS)
                        "c2reg" -> Screen3(data, padding, "Все траты", filter = Bucket.REGULAR, days = Days.TABS)
                        "c2days" -> Screen3(data, padding, "Все траты", days = Days.TABS, tabDays = true)
                        "c2regdays" -> Screen3(data, padding, "Все траты", filter = Bucket.REGULAR, days = Days.TABS, tabDays = true)
                        "c2dailydays" -> Screen3(data, padding, "Все траты", filter = Bucket.DAILY, days = Days.TABS, tabDays = true)
                        "c2bigdays" -> Screen3(data, padding, "Все траты", filter = Bucket.BIG, days = Days.TABS, tabDays = true)
                        "c2regall" -> Screen3(data, padding, "Все траты", filter = Bucket.REGULAR, days = Days.TABS, tabDays = true, wholePeriod = true)
                        "c2bigall" -> Screen3(data, padding, "Все траты", filter = Bucket.BIG, days = Days.TABS, tabDays = true, wholePeriod = true)
                        "c3" -> Screen3(data, padding, "Все траты", days = Days.TWO)
                        "c3reg" -> Screen3(data, padding, "Все траты", filter = Bucket.REGULAR, days = Days.TWO)
                        "o2" -> Screen3(data, padding, "Все траты", days = Days.FILTER)
                        else -> Screen3(data, padding, "Все траты", days = Days.FILTER, dayPicked = true)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("../docs/tasks/analytics-contours/r3-$name.png")
    }
}

private enum class Rows { FOUR, OPEN, ALL, PAGE }
private enum class Days { ROW, INLINE, TABS, FILTER, WHOLE, TWO }

private fun narrowed(data: AppData, filter: Bucket?): Ctx {
    val all = ctx(data)
    val only = data.operations.filter { filter == null || (it.op.type == OpType.EXPENSE && Buckets.of(it.op, data.categoryById) == filter) }
    return Ctx(data, Analytics.report(only, data.accountById, data.categoryById, all.report.period, data.today, data.zone, data.rates), all.title)
}

/** Each day's spending by category, for the bars; [filter] narrows it like the tile does. */
@Composable
private fun dayStacks(data: AppData, filter: Bucket?): Map<java.time.LocalDate, List<DayPart>> {
    val tones = LocalTones.current
    return data.operations.filter { it.op.type == OpType.EXPENSE && (filter == null || Buckets.of(it.op, data.categoryById) == filter) }
        .groupBy { Ledger.localDate(it.op.timestamp, data.zone) }
        .mapValues { (_, ops) ->
            ops.groupBy { it.op.categoryId?.let(data.categoryById::get) }.map { (cat, list) ->
                DayPart(cat?.key?.let { categoryColor(it, tones) } ?: leitmotifColor(null, tones), cat?.label() ?: "Без категории", -list.sumOf { o -> o.postings.sumOf { p -> p.rubMinor } })
            }.filter { it.rub > 0 }.sortedByDescending { it.rub }
        }
}

/**
 * The bars lower than today's 140, their pill over the picked day, the period's ends under them.
 * [picked] null: no pill and no dot, every day equal.
 */
@Composable
private fun LowBars(c: Ctx, filter: Bucket?, picked: java.time.LocalDate?, high: Dp) {
    val days = Analytics.bars(c.report.days)
    val stacks = dayStacks(c.data, filter)
    val plan = c.data.envelope?.let { e -> c.data.rates.rubMinor(e.perDayMinor, e.currency) }?.takeIf { it > 0 && filter == Bucket.DAILY }
    val top = maxOf(days.maxOf { it.second }, plan ?: 0L).coerceAtLeast(1)
    val ink = MaterialTheme.colorScheme.onSurface
    val line = MaterialTheme.colorScheme.outline
    val faint = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.fillMaxWidth()) {
        Column {
            if (picked != null) {
                val i = days.indexOfFirst { it.first == picked }.coerceAtLeast(0)
                val label = (if (picked == c.data.today) "Сегодня  " else dayLabel(picked, c.data.today) + "  ") + c.data.base.approx(days[i].second)
                Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.TopEnd) {
                    Surface(shape = CircleShape, color = pickedColor(), contentColor = onPickedColor()) {
                        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
                VSpace(Gap.s)
            }
            Canvas(Modifier.fillMaxWidth().height(high)) {
                val w = size.width / days.size
                val bar = w * 0.8f
                val radius = minOf(bar / 2, 12.dp.toPx())
                days.forEachIndexed { i, (day, value) ->
                    val h = if (value > 0) (size.height * value / top).coerceAtLeast(radius) else 3.dp.toPx()
                    val left = w * i + (w - bar) / 2
                    val path = Path().apply {
                        addRoundRect(RoundRect(left, size.height - h, left + bar, size.height, CornerRadius(radius), CornerRadius(radius), CornerRadius.Zero, CornerRadius.Zero))
                    }
                    val parts = stacks[day].orEmpty()
                    val sum = parts.sumOf { it.rub }
                    if (value > 0 && sum > 0) {
                        clipPath(path) {
                            var y = size.height
                            parts.forEach { p ->
                                val part = h * p.rub / sum
                                drawRect(p.color, Offset(left, y - part), Size(bar, part))
                                y -= part
                            }
                        }
                    } else {
                        drawPath(path, faint)
                    }
                    if (day == picked) drawCircle(ink, 3.dp.toPx(), Offset(left + bar / 2, size.height + 7.dp.toPx()))
                }
                plan?.let {
                    val y = size.height - size.height * it / top
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                }
            }
            VSpace(Gap.m)
            Row(Modifier.fillMaxWidth()) {
                Text(dayMonth(days.first().first), Modifier.weight(1f), style = TextRole.caption)
                Text(dayMonth(days.last().first), style = TextRole.caption)
            }
            plan?.let {
                VSpace(Gap.m)
                Text("План " + c.data.base.approx(it) + " в день", style = TextRole.caption)
            }
        }
    }
}

/** A part of a card between its title and its rows: the card's colour, no corners, the sides' 16. */
@Composable
private fun Middle(content: @Composable () -> Unit) {
    Surface(Modifier.padding(horizontal = Gap.m).bleed(Gap.s).fillMaxWidth(), color = tileColor()) {
        Box(Modifier.padding(horizontal = 16.dp)) { content() }
    }
}

private fun LazyListScope.more(c: Ctx, hidden: Int, index: Int, count: Int, open: Boolean) {
    item {
        val rest = c.report.categories.drop(4).sumOf { it.rubMinor }
        GroupRow(
            index, count, Modifier.padding(horizontal = Gap.m), onClick = {},
            leading = { RowIcon(if (open) R.drawable.ic_close else R.drawable.ic_chevron_down) },
            trailing = { if (!open) Text(c.data.base.whole(rest), style = TextRole.rowAmount, color = MaterialTheme.colorScheme.onSurface) },
            trailingOnTitle = false,
        ) { Text(if (open) "Свернуть" else "Ещё " + hidden + " " + kinds(hidden), style = TextRole.rowName) }
    }
}

private fun kinds(n: Int) = when {
    n % 100 in 11..14 -> "категорий"
    n % 10 == 1 -> "категория"
    n % 10 in 2..4 -> "категории"
    else -> "категорий"
}

/** What is left of a list with [shown] of its categories and one more row at its end. */
private fun LazyListScope.rowsOf(c: Ctx, shown: Int, extra: Int) {
    val total = c.report.spentRub.coerceAtLeast(1)
    val list = c.report.categories.take(shown)
    list.forEachIndexed { i, spend ->
        item {
            val cat = spend.categoryId?.let { c.data.categoryById[it] }
            val colour = cat?.key?.let { categoryColor(it, LocalTones.current) } ?: leitmotifColor(null, LocalTones.current)
            GroupRow(
                i + 1, list.size + 1 + extra, Modifier.padding(horizontal = Gap.m), onClick = {},
                leading = { RowIcon(categoryIcon(cat?.key), colour) },
                supporting = { Text(wholePercent(spend.rubMinor.toDouble() / total), style = TextRole.caption) },
                trailing = { Text(c.data.base.whole(spend.rubMinor), style = TextRole.rowAmount, color = MaterialTheme.colorScheme.onSurface) },
            ) { Text(cat?.label() ?: "Без категории", style = TextRole.rowName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

private fun LazyListScope.listCard(c: Ctx, title: String, rows: Rows) {
    val n = c.report.categories.size
    val hidden = (n - 4).coerceAtLeast(0)
    item {
        VSpace(ListSpace.cards)
        if (rows == Rows.PAGE) CardTitle(title, Modifier.padding(horizontal = Gap.m)) { Chevron() } else CardTitle(title, Modifier.padding(horizontal = Gap.m))
    }
    when (rows) {
        Rows.ALL -> rowsOf(c, n, 0)
        Rows.PAGE -> rowsOf(c, 4, 0)
        Rows.OPEN -> { rowsOf(c, n, 1); more(c, hidden, n + 1, n + 2, open = true) }
        Rows.FOUR -> if (hidden > 0) { rowsOf(c, 4, 1); more(c, hidden, 5, 6, open = false) } else rowsOf(c, n, 0)
    }
}

private fun ofKind(data: AppData, op: sh.aminov.golda.data.OperationFull, filter: Bucket?) =
    filter == null || (op.op.type == OpType.EXPENSE && Buckets.of(op.op, data.categoryById) == filter)

private fun LazyListScope.dayRows(data: AppData, day: java.time.LocalDate, filter: Bucket? = null) {
    val ops = data.visibleOperations.filter { Ledger.localDate(it.op.timestamp, data.zone) == day && ofKind(data, it, filter) }
    ops.forEachIndexed { i, op -> item { OperationRow(data, op, null, {}, i + 1, ops.size + 1, Modifier.padding(horizontal = Gap.m)) } }
}

/** Every payment of the kind in the period, the date where a day's list has its category: few enough to need no picking. */
private fun LazyListScope.periodRows(c: Ctx, filter: Bucket?) {
    val data = c.data
    val from = c.report.days.first().first
    val ops = data.visibleOperations
        .filter { ofKind(data, it, filter) && !Ledger.localDate(it.op.timestamp, data.zone).isBefore(from) }
        .sortedByDescending { it.op.timestamp }
    ops.forEachIndexed { i, op ->
        item {
            val cat = op.op.categoryId?.let { data.categoryById[it] }
            val colour = cat?.key?.let { categoryColor(it, LocalTones.current) } ?: leitmotifColor(null, LocalTones.current)
            GroupRow(
                i + 1, ops.size + 1, Modifier.padding(horizontal = Gap.m), onClick = {},
                leading = { RowIcon(categoryIcon(cat?.key), colour) },
                supporting = { Text(dayLabel(Ledger.localDate(op.op.timestamp, data.zone), data.today), style = TextRole.caption) },
                trailing = { Text(data.base.whole(-op.postings.sumOf { p -> p.rubMinor }), style = TextRole.rowAmount, color = MaterialTheme.colorScheme.onSurface) },
            ) { Text(op.op.note.ifBlank { cat?.label() ?: "Без категории" }, style = TextRole.rowName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Four categories and «Ещё», or all of them when there are no more than that. */
private fun LazyListScope.listRows(c: Ctx) {
    val n = c.report.categories.size
    if (n > 4) { rowsOf(c, 4, 1); more(c, n - 4, 5, 6, open = false) } else rowsOf(c, n, 0)
}

/** The bars inside a card, with the air a row keeps from them. */
@Composable
private fun BarsInCard(c: Ctx, filter: Bucket?, picked: java.time.LocalDate?) {
    Middle { Box(Modifier.padding(top = ListSpace.titleToRow, bottom = Gap.m)) { LowBars(c, filter, picked, 96.dp) } }
}

/** Everything under the accepted top, one way or another; «Разбор» a row and income at the end throughout. */
@Composable
private fun Screen3(
    data: AppData, padding: PaddingValues, title: String, filter: Bucket? = null, list: Rows = Rows.FOUR,
    days: Days = Days.ROW, tabDays: Boolean = false, dayPicked: Boolean = false, wholePeriod: Boolean = false,
) {
    val all = ctx(data)
    val c = narrowed(data, filter)
    val shownTitle = filter?.let { bucketLabel(it) } ?: title
    val last = c.report.days.last { it.second > 0 }.first
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.aboveActions()) {
        item { Header2(c) }
        item { VSpace(ListSpace.cards); KindMosaic(all.report, data.base, false, filter) }
        when (days) {
            Days.TABS -> {
                item { VSpace(ListSpace.cards); CardTitle(shownTitle, Modifier.padding(horizontal = Gap.m)) }
                item {
                    Middle {
                        ChoiceGroup(listOf("Категории", "По дням"), if (tabDays) "По дням" else "Категории", {}, Modifier.fillMaxWidth(), height = 40.dp) { ChoiceText(it) }
                    }
                }
                if (tabDays) {
                    item { BarsInCard(c, filter, if (wholePeriod) null else last) }
                    if (wholePeriod) periodRows(c, filter) else dayRows(data, last, filter)
                } else {
                    listRows(c)
                }
            }
            Days.WHOLE -> {
                item { VSpace(ListSpace.cards); CardTitle(shownTitle, Modifier.padding(horizontal = Gap.m)) }
                item { BarsInCard(c, filter, if (dayPicked) last else null) }
                if (dayPicked) dayRows(data, last, filter) else listRows(c)
            }
            Days.TWO -> {
                listCard(c, "$shownTitle по категориям", Rows.FOUR)
                item { VSpace(ListSpace.cards); CardTitle("$shownTitle по дням", Modifier.padding(horizontal = Gap.m)) }
                item { BarsInCard(c, filter, last) }
                dayRows(data, last, filter)
            }
            Days.FILTER -> {
                item {
                    VSpace(ListSpace.cards)
                    Surface(Modifier.padding(horizontal = Gap.s).fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(CardCorner), color = tileColor()) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = ListSpace.edge)) { LowBars(c, filter, if (dayPicked) last else null, 72.dp) }
                    }
                }
                if (dayPicked) {
                    item { VSpace(ListSpace.cards); CardTitle(dayLabel(last, data.today), Modifier.padding(horizontal = Gap.m)) }
                    dayRows(data, last)
                } else {
                    listCard(c, shownTitle, Rows.FOUR)
                }
            }
            Days.INLINE -> {
                listCard(c, shownTitle, list)
                item { VSpace(ListSpace.cards); CardTitle("По дням", Modifier.padding(horizontal = Gap.m)) }
                item { Middle { LowBars(c, filter, last, 96.dp) } }
                dayRows(data, last)
            }
            Days.ROW -> {
                listCard(c, shownTitle, list)
                item {
                    VSpace(ListSpace.cards)
                    val day = c.report.days.lastOrNull { it.second > 0 }
                    GroupRow(
                        0, 1, Modifier.padding(horizontal = Gap.m), onClick = {},
                        leading = { RowIcon(R.drawable.ic_chart) },
                        supporting = { Text(day?.let { dayLabel(it.first, data.today) + " " + data.base.whole(it.second) }.orEmpty(), style = TextRole.caption) },
                        trailing = { Chevron() }, trailingOnTitle = false,
                    ) { Text("По дням", style = TextRole.rowName) }
                }
            }
        }
        item {
            VSpace(ListSpace.cards)
            GroupRow(
                0, 1, Modifier.padding(horizontal = Gap.m), onClick = {},
                leading = { RowIcon(R.drawable.ic_send, iceColor()) },
                trailing = { Chevron() }, trailingOnTitle = false,
            ) { Text("Разбор", style = TextRole.rowName) }
        }
        incomeRows(all)
    }
}

/** Д2: «По дням» as a sheet from below, drawn in place: a real sheet is its own window, which a shot of the root leaves out. */
@Composable
private fun DaysSheet(data: AppData, filter: Bucket? = null) {
    val c = narrowed(data, filter)
    val last = c.report.days.last { it.second > 0 }.first
    val top = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x66000000)), contentAlignment = Alignment.BottomCenter) {
        Surface(Modifier.fillMaxWidth(), shape = top, color = tileColor()) {
            Column(Modifier.padding(bottom = ListSpace.edge)) {
                Box(Modifier.fillMaxWidth().padding(vertical = Gap.m), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(32.dp, 4.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
                }
                Text(filter?.let { bucketLabel(it) } ?: "По дням", Modifier.padding(horizontal = 24.dp).letters(TextRole.groupTitle.fontSize), style = TextRole.groupTitle)
                VSpace(ListSpace.titleToRow)
                Box(Modifier.padding(horizontal = 24.dp)) { LowBars(c, filter, last, 140.dp) }
                VSpace(ListSpace.titleToRow)
                val ops = data.visibleOperations.filter { Ledger.localDate(it.op.timestamp, data.zone) == last && ofKind(data, it, filter) }
                ops.forEachIndexed { i, op -> OperationRow(data, op, null, {}, i + 1, ops.size + 1, Modifier.padding(horizontal = Gap.m)) }
            }
        }
    }
}
