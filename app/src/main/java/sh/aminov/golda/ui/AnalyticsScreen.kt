package sh.aminov.golda.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
import sh.aminov.golda.domain.Analytics
import sh.aminov.golda.domain.Base
import sh.aminov.golda.domain.Buckets
import sh.aminov.golda.domain.CategorySpend
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.KindBlock
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
 * The period's spending on one screen: the header with the total, the mosaic of the three kinds, and
 * one card under it, bars over categories, which the picked kind narrows as a whole. A bar picked,
 * the categories give way to that day's purchases. Income and exchange close the screen, whatever is picked.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnalyticsScreen(data: AppData, padding: PaddingValues, onEdit: (OperationFull) -> Unit = {}, onReviews: (Period) -> Unit) {
    val today = data.today
    var kind by rememberSaveable { mutableStateOf(PeriodKind.MONTH) }
    // Saved like the kind it belongs to (as two epoch days), so the two never disagree after a restart.
    var customDays by rememberSaveable { mutableStateOf<LongArray?>(null) }
    val custom = customDays?.let { (from, to) -> Period(LocalDate.ofEpochDay(from), LocalDate.ofEpochDay(to)) }
    var picking by remember { mutableStateOf(false) }
    // A kind picked narrows everything under the mosaic and the header's figures; tapped again, back to all.
    var filter by rememberSaveable { mutableStateOf<Bucket?>(null) }
    // A category tapped: its purchases of the period in a sheet.
    var opened by remember { mutableStateOf<OpenedCategory?>(null) }
    val period = Analytics.period(kind, today, custom)
    // Belongs to what is on the card: another kind or period lets it go.
    var pickedDay by rememberSaveable(period, filter) { mutableStateOf<Long?>(null) }
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
    val day = pickedDay?.let(LocalDate::ofEpochDay)
    // What the picked bar spans. A day is few purchases, so they are listed; a longer bar is many, so
    // its categories stand in for the period's: the same rows, narrowed.
    val span = day?.let { Period(it, Analytics.barEnd(it, last, bar)) }
    val dayOps = remember(data, filter, span, bar) { if (span == null || bar != Analytics.Bar.DAY) emptyList() else inPeriod(data, span, filter) { true } }
    val spanned = remember(kept, span, bar) {
        if (span == null || bar == Analytics.Bar.DAY) null else Analytics.report(kept, data.accountById, data.categoryById, span, today, data.zone, data.rates)
    }
    // A pick shortens the list, and at the screen's end that would pull the chart from under the finger.
    // What the list loses, the page keeps as room at its very end, so its length never changes.
    var rowHeight by remember { mutableStateOf(0) }
    val rowsNow = if (day != null && bar == Analytics.Bar.DAY) dayOps.size else (spanned ?: shown).categories.size
    val held = (report.categories.size - rowsNow.coerceAtLeast(1)).coerceAtLeast(0) * rowHeight
    val density = LocalDensity.current

    // The whole page, not just the content: a short list left the pager without a back swipe here.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.aboveActions()) {
        item(key = "header") {
            InsightsHeader(
                title = periodCaption(period, today),
                figure = data.base.whole(shown.spentRub),
                average = "≈ " + data.base.approx(shown.averagePerDayRub),
                kind = kind,
                onKind = { if (it == PeriodKind.CUSTOM) picking = true else kind = it },
                onReview = { onReviews(period) },
            )
        }
        if (report.categories.isEmpty()) {
            item(key = "empty") {
                VSpace(ListSpace.cards)
                GroupRow(0, 1, Modifier.padding(horizontal = Gap.m)) { Text(tr("За эти дни трат нет", "No spending in these days"), style = TextRole.rowName) }
            }
        } else {
            item(key = "kinds") {
                VSpace(ListSpace.cards)
                KindMosaic(report.regularRub, report.dailyRub, report.bigRub, data.base, filter) { filter = Analytics.toggled(filter, it) }
            }
            item(key = "title") {
                VSpace(ListSpace.cards)
                val nothing = shown.categories.isEmpty()
                CardTitle(
                    filter?.let(::bucketLabel) ?: tr("Все траты", "All spending"), Modifier.padding(horizontal = Gap.m), tight = !nothing,
                    trailing = if (day == null && filter == null) null else ({
                        // The day first, then the kind: one tap, one step back. Its glyph's right edge on the card's text.
                        IconButton(onClick = { if (day != null) pickedDay = null else filter = null }, modifier = Modifier.offset(x = 8.dp)) {
                            Icon(painterResource(R.drawable.ic_close), if (day != null) tr("Снять выбор дня", "Clear the day") else tr("Снять выбор вида", "Clear the kind"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }),
                )
            }
            if (shown.categories.isEmpty()) {
                item(key = "none") {
                    GroupRow(1, 2, Modifier.padding(horizontal = Gap.m)) { Text(tr("За эти дни трат нет", "No spending in these days"), style = TextRole.rowName) }
                }
            } else {
                item(key = "bars") {
                    // A part of the card between its title and its rows: no corners of its own.
                    Surface(Modifier.padding(horizontal = Gap.m).bleed(Gap.s).fillMaxWidth(), color = tileColor()) {
                        // The first row brings half its gap over its circle.
                        Box(Modifier.padding(start = Space.card, end = Space.card, bottom = ChartSpace.labelsToList - ListSpace.circles / 2 - ChartSpace.rowOverCircle)) {
                            val picked = span?.let { barCaption(it.from, it.to, bar) to (spanned?.spentRub ?: Analytics.bars(shown.days).firstOrNull { b -> b.first == day }?.second ?: 0L) }
                            val (words, rub) = picked ?: (periodLine(period, today) to shown.spentRub)
                            DayBars(
                                Analytics.bars(shown.days), bar, last, plan, data.base, barStacks, day, "$words · ${data.base.whole(rub)}",
                                onTap = { pickedDay = Analytics.toggled(day, it)?.toEpochDay() },
                            )
                        }
                    }
                }
                if (day != null && bar == Analytics.Bar.DAY) dayRows(data, dayOps, onEdit)
                else (spanned ?: shown).let { rows -> categoryRows(data, rows.categories, rows.spentRub, { rowHeight = maxOf(rowHeight, it) }) { opened = it } }
            }
            // Income and exchange are not spending: no kind narrows them.
        }
        incomeRows(data, report.incomeRub, report.fxLossRub.takeIf { report.fxVolumeRub > 0 })
        if (held > 0) item(key = "held") { VSpace(with(density) { held.toDp() }) }
    }

    opened?.let { item ->
        val ops = remember(data, period, span, filter, item) { inPeriod(data, span ?: period, filter) { it.op.categoryId == item.id } }
        CategoryPurchases(data, item.name, item.amount, ReviewPrompt.title(span ?: period, today), ops, onDismiss = { opened = null }) { opened = null; onEdit(it) }
    }
    if (picking) {
        RangeDialog(period, onDismiss = { picking = false }) { from, to ->
            customDays = longArrayOf(from.toEpochDay(), to.toEpochDay())
            kind = PeriodKind.CUSTOM
            picking = false
        }
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

/** The review's mark sits on the line of the average: its 24 centred on the letters, whose height is about 16. */
private val ReviewLift = (-4).dp

/**
 * What the period came to: the caption with the calendar on its line, the figure, the average with
 * the way to the reviews on its own. Both marks' right edges on the content's, like Home's gear.
 */
@Composable
private fun InsightsHeader(title: String, figure: String, average: String, kind: PeriodKind, onKind: (PeriodKind) -> Unit, onReview: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onPrimaryFixed
    val words = TextRole.caption
    val figures = TextRole.headerFigures
    HeroTile(Modifier.padding(horizontal = Gap.s), vertical = HeaderSpace.edge) {
        Row(Modifier.letters(words.fontSize)) {
            Text(title, Modifier.weight(1f), style = words, maxLines = 1, overflow = TextOverflow.Ellipsis)
            PeriodMenu(kind, onKind, Modifier.hang())
        }
        VSpace(HeaderSpace.figureCaption)
        BoxWithConstraints { HeroAmount(figure, ink, Modifier.testTag(Tags.INSIGHTS_TOTAL).letters(heroFigureSize(figure, maxWidth)), signColor = ink) }
        VSpace(HeaderSpace.block)
        Text(tr("В среднем в день", "On average a day"), Modifier.letters(words.fontSize), style = words)
        VSpace(HeaderSpace.valueCaption)
        Row(Modifier.letters(figures.fontSize)) {
            Text(average, Modifier.weight(1f), style = figures, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onReview, modifier = Modifier.hang().offset(y = ReviewLift)) {
                Icon(painterResource(R.drawable.ic_review), tr("Разбор", "Review"), tint = scheme.onPrimaryFixedVariant)
            }
        }
    }
}

/** The calendar and what it opens: the two usual periods and a range of your own, the current one checked. */
@Composable
private fun PeriodMenu(kind: PeriodKind, onKind: (PeriodKind) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_calendar), tr("Период", "Period"), tint = MaterialTheme.colorScheme.onPrimaryFixedVariant)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            listOf(PeriodKind.WEEK to tr("Неделя", "Week"), PeriodKind.MONTH to tr("Месяц", "Month"), PeriodKind.CUSTOM to tr("Выбрать даты", "Pick dates")).forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = TextRole.rowName) },
                    onClick = { open = false; onKind(option) },
                    trailingIcon = { if (option == kind) Icon(painterResource(R.drawable.ic_check), null) },
                )
            }
        }
    }
}

