package sh.aminov.golda.ui

import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import sh.aminov.golda.R
import sh.aminov.golda.data.Bucket
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Review
import sh.aminov.golda.domain.Analytics
import sh.aminov.golda.domain.Base
import sh.aminov.golda.domain.Buckets
import sh.aminov.golda.domain.CategorySpend
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.Period
import sh.aminov.golda.domain.PeriodKind
import sh.aminov.golda.domain.ReviewPrompt
import sh.aminov.golda.domain.label
import sh.aminov.golda.domain.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/**
 * What is picked on Insights, one state for the card and its page: the period, the kind and a bar.
 * Another period or kind lets the bar go.
 */
class InsightsPick(kind: PeriodKind = PeriodKind.MONTH, range: Period? = null, filter: Bucket? = null, bar: LocalDate? = null) {
    var kind by mutableStateOf(kind)
        private set
    var range by mutableStateOf(range)
        private set
    var filter by mutableStateOf(filter)
        private set
    var bar by mutableStateOf(bar)
        private set

    fun period(today: LocalDate): Period = Analytics.period(kind, today, range)

    fun choosePeriod(next: PeriodKind) {
        if (next != kind) bar = null
        kind = next
    }

    fun chooseRange(from: LocalDate, to: LocalDate) {
        range = Period(from, to)
        kind = PeriodKind.CUSTOM
        bar = null
    }

    fun chooseKind(next: Bucket?) {
        if (next != filter) bar = null
        filter = next
    }

    fun tapBar(tapped: LocalDate) { bar = Analytics.toggled(bar, tapped) }

    fun letBarGo() { bar = null }

    companion object {
        /** As strings, so it survives the activity being recreated. */
        val Saver = listSaver<InsightsPick, String?>(
            save = { listOf(it.kind.name, it.range?.from?.toString(), it.range?.to?.toString(), it.filter?.name, it.bar?.toString()) },
            restore = { (kind, from, to, filter, bar) ->
                InsightsPick(
                    PeriodKind.valueOf(kind!!),
                    from?.let { start -> to?.let { end -> Period(LocalDate.parse(start), LocalDate.parse(end)) } },
                    filter?.let(Bucket::valueOf), bar?.let(LocalDate::parse),
                )
            },
        )
    }
}

@Composable
fun rememberInsightsPick(): InsightsPick = rememberSaveable(saver = InsightsPick.Saver) { InsightsPick() }

/**
 * The period's spending on one screen: one card of the kind, its figure, the bars over their legend, of
 * one height whatever is picked. The title picks the kind and the bars a bar; the rest of the card opens
 * the details ([InsightsDetails]). Income and exchange follow, and the reviews close the screen.
 */
