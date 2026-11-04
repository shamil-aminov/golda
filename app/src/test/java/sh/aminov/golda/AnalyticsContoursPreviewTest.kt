package sh.aminov.golda

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
                supporting = { Text("${spend.rubMinor * 100 / total} %", style = TextRole.caption) },
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

@Suppress("unused") private val keep = Fmt to Buckets