/** Its colour stays with the kind from one period to the next, whichever is the biggest. */
private val KindRank = mapOf(Bucket.REGULAR to 0, Bucket.DAILY to 1, Bucket.BIG to 2)

/** How far a tile or a bar steps back towards what it stands on while another is picked. */
private const val QUIET = 0.62f

/** The right column's least width: the longest name and a sum in the millions, with room to breathe. */
private const val KIND_COLUMN = 128f

/** A column's least width: the longest name and a sum in the millions. */
private const val KIND_LEAST = 116f

/**
 * The three kinds, 136 high, the area their share: the biggest on the left, the two others stacked,
 * or three columns when the shares read as equal ([KindBlock]). A tile is the filter of all under it;
 * picked, it keeps its colour and the others step back.
 */
@Composable
private fun KindMosaic(regular: Long, daily: Long, big: Long, base: Base, filter: Bucket?, onPick: (Bucket) -> Unit) {
    val kinds = listOf(Bucket.REGULAR to regular, Bucket.DAILY to daily, Bucket.BIG to big).sortedByDescending { it.second }
    val gap = 4.dp
    val density = LocalDensity.current
    // Room for two lines, grown with the font scale.
    val high = 136.dp * density.fontScale
    val tile: @Composable (Pair<Bucket, Long>, Modifier) -> Unit = { (bucket, rub), modifier ->
        KindTile(bucket, base.whole(rub), rub == 0L, filter == bucket, filter != null && filter != bucket, modifier) { onPick(bucket) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Gap.s)) {
        val columns = KindBlock.columns(kinds[0].second, kinds[1].second, kinds[2].second, maxWidth.value, gap.value, KIND_LEAST * density.fontScale)
        val split = KindBlock.split(kinds[0].second, kinds[1].second, kinds[2].second, maxWidth.value, high.value, gap.value, KIND_COLUMN * density.fontScale, 56f * density.fontScale)
        Row(Modifier.height(high), horizontalArrangement = Arrangement.spacedBy(gap)) {
            if (columns != null) {
                kinds.forEachIndexed { i, kind -> tile(kind, Modifier.width(columns[i].dp).height(high)) }
            } else {
                tile(kinds[0], Modifier.width(split.left.dp).height(high))
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    tile(kinds[1], Modifier.fillMaxWidth().height(high - gap - split.low.dp))
                    tile(kinds[2], Modifier.fillMaxWidth().height(split.low.dp))
                }
            }
        }
    }
}