@Composable
fun AnalyticsScreen(
    data: AppData,
    padding: PaddingValues,
    pick: InsightsPick = rememberInsightsPick(),
    onDetails: () -> Unit = {},
    reviews: List<Review> = emptyList(),
    onOpenReview: (Long, Period) -> Unit = { _, _ -> },
    onReviews: (Period) -> Unit,
) {
    val today = data.today
    // A kind picked narrows the whole card.
    val filter = pick.filter
    val period = pick.period(today)
    val report = remember(data, period) { Analytics.report(data.operations, data.accountById, data.categoryById, period, today, data.zone, data.rates) }
    val kept = remember(data, filter) {
        val bucket = filter ?: return@remember data.operations
        data.operations.filter { it.op.type == OpType.EXPENSE && Buckets.of(it.op, data.categoryById) == bucket }
    }
    val shown = remember(report, kept, filter) {
        if (filter == null) report else Analytics.report(kept, data.accountById, data.categoryById, period, today, data.zone, data.rates)
    }
    // Each day's spending by category, biggest first: the day bars are stacked in the categories' colours.
    val tones = LocalTones.current
    val stacks = remember(data, period, filter, tones) {
        // Like the total: every expense, or the picked kind's alone.
        data.operations
            .filter { it.op.type == OpType.EXPENSE && (filter == null || Buckets.of(it.op, data.categoryById) == filter) }
            .groupBy { Ledger.localDate(it.op.timestamp, data.zone) }
            .mapValues { (_, ops) ->
                ops.groupBy { it.op.categoryId?.let(data.categoryById::get) }
                    .map { (category, list) ->
                        DayPart(category?.key?.let { categoryColor(it, tones) } ?: leitmotifColor(null, tones), category?.label() ?: tr("Без категории", "No category"), -list.sumOf { o -> o.postings.sumOf { p -> p.rubMinor } })
                    }
                    .filter { it.rub > 0 }
                    .sortedByDescending { it.rub }
            }
    }
    // As fine as the chart's cap allows. The day's plan means nothing against a week, some of them
    // cut short by the period's edge, so it shows only for days.
    val first = shown.days.firstOrNull()?.first ?: today
    val last = shown.days.lastOrNull()?.first ?: today
    val bar = Analytics.barFor(first, last)
    val barStacks = remember(stacks, bar, first) {
        if (bar == Analytics.Bar.DAY) stacks
        else stacks.entries.groupBy { Analytics.barStart(it.key, first, bar) }.mapValues { (_, days) ->
            days.flatMap { it.value }.groupBy { it.name }.map { (_, parts) -> parts.first().copy(rub = parts.sumOf { it.rub }) }.sortedByDescending { it.rub }
        }
    }
    val plan = data.envelope?.let { e -> data.rates.rubMinor(e.perDayMinor, e.currency) }?.takeIf { it > 0 && filter == Bucket.DAILY && bar == Analytics.Bar.DAY }
    val day = pick.bar
    // What the picked bar spans.
    val span = day?.let { Period(it, Analytics.barEnd(it, last, bar)) }

    val bars = Analytics.bars(shown.days)
    val quiet = MaterialTheme.colorScheme.onSurface.copy(alpha = RestAlpha)
    val legend = if (day != null) barStacks[day].orEmpty() else shown.categories.map { spend ->
        val key = spend.categoryId?.let { data.categoryById[it]?.key }
        DayPart(if (key == null) leitmotifColor(null, tones) else categoryColor(key, tones), categoryName(data, spend), spend.rubMinor)
    }
    val spending = shown.categories.isNotEmpty()

    // The whole page, not just the content: a short list left the pager without a back swipe here.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.aboveActions()) {
        item(key = "card") {
            // One height whatever is picked. The whole card opens the details; the title, the marks and the bars keep their taps.
            Surface(
                onClick = onDetails,
                enabled = spending,
                modifier = Modifier.padding(horizontal = Gap.s).fillMaxWidth().semantics { if (spending) contentDescription = tr("Подробнее", "Details") },
                shape = RoundedCornerShape(CardCorner), color = tileColor(),
            ) {
                Column(Modifier.padding(horizontal = Space.card, vertical = ListSpace.edge)) {
                    val tint = MaterialTheme.colorScheme.onSurfaceVariant
                    val ink = MaterialTheme.colorScheme.onSurface
                    val figure = data.base.whole(if (day == null) shown.spentRub else bars.firstOrNull { it.first == day }?.second ?: 0L)
                    // A kind is never more than the period: sized by the whole, a pick leaves the figure's size as it is.
                    val sizedFor = data.base.whole(report.spentRub)
                    Row {
                        KindTitle(pick, Modifier.weight(1f), TextRole.groupTitle, onCard = true)
                        PeriodMark(pick, today, tint, Modifier.hang())
                    }
                    VSpace(HeaderSpace.valueCaption)
                    Text(span?.let { barCaption(it.from, it.to, bar) } ?: periodLine(period, today), Modifier.letters(TextRole.caption.fontSize), style = TextRole.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    VSpace(HeaderSpace.figureCaption)
                    BoxWithConstraints { HeroAmount(figure, ink, Modifier.testTag(Tags.INSIGHTS_TOTAL).letters(heroFigureSize(sizedFor, maxWidth)), signColor = ink, sizedFor = sizedFor) }
                    VSpace(HeaderSpace.block)
                    if (spending) {
                        DayBars(bars, bar, last, plan, data.base, barStacks, day, onTap = pick::tapBar)
                        VSpace(ChartSpace.labelsToLegend)
                        BarLegend(legend, quiet, data.base, if (day == null) tr("За эти дни трат нет", "No spending in these days") else tr("В этот день трат нет", "No spending this day"))
                    } else {
                        Text(tr("За эти дни трат нет", "No spending in these days"), Modifier.letters(TextRole.rowName.fontSize), style = TextRole.rowName)
                    }
                }
            }
        }
        // Income and exchange are not spending: no kind narrows them.
        incomeRows(data, report.incomeRub, report.fxLossRub.takeIf { report.fxVolumeRub > 0 })
        reviewRows(data, reviews, onOpen = { onOpenReview(it, period) }, onAll = { onReviews(period) })
    }
}