/**
 * One kind: its name and sum on its colour, or on that colour stepped back when another is picked.
 * With nothing in it ([empty]) it has no colour to show: the neutral soft, still a tile to tap, or a pick could not be let go.
 */
@Composable
private fun KindTile(bucket: Bucket, amount: String, empty: Boolean, on: Boolean, quiet: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tones = LocalTones.current
    val colour = leitmotifColor(KindRank.getValue(bucket), tones)
    val spec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val target = when {
        empty -> tones.soft
        quiet -> lerp(colour, MaterialTheme.colorScheme.surface, QUIET)
        else -> colour
    }
    val fill by animateColorAsState(target, spec, label = "kind")
    val ink = if (empty) tones.text else tileInk(fill)
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(fill)
            .selectable(selected = on, role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
    ) {
        Column {
            Text(bucketLabel(bucket), Modifier.letters(TextRole.caption.fontSize), style = TextRole.caption, color = ink, maxLines = 1)
            VSpace(Gap.s)
            Text(amount, Modifier.letters(TextRole.rowAmount.fontSize), style = TextRole.rowAmount, color = ink, maxLines = 1)
        }
    }
}

/** A category picked for its purchases: what the sheet is titled with, and whose they are. */
private data class OpenedCategory(val name: String, val amount: String, val id: Long?)

/** Every category, biggest first, a tap on one opening its purchases. */
private fun LazyListScope.categoryRows(data: AppData, all: List<CategorySpend>, spent: Long, onHeight: (Int) -> Unit, onOpen: (OpenedCategory) -> Unit) {
    if (all.isEmpty()) {
        item { GroupRow(1, 2, Modifier.padding(horizontal = Gap.m)) { Text(tr("За эти дни трат нет", "No spending in these days"), style = TextRole.rowName) } }
    }
    all.forEachIndexed { i, spend ->
        item {
            val key = spend.categoryId?.let { data.categoryById[it]?.key }
            val tones = LocalTones.current
            val name = categoryName(data, spend)
            val amount = data.base.whole(spend.rubMinor)
            GroupRow(
                i + 1, all.size + 1, Modifier.padding(horizontal = Gap.m).onSizeChanged { if (i in 1 until all.lastIndex) onHeight(it.height) }, onClick = { onOpen(OpenedCategory(name, amount, spend.categoryId)) },
                leading = { RowIcon(categoryIcon(key), if (key == null) leitmotifColor(null, tones) else categoryColor(key, tones)) },
                supporting = { Text(wholePercent(spend.rubMinor.toDouble() / spent.coerceAtLeast(1)), style = TextRole.caption) },
                trailing = { Text(amount, style = TextRole.rowAmount, color = MaterialTheme.colorScheme.onSurface) },
            ) { Text(name, style = TextRole.rowName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** What went on the picked day, in place of the categories: the feed's rows, a tap opening one to edit. */
private fun LazyListScope.dayRows(data: AppData, ops: List<OperationFull>, onEdit: (OperationFull) -> Unit) {
    if (ops.isEmpty()) {
        item { GroupRow(1, 2, Modifier.padding(horizontal = Gap.m)) { Text(tr("В этот день трат нет", "No spending this day"), style = TextRole.rowName) } }
    }
    ops.forEachIndexed { i, op -> item { OperationRow(data, op, null, onEdit, i + 1, ops.size + 1, Modifier.padding(horizontal = Gap.m)) } }
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

/** Between two bars: the mosaic's own gap. */
private val BarGap: Dp = 4.dp

/** The thinnest a layer of a bar gets: still seen, whatever its share. */
private val BarLayer: Dp = 4.dp

/** A bar's corners, at both ends. */
private val BarCorner: Dp = 6.dp

/**
 * The bars in the card, each a rounded block stacked in its categories, colours touching, under [caption]:
 * when and how much, always there, so the bars never jump. A tap picks a bar and a tap on it lets go:
 * only a tap, so a finger that moves still scrolls the screen or swipes the tabs from anywhere on the
 * chart. The picked bar stays bright, the others step back.
 * The dashed line is the daily budget.
 */
@Composable
private fun DayBars(
    days: List<Pair<LocalDate, Long>>, bar: Analytics.Bar, last: LocalDate, budget: Long?, base: Base, stacks: Map<LocalDate, List<DayPart>>,
    picked: LocalDate?, caption: String, onTap: (LocalDate) -> Unit,
) {
    if (days.isEmpty()) return
    val pickedIndex = days.indexOfFirst { it.first == picked }
    val normal = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val rest = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val faint = MaterialTheme.colorScheme.outlineVariant
    val line = MaterialTheme.colorScheme.outline
    val card = tileColor()
    val top = maxOf(days.maxOf { it.second }, budget ?: 0L).coerceAtLeast(1)
    val density = LocalDensity.current
    // The bars rise when the period or the kind changes, left to right in a short wave.
    val grow = remember(days) { Animatable(0f) }
    val rise = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(days) { grow.animateTo(1f, rise) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val gapPx = with(density) { BarGap.toPx() }
        val step = (widthPx + gapPx) / days.size
        val barWidth = step - gapPx
        fun at(px: Float) = days[(px / step).toInt().coerceIn(0, days.lastIndex)].first
        Column {
            VSpace(ChartSpace.titleToLine - ChartSpace.titleDescent)
            Text(caption, Modifier.letters(TextRole.caption.fontSize), style = TextRole.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            VSpace(ChartSpace.lineToBars)
            // A bar's column to tap, from over it down through its label.
            Column(Modifier.pointerInput(days, picked) { detectTapGestures { onTap(at(it.x)) } }) {
                Canvas(Modifier.fillMaxWidth().height(BarsHeight).testTag(Tags.INSIGHTS_BARS)) {
                    days.forEachIndexed { i, (day, value) ->
                        val g = (grow.value * 1.5f - 0.5f * i / days.size).coerceIn(0f, 1f)
                        val h = (if (value > 0) (size.height * value / top).coerceAtLeast(4.dp.toPx()) else 3.dp.toPx()) * g
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
                        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
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
            // What the dashed line is: the plan for a day.
            budget?.let {
                VSpace(ChartSpace.labelsToPlan)
                Text(tr("План ", "Plan ") + base.approx(it) + tr(" в день", " a day"), Modifier.letters(TextRole.caption.fontSize), style = TextRole.caption)
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

/** The card's line for a picked bar: "За субботу, 24 октября", "За 12–18 октября", "За октябрь 2026". */
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