/** The card's line: when and how much. */
private fun chartLine(data: AppData, period: Period, span: Period?, bar: Analytics.Bar, today: LocalDate, rub: Long): String =
    (span?.let { barCaption(it.from, it.to, bar) } ?: periodLine(period, today)) + " · " + data.base.whole(rub)

/** How quiet the bars' and the legend's last layer is, the rest of the categories. */
private const val RestAlpha = 0.22f

/**
 * The picked bar's layers, or the period's, as they stand in the bars: three at most, the third the
 * rest when there are more. Always three rows high, so nothing under the card moves.
 */
@Composable
private fun BarLegend(parts: List<DayPart>, rest: Color, base: Base, none: String) {
    val own = Analytics.coloured(parts.size)
    val rows = parts.take(own) + if (own < parts.size) listOf(DayPart(rest, tr("Остальное", "The rest"), parts.drop(own).sumOf { it.rub })) else emptyList()
    repeat(3) { i ->
        if (i > 0) VSpace(ChartSpace.legendRows)
        val row = rows.getOrNull(i)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row != null) {
                Box(Modifier.size(10.dp).background(row.color, CircleShape))
                Spacer(Modifier.width(Gap.s))
            }
            val words = row?.name ?: none.takeIf { i == 0 }
            // An empty row holds its height with letters no one sees or hears.
            Text(
                words ?: "0", Modifier.weight(1f).letters(TextRole.rowName.fontSize).then(if (words == null) Modifier.clearAndSetSemantics {} else Modifier),
                style = TextRole.rowName, color = if (words == null) Color.Transparent else Color.Unspecified, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (row != null) Text(base.whole(row.rub), Modifier.letters(TextRole.rowAmount.fontSize), style = TextRole.rowAmount, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

/**
 * The card's second level: the same title and line, and in full what the legend folds. The categories,
 * a tap opening a category's purchases; for a picked day, its purchases.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsDetails(data: AppData, pick: InsightsPick, onBack: () -> Unit, onEdit: (OperationFull) -> Unit) {
    val today = data.today
    val filter = pick.filter
    val period = pick.period(today)
    val kept = remember(data, filter) {
        val bucket = filter ?: return@remember data.operations
        data.operations.filter { it.op.type == OpType.EXPENSE && Buckets.of(it.op, data.categoryById) == bucket }
    }
    val whole = remember(kept, period) { Analytics.report(kept, data.accountById, data.categoryById, period, today, data.zone, data.rates) }
    val bar = Analytics.barFor(whole.days.firstOrNull()?.first ?: today, whole.days.lastOrNull()?.first ?: today)
    val span = pick.bar?.let { Period(it, Analytics.barEnd(it, whole.days.lastOrNull()?.first ?: today, bar)) }
    val scoped = remember(kept, span, whole) { span?.let { Analytics.report(kept, data.accountById, data.categoryById, it, today, data.zone, data.rates) } ?: whole }
    val perDay = span != null && bar == Analytics.Bar.DAY
    val ops = remember(data, filter, span, perDay) { if (perDay) inPeriod(data, span!!, filter) { true } else emptyList() }
    var opened by remember { mutableStateOf<OpenedCategory?>(null) }
    val fold = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(fold.nestedScrollConnection),
        topBar = {
            CollapsingHeader({
                // The bar keeps both of its titles in place, one unseen: only the one in sight takes a tap.
                val style = LocalTextStyle.current
                val expanded = style.fontSize.value > TextRole.groupTitle.fontSize.value
                KindTitle(pick, Modifier.padding(start = Gap.s), style, onCard = false, enabled = expanded == (fold.state.collapsedFraction < 0.5f))
            }, onBack, fold) {
                PeriodMark(pick, today, MaterialTheme.colorScheme.onSurfaceVariant, Modifier)
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Gap.l)) {
            item(key = "line") {
                Text(chartLine(data, period, span, bar, today, scoped.spentRub), Modifier.padding(horizontal = Gap.l).letters(TextRole.caption.fontSize), style = TextRole.caption)
                VSpace(ListSpace.cards)
            }
            if (perDay) dayRows(data, ops, onEdit) else categoryRows(data, scoped.categories, scoped.spentRub) { opened = it }
        }
    }
    opened?.let { item ->
        val list = remember(data, period, span, filter, item) { inPeriod(data, span ?: period, filter) { it.op.categoryId == item.id } }
        CategoryPurchases(data, item.name, item.amount, ReviewPrompt.title(span ?: period, today), list, onDismiss = { opened = null }) { opened = null; onEdit(it) }
    }
}

/** The period's expenses of the picked kind that [also] keeps, newest first. */
private fun inPeriod(data: AppData, period: Period, filter: Bucket?, also: (OperationFull) -> Boolean): List<OperationFull> =
    data.operations.filter { full ->
        val day = Ledger.localDate(full.op.timestamp, data.zone)
        full.op.type == OpType.EXPENSE && !day.isBefore(period.from) && !day.isAfter(period.to) &&
            (filter == null || Buckets.of(full.op, data.categoryById) == filter) && also(full)
    }.sortedByDescending { it.op.timestamp }

/** The caption names the quantity, like Можно сегодня and Всего на счетах: "Траты за октябрь", or "За 30 дней" for the last days. */
private fun periodCaption(period: Period, today: LocalDate): String {
    val span = ReviewPrompt.title(period, today)
    return when {
        period.to == today && span.startsWith(tr("последние ", "last ")) -> tr("За ", "Last ") + span.substringAfter(' ')
        span.startsWith("с ") -> tr("Траты ", "Spent ") + span
        else -> tr("Траты за ", "Spent in ") + span
    }
}

/** The card's line when no bar is picked: the caption's period, as a period: "За 30 дней", "За октябрь", "С 5 по 12 октября". */
private fun periodLine(period: Period, today: LocalDate): String {
    val caption = periodCaption(period, today)
    val spent = tr("Траты ", "Spent ")
    return if (caption.startsWith(spent)) caption.removePrefix(spent).replaceFirstChar { it.titlecase(I18n.locale) } else caption
}

/**
 * The kind as a title, the whole of it a tap to the menu of kinds, the current one checked. Its mark
 * goes by the title's size: 20 and 6 off a card's, 24 and 8 off a page's.
 */
@Composable
private fun KindTitle(pick: InsightsPick, modifier: Modifier, style: androidx.compose.ui.text.TextStyle, onCard: Boolean, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    val big = style.fontSize.value > TextRole.groupTitle.fontSize.value
    Box(modifier) {
        Row(
            (if (onCard) Modifier.fillMaxWidth().tapLine(style.fontSize).bleed(Gap.s) else Modifier)
                .clip(RoundedCornerShape(Gap.s))
                .clickable(enabled = enabled, onClickLabel = tr("Выбрать вид трат", "Pick the kind of spending")) { open = true }
                .padding(horizontal = if (onCard) Gap.s else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                kindName(pick.filter), Modifier.weight(1f, fill = false).semantics { heading() }.then(if (onCard) Modifier.letters(style.fontSize) else Modifier),
                style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(if (big) 8.dp else 6.dp))
            // On the middle of the small letters, taking none of their height.
            Box(Modifier.height(0.dp).offset(y = 1.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.requiredSize(if (big) 24.dp else 20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            listOf(null, Bucket.DAILY, Bucket.REGULAR, Bucket.BIG).forEach { kind ->
                DropdownMenuItem(
                    text = { Text(kindName(kind), style = TextRole.rowName) },
                    onClick = { open = false; pick.chooseKind(kind) },
                    trailingIcon = { if (kind == pick.filter) Icon(painterResource(R.drawable.ic_check), null) },
                )
            }
        }
    }
}

private fun kindName(kind: Bucket?): String = kind?.let(::bucketLabel) ?: tr("Все траты", "All spending")

/** The corner's mark: the calendar, or while a bar is picked, in its place, the cross that lets the bar go. */
@Composable
private fun PeriodMark(pick: InsightsPick, today: LocalDate, tint: Color, modifier: Modifier) {
    if (pick.bar == null) {
        PeriodMenu(pick, today, tint, modifier)
    } else {
        IconButton(onClick = pick::letBarGo, modifier = modifier) {
            Icon(painterResource(R.drawable.ic_close), tr("Снять выбор дня", "Clear the day"), tint = tint)
        }
    }
}

/** The calendar and what it opens: the two usual periods and a range of your own, the current one checked. */
@Composable
private fun PeriodMenu(pick: InsightsPick, today: LocalDate, tint: Color, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_calendar), tr("Период", "Period"), tint = tint)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            listOf(PeriodKind.WEEK to tr("Неделя", "Week"), PeriodKind.MONTH to tr("Месяц", "Month"), PeriodKind.CUSTOM to tr("Выбрать даты", "Pick dates")).forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = TextRole.rowName) },
                    onClick = { open = false; if (option == PeriodKind.CUSTOM) picking = true else pick.choosePeriod(option) },
                    trailingIcon = { if (option == pick.kind) Icon(painterResource(R.drawable.ic_check), null) },
                )
            }
        }
    }
    if (picking) {
        RangeDialog(pick.period(today), onDismiss = { picking = false }) { from, to ->
            pick.chooseRange(from, to)
            picking = false
        }
    }
}

/** How far a bar steps back towards what it stands on while another is picked. */
private const val QUIET = 0.62f

/** A category picked for its purchases: what the sheet is titled with, and whose they are. */
private data class OpenedCategory(val name: String, val amount: String, val id: Long?)

/** Every category, biggest first, a tap on one opening its purchases. */
private fun LazyListScope.categoryRows(data: AppData, all: List<CategorySpend>, spent: Long, onOpen: (OpenedCategory) -> Unit) {
    if (all.isEmpty()) {
        item { GroupRow(0, 1, Modifier.padding(horizontal = Gap.m)) { Text(tr("За эти дни трат нет", "No spending in these days"), style = TextRole.rowName) } }
    }
    all.forEachIndexed { i, spend ->
        item {
            val key = spend.categoryId?.let { data.categoryById[it]?.key }
            val tones = LocalTones.current
            val name = categoryName(data, spend)
            val amount = data.base.whole(spend.rubMinor)
            GroupRow(
                i, all.size, Modifier.padding(horizontal = Gap.m), onClick = { onOpen(OpenedCategory(name, amount, spend.categoryId)) },
                leading = { RowIcon(categoryIcon(key), if (key == null) leitmotifColor(null, tones) else categoryColor(key, tones)) },
                supporting = { Text(wholePercent(spend.rubMinor.toDouble() / spent.coerceAtLeast(1)), style = TextRole.caption) },
                trailing = { Text(amount, style = TextRole.rowAmount, color = MaterialTheme.colorScheme.onSurface) },
            ) { Text(name, style = TextRole.rowName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** What went on the picked day: the feed's rows, a tap opening one to edit. */
private fun LazyListScope.dayRows(data: AppData, ops: List<OperationFull>, onEdit: (OperationFull) -> Unit) {
    if (ops.isEmpty()) {
        item { GroupRow(0, 1, Modifier.padding(horizontal = Gap.m)) { Text(tr("В этот день трат нет", "No spending this day"), style = TextRole.rowName) } }
    }
    ops.forEachIndexed { i, op -> item { OperationRow(data, op, null, onEdit, i, ops.size, Modifier.padding(horizontal = Gap.m)) } }
}

/** The screen's end, the same whatever is picked: income, and what exchanges lost if there were any. */
private fun LazyListScope.incomeRows(data: AppData, income: Long, fxLoss: Long?) {
    val rows = listOfNotNull(tr("Доходы", "Income") to income, fxLoss?.let { tr("Потери на обмене", "Exchange losses") to it })
    item(key = "more") { VSpace(ListSpace.cards); CardTitle(tr("Доходы и обмен", "Income and exchange"), Modifier.padding(horizontal = Gap.m), icons = false) }
    rows.forEachIndexed { i, (label, rub) ->
        item {
            GroupRow(i + 1, rows.size + 1, Modifier.padding(horizontal = Gap.m)) {
                Row { Text(label, Modifier.weight(1f), style = TextRole.rowName); Text(data.base.approx(rub), style = TextRole.rowAmount) }
            }
        }
    }
}

/** How many reviews the screen shows; the rest are on their own page. */
private const val LAST_REVIEWS = 3

/** The reviews by the day they were written, what each looked at on its right; the "+" and the row of all lead to their page. */
private fun LazyListScope.reviewRows(data: AppData, reviews: List<Review>, onOpen: (Long) -> Unit, onAll: () -> Unit) {
    val last = reviews.take(LAST_REVIEWS)
    val more = reviews.size > LAST_REVIEWS
    val rows = last.size + if (more) 1 else 0
    item(key = "reviews") {
        VSpace(ListSpace.cards)
        GroupHeader(tr("Разборы", "Reviews"), tr("Сделать разбор", "Write a review"), onAll, Modifier.padding(horizontal = Gap.m), alone = rows == 0, icons = false)
    }
    last.forEachIndexed { i, review ->
        item(key = "review-${review.id}") {
            GroupRow(i + 1, rows + 1, Modifier.padding(horizontal = Gap.m), onClick = { onOpen(review.id) }) {
                Row {
                    Text(writtenOn(review, data), Modifier.weight(1f).alignByBaseline(), style = TextRole.rowName, maxLines = 1)
                    Text(ReviewPrompt.looked(Period(LocalDate.ofEpochDay(review.fromDay), LocalDate.ofEpochDay(review.toDay))), Modifier.alignByBaseline(), style = TextRole.caption, maxLines = 1)
                }
            }
        }
    }
    if (more) item(key = "reviews-all") {
        GroupRow(rows, rows + 1, Modifier.padding(horizontal = Gap.m), onClick = onAll) { Text(tr("Все разборы", "All reviews"), style = TextRole.rowName) }
    }
}

/** "24 октября", with the year when it was another. */
private fun writtenOn(review: Review, data: AppData): String {
    val day = Instant.ofEpochMilli(review.createdAt).atZone(data.zone).toLocalDate()
    return shortDate(day) + if (day.year == data.today.year) "" else " " + day.year
}

/** A category's purchases of the period, newest first, in a sheet; a tap opens one to edit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPurchases(data: AppData, name: String, amount: String, span: String, ops: List<OperationFull>, onDismiss: () -> Unit, onEdit: (OperationFull) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = tileColor()) {
        LazyColumn(contentPadding = PaddingValues(bottom = Gap.xl)) {
            item(key = "title") {
                Column(Modifier.padding(horizontal = Gap.l)) {
                    Text(name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                    Caption("$amount, " + span)
                }
            }
            operationItems(data, ops, null, onEdit)
        }
    }
}

private fun shortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(if (I18n.russian) "d MMMM" else "MMMM d", I18n.locale))

/**
 * A range of your own, full screen and edge to edge: close, "Период", "Готово", and the dates picked
 * shown big. One container colour from the status bar to the navigation bar, no divider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDialog(period: Period, onDismiss: () -> Unit, onPick: (LocalDate, LocalDate) -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = period.from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        initialSelectedEndDateMillis = period.to.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    val colors = DatePickerDefaults.colors(dividerColor = Color.Transparent)
    val container = colors.containerColor
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // The dialog's own window draws behind the bars: no scrim there, and icons that read on the container.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val light = container.luminance() > 0.5f
        SideEffect {
            window?.let {
                it.setDimAmount(0f)
                WindowCompat.getInsetsController(it, it.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
        }
        Scaffold(
            containerColor = container,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = container, scrolledContainerColor = container),
                    title = { Text(tr("Период", "Period")) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.ic_close), tr("Закрыть", "Close")) } },
                    actions = {
                        Box(Modifier.padding(end = Gap.s)) {
                            DoneButton(enabled = state.selectedStartDateMillis != null, onClick = {
                                val from = day(state.selectedStartDateMillis!!)
                                onPick(from, state.selectedEndDateMillis?.let(::day) ?: from)
                            })
                        }
                    },
                )
            },
        ) { padding ->
            DateRangePicker(
                state = state,
                modifier = Modifier.padding(padding).fillMaxSize(),
                colors = colors,
                title = null,
                headline = {
                    val from = state.selectedStartDateMillis?.let { day(it) }
                    val to = state.selectedEndDateMillis?.let { day(it) }
                    Text(
                        when {
                            from == null -> tr("Выбери начало", "Pick the start")
                            to == null -> tr("С ", "From ") + shortDate(from)
                            else -> ReviewPrompt.span(from, to).replaceFirstChar { it.titlecase(I18n.locale) }
                        },
                        modifier = Modifier.padding(start = Gap.l, end = Gap.l, top = Gap.m, bottom = Gap.m),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                },
                showModeToggle = false,
            )
        }
    }
}

/** How tall the bars stand: low enough for the categories to start on the first screen. */
private val BarsHeight: Dp = 96.dp

/** Between two bars. */
private val BarGap: Dp = 4.dp

/** The thinnest a layer of a bar gets: still seen, whatever its share. */
private val BarLayer: Dp = 4.dp

/** A bar's corners, at both ends. */
private val BarCorner: Dp = 6.dp

/**
 * The bars in the card, each a rounded block stacked in its categories, colours touching. A tap picks a bar and a tap on it lets go:
 * only a tap, so a finger that moves still scrolls the screen or swipes the tabs from anywhere on the
 * chart. The picked bar stays bright, the others step back.
 * The dashed line is the daily budget.
 */
@Composable
private fun DayBars(
    days: List<Pair<LocalDate, Long>>, bar: Analytics.Bar, last: LocalDate, budget: Long?, base: Base, stacks: Map<LocalDate, List<DayPart>>,
    picked: LocalDate?, onTap: (LocalDate) -> Unit,
) {
    if (days.isEmpty()) return
    val pickedIndex = days.indexOfFirst { it.first == picked }
    val normal = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val rest = MaterialTheme.colorScheme.onSurface.copy(alpha = RestAlpha)
    val faint = MaterialTheme.colorScheme.outlineVariant
    val dash = MaterialTheme.colorScheme.outline
    val card = tileColor()
    val top = maxOf(days.maxOf { it.second }, budget ?: 0L).coerceAtLeast(1)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val gapPx = with(density) { BarGap.toPx() }
        val step = (widthPx + gapPx) / days.size
        val barWidth = step - gapPx
        fun at(px: Float) = days[(px / step).toInt().coerceIn(0, days.lastIndex)].first
        Column {
            // A bar's column to tap, from over it down through its label.
            Column(Modifier.pointerInput(days, picked) { detectTapGestures { onTap(at(it.x)) } }) {
                Canvas(Modifier.fillMaxWidth().height(BarsHeight).testTag(Tags.INSIGHTS_BARS).semantics(mergeDescendants = true) {}) {
                    days.forEachIndexed { i, (day, value) ->
                        val h = (if (value > 0) (size.height * value / top).coerceAtLeast(4.dp.toPx()) else 3.dp.toPx())
                        val left = step * i
                        fun shown(color: Color) = if (pickedIndex < 0 || i == pickedIndex) color else lerp(color, card, QUIET)
                        // Never more than half its height: a low bar stays a bar, not an oval.
                        val radius = CornerRadius(minOf(BarCorner.toPx(), h / 2, barWidth / 2))
                        val path = Path().apply { addRoundRect(RoundRect(left, size.height - h, left + barWidth, size.height, radius)) }
                        val parts = stacks[day].orEmpty()
                        if (value > 0 && parts.isNotEmpty()) {
                            // At most three layers, biggest at the bottom: the first ones in their colours, the rest quiet.
                            val own = Analytics.coloured(parts.size)
                            val colours = parts.take(own).map { it.color } + if (own < parts.size) listOf(rest) else emptyList()
                            val amounts = parts.take(own).map { it.rub } + if (own < parts.size) listOf(parts.drop(own).sumOf { it.rub }) else emptyList()
                            val heights = Analytics.layerHeights(amounts, h, BarLayer.toPx())
                            clipPath(path) {
                                var y = size.height
                                colours.forEachIndexed { n, color ->
                                    drawRect(shown(color), Offset(left, y - heights[n]), Size(barWidth, heights[n]))
                                    y -= heights[n]
                                }
                            }
                        } else {
                            drawPath(path, shown(if (value > 0) normal else faint))
                        }
                    }
                    budget?.let {
                        val y = size.height - size.height * it / top
                        drawLine(dash, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                    }
                }
                VSpace(ChartSpace.barsToLabels)
                // Wider than a bar, centred on it; where bars are narrower than their words, every second or third.
                val stepDp = with(density) { step.toDp() }
                val wide = when (bar) {
                    Analytics.Bar.WEEK -> 56.dp
                    Analytics.Bar.YEAR -> 30.dp
                    else -> 24.dp
                }
                val every = kotlin.math.ceil(wide / stepDp).toInt().coerceAtLeast(1)
                Box(Modifier.fillMaxWidth()) {
                    days.forEachIndexed { i, (d, _) ->
                        if ((days.lastIndex - i) % every == 0) {
                            Text(
                                barMark(d, Analytics.barEnd(d, last, bar), bar, days.size),
                                modifier = Modifier.offset(x = with(density) { (step * i + barWidth / 2).toDp() } - 36.dp).width(72.dp).letters(MaterialTheme.typography.labelSmall.fontSize), // scale-exempt: as below
                                style = MaterialTheme.typography.labelSmall, // scale-exempt: a chart's axis, under bars a few dp wide
                                color = if (i == pickedIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            // What the dashed line is: the plan for a day. A pick within a kind keeps it or its absence.
            budget?.let {
                VSpace(ChartSpace.labelsToPlan)
                Text(tr("План ", "Plan ") + base.approx(it) + tr(" в день", " a day"), Modifier.letters(TextRole.caption.fontSize), style = TextRole.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun monthShort(date: LocalDate): String = date.month.getDisplayName(TextStyle.SHORT_STANDALONE, I18n.locale).take(3)

/** Under a bar, as short as its width asks: "Пн", "24", "5–11 окт", "окт", "2026". */
private fun barMark(start: LocalDate, end: LocalDate, bar: Analytics.Bar, bars: Int): String = when (bar) {
    // Two letters: "Пн Вт Ср", never the ambiguous single "В".
    Analytics.Bar.DAY -> if (bars <= 7) start.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, I18n.locale).take(2).replaceFirstChar { it.titlecase(I18n.locale) } else start.dayOfMonth.toString()
    Analytics.Bar.WEEK -> if (start == end) dayMonth(start) else if (I18n.russian) "${start.dayOfMonth}–${dayMonth(end)}" else "${dayMonth(start)}–${end.dayOfMonth}"
    Analytics.Bar.MONTH -> monthShort(start)
    Analytics.Bar.YEAR -> start.year.toString()
}

/** The period's line for a picked bar: "За субботу, 24 октября", "За 12–18 октября", "За октябрь 2026". */
private fun barCaption(start: LocalDate, end: LocalDate, bar: Analytics.Bar): String {
    val date = DateTimeFormatter.ofPattern(if (I18n.russian) "d MMMM" else "MMMM d", I18n.locale)
    val wholeMonth = start.dayOfMonth == 1 && end.dayOfMonth == end.lengthOfMonth()
    return when {
        bar == Analytics.Bar.DAY -> if (I18n.russian) "За " + WeekdayAccusative[start.dayOfWeek.value - 1] + ", " + start.format(date) else start.dayOfWeek.getDisplayName(TextStyle.FULL, I18n.locale) + ", " + start.format(date)
        bar == Analytics.Bar.YEAR -> tr("За ", "") + start.year
        bar == Analytics.Bar.MONTH && wholeMonth -> tr("За ", "") + start.month.getDisplayName(TextStyle.FULL_STANDALONE, I18n.locale) + " " + start.year
        start == end -> tr("За ", "") + start.format(date)
        start.month == end.month -> if (I18n.russian) "За ${start.dayOfMonth}–" + end.format(date) else start.format(date) + "–" + end.dayOfMonth
        else -> tr("За ", "") + start.format(date) + " – " + end.format(date)
    }
}

/** "За субботу": the weekdays as they stand after "за". */
private val WeekdayAccusative = listOf("понедельник", "вторник", "среду", "четверг", "пятницу", "субботу", "воскресенье")

private fun categoryName(data: AppData, spend: CategorySpend): String =
    spend.categoryId?.let { data.categoryById[it]?.label() } ?: tr("Без категории", "No category")

/** One category's share of a bar: its colour, its name and what went on it, in rubles (kopecks). */
data class DayPart(val color: Color, val name: String, val rub: Long)
