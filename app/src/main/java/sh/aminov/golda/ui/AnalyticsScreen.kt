package sh.aminov.golda.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import sh.aminov.golda.domain.Treemap
import sh.aminov.golda.domain.Ledger
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import sh.aminov.golda.domain.ReviewPrompt
import sh.aminov.golda.R
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.border
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import sh.aminov.golda.data.OpType
import sh.aminov.golda.domain.Buckets
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import sh.aminov.golda.data.Bucket
import sh.aminov.golda.domain.Analytics
import sh.aminov.golda.domain.Base
import androidx.compose.ui.platform.testTag
import sh.aminov.golda.domain.Budget
import sh.aminov.golda.domain.CategorySpend
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Period
import sh.aminov.golda.domain.PeriodKind
import sh.aminov.golda.domain.label
import sh.aminov.golda.domain.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The period's spending on one screen: a header tile with the total, the everyday average, the
 * buckets (which filter everything under them) and "Разбор"; the categories as bars; the days; and
 * income and exchange losses in one line.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnalyticsScreen(data: AppData, padding: PaddingValues, onReviews: (Period) -> Unit) {
    val today = data.today
    var kind by rememberSaveable { mutableStateOf(PeriodKind.MONTH) }
    // Saved like the kind it belongs to (as two epoch days), so the two never disagree after a restart.
    var customDays by rememberSaveable { mutableStateOf<LongArray?>(null) }
    val custom = customDays?.let { (from, to) -> Period(LocalDate.ofEpochDay(from), LocalDate.ofEpochDay(to)) }
    var picking by remember { mutableStateOf(false) }
    // A bucket picked narrows the bars and the days to its spending; tapped again, back to all.
    var filter by rememberSaveable { mutableStateOf<Bucket?>(null) }
    val period = Analytics.period(kind, today, custom)
    val report = remember(data, period) { Analytics.report(data.operations, data.accountById, data.categoryById, period, today, data.zone, data.rates) }
    val shown = remember(data, period, filter) {
        val bucket = filter ?: return@remember report
        val only = data.operations.filter { it.op.type == OpType.EXPENSE && Buckets.of(it.op, data.categoryById) == bucket }
        // The day bars count everyday spending only; for another bucket every expense of it is a bar.
        val asDaily = data.categoryById.mapValues { (_, c) -> c.copy(bucket = Bucket.DAILY) }
        Analytics.report(only.map { it.copy(op = it.op.copy(bucket = null)) }, data.accountById, asDaily, period, today, data.zone, data.rates)
    }
    // Each day's spending by category, biggest first: the day bars are stacked in the categories' colours.
    val tones = LocalTones.current
    // Each category's colour by its rank in this period's whole spending (not the picked bucket's), so
    // a filter never repaints a category: the biggest in the theme's hue.
    val colours = remember(report, tones) {
        report.categories.withIndex().associate { (rank, spend) ->
            val key = spend.categoryId?.let { data.categoryById[it]?.key }
            key to leitmotifColor(if (key == null) null else rank, tones)
        }
    }
    val stacks = remember(data, period, filter, tones, colours) {
        val want = filter ?: Bucket.DAILY
        data.operations
            .filter { it.op.type == OpType.EXPENSE && Buckets.of(it.op, data.categoryById) == want }
            .groupBy { Ledger.localDate(it.op.timestamp, data.zone) }
            .mapValues { (_, ops) ->
                ops.groupBy { it.op.categoryId?.let(data.categoryById::get)?.key }
                    .map { (key, list) -> (colours[key] ?: leitmotifColor(null, tones)) to -list.sumOf { o -> o.postings.sumOf { p -> p.rubMinor } } }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
            }
    }
    // The golden-ratio steps: 14 for words around the figure, 22 for the main line, the figure up to 94.
    val text = MaterialTheme.typography.bodyMedium.merge(Tnum)
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant

    // The whole page, not just the content: a short list left the pager without a back swipe here.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.aboveActions(), verticalArrangement = Arrangement.spacedBy(Gap.m)) {
        item(key = "header") {
            HeroTile(Modifier.padding(horizontal = Gap.s)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The caption names the quantity, like Можно сегодня and Всего на счетах: "Траты за октябрь".
                    // "За 30 дней" for the last days: "Траты за последние 30 дней" crowds the button.
                    val span = ReviewPrompt.title(period, today)
                    val recent = period.to == today && span.startsWith(tr("последние ", "last "))
                    Text(
                        when {
                            recent -> tr("За ", "Last ") + span.substringAfter(' ')
                            span.startsWith("с ") -> tr("Траты ", "Spent ") + span
                            else -> tr("Траты за ", "Spent in ") + span
                        },
                        Modifier.weight(1f), style = text, color = quiet, maxLines = 1,
                    )
                    // The one ice-blue thing in the app: the way to Gemini's reviews.
                    Button(
                        onClick = { onReviews(period) },
                        colors = ButtonDefaults.buttonColors(containerColor = iceColor(), contentColor = onIceColor()),
                        contentPadding = PaddingValues(horizontal = Gap.m),
                        // Hangs over the caption's line, so the caption sits as close to the figure as on every hero.
                        modifier = Modifier.overhang(0.dp).height(40.dp),
                    ) { Text(tr("Разбор", "Review"), style = MaterialTheme.typography.labelLarge) }
                }
                VSpace(Gap.m)
                HeroAmount(data.base.whole(shown.spentRub), MaterialTheme.colorScheme.onSurface, Modifier.testTag(Tags.INSIGHTS_TOTAL))
                // The average of what is shown: the picked bucket's, or everyday spending's.
                Text(tr("В среднем ${data.base.approx(shown.averagePerDayRub)} в день", "${data.base.approx(shown.averagePerDayRub)} a day on average"), style = text, color = quiet)
                VSpace(Gap.l)
                Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                    listOf(Bucket.DAILY to report.dailyRub, Bucket.REGULAR to report.regularRub, Bucket.BIG to report.bigRub).forEach { (bucket, rub) ->
                        BucketChip(bucketLabel(bucket), data.base.whole(rub), filter == bucket, Modifier.weight(1f)) {
                            filter = if (filter == bucket) null else bucket
                        }
                    }
                }
            }
        }
        item(key = "period") {
            ChoiceGroup(
                options = listOf(PeriodKind.WEEK, PeriodKind.MONTH, PeriodKind.CUSTOM),
                selected = kind,
                onSelect = { if (it == PeriodKind.CUSTOM) picking = true else kind = it },
                // On the hero's edge, 8 from the screen.
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gap.s),
                height = 40.dp,
            ) {
                when (it) {
                    PeriodKind.WEEK -> ChoiceText(tr("Неделя", "Week"))
                    PeriodKind.MONTH -> ChoiceText(tr("Месяц", "Month"))
                    PeriodKind.CUSTOM -> Icon(painterResource(R.drawable.ic_calendar), tr("Свой период", "Custom range"), Modifier.size(20.dp))
                }
            }
        }
        if (shown.categories.isEmpty()) {
            item(key = "empty") {
                Text(tr("За эти дни трат нет", "No spending in these days"), Modifier.padding(Gap.l), style = text, color = quiet)
            }
        } else {
            item(key = "mosaic") {
                // The categories as a mosaic: each a tile in its colour, as big as its share.
                val items = remember(shown, colours) {
                    shown.categories.map { spend ->
                        MosaicItem(categoryName(data, spend), data.base.whole(spend.rubMinor), spend.rubMinor, colours[spend.categoryId?.let { data.categoryById[it]?.key }] ?: leitmotifColor(null, tones))
                    }
                }
                Mosaic(items, Modifier.padding(horizontal = Gap.s))
            }
            item(key = "chart") {
                Tile(Modifier.fillMaxWidth().padding(horizontal = Gap.s)) {
                    DayBars(shown.days, today, kind, data.envelope?.let { e -> data.rates.rubMinor(e.perDayMinor, e.currency) }?.takeIf { it > 0 && filter in listOf(null, Bucket.DAILY) }, data.base, stacks)
                }
            }
        }
        item(key = "more") {
            Tile(Modifier.fillMaxWidth().padding(horizontal = Gap.s)) {
                LabelAmount(tr("Доходы", "Income"), data.base.approx(report.incomeRub))
                if (report.fxVolumeRub > 0) {
                    VSpace(Gap.m)
                    LabelAmount(tr("Потери на обмене", "Exchange losses"), data.base.approx(report.fxLossRub))
                }
            }
        }
    }

    if (picking) {
        RangeDialog(period, onDismiss = { picking = false }) { from, to ->
            customDays = longArrayOf(from.toEpochDay(), to.toEpochDay())
            kind = PeriodKind.CUSTOM
            picking = false
        }
    }
}

/** A bucket as a small tile: its name and amount; picked, it is dark and filters what follows. */
@Composable
private fun BucketChip(label: String, amount: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val spec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    // Idle on the white hero is the page's grey: one step off what it sits on.
    val container by animateColorAsState(if (on) pickedColor() else MaterialTheme.colorScheme.surface, spec, label = "bucket")
    val ink by animateColorAsState(if (on) onPickedColor() else MaterialTheme.colorScheme.onSurface, spec, label = "bucket-ink")
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), color = container, contentColor = ink) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            VSpace(2.dp)
            Text(amount, style = MaterialTheme.typography.titleMedium.merge(Tnum).copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
    }
}

/** A category in the [Mosaic]. */
private data class MosaicItem(val name: String, val amount: String, val rub: Long, val color: Color)

/**
 * The categories as a squarified treemap: the biggest share the biggest tile, in each category's
 * own colour, its name, amount and share written on it when it has room. Tiles too small to carry
 * words are named in one quiet line under the mosaic. They settle in one after another.
 */
@Composable
private fun Mosaic(items: List<MosaicItem>, modifier: Modifier = Modifier) {
    val total = items.sumOf { it.rub }.coerceAtLeast(1)
    val gap = 4.dp
    val motion = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val density = LocalDensity.current
    // Room for three lines, grown with the font scale.
    val wordsW = 96.dp * density.fontScale
    val wordsH = 84.dp * density.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { 260.dp.toPx() }
        val tiles = remember(items, w, h) { Treemap.layout(items.map { it.rub }, w, h) }
        val roomy = remember(tiles, density) {
            tiles.associate { t -> t.index to with(density) { t.width.toDp() >= wordsW && t.height.toDp() >= wordsH } }
        }
        Column {
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                tiles.forEachIndexed { order, tile ->
                    val item = items[tile.index]
                    val shown = remember(item.name) { Animatable(0f) }
                    LaunchedEffect(item.name) { delay(order * 50L); shown.animateTo(1f, motion) }
                    // Ink: the tile's own colour, made deep, so the words belong to it.
                    // Deep ink on light tiles, light ink on deep ones: readable on either.
                    val ink = if (item.color.luminance() < 0.3f) lerp(item.color, Color.White, 0.9f) else lerp(item.color, Color.Black, 0.72f)
                    // Corners in proportion to the tile: 16 dp on the big ones, and a small tile keeps
                    // its corners small rather than turning into a pill.
                    val corner = with(density) { (minOf(tile.width, tile.height) * 0.12f).toDp() }.coerceIn(4.dp, 16.dp)
                    Box(
                        Modifier
                            .offset { IntOffset(tile.x.roundToInt(), tile.y.roundToInt()) }
                            .size(with(density) { tile.width.toDp() }, with(density) { tile.height.toDp() })
                            .padding(gap / 2)
                            .graphicsLayer { alpha = shown.value; scaleX = 0.92f + 0.08f * shown.value; scaleY = 0.92f + 0.08f * shown.value }
                            .clip(RoundedCornerShape(corner))
                            .background(item.color)
                            .semantics(mergeDescendants = true) { contentDescription = "${item.name}, ${item.amount}, ${wholePercent(item.rub.toDouble() / total)}" }
                            .padding(14.dp),
                    ) {
                        if (roomy[tile.index] == true) {
                            Column {
                                Text(item.name, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
                                Text(item.amount, style = MaterialTheme.typography.titleMedium.merge(Tnum).copy(fontWeight = FontWeight.Bold), color = ink, maxLines = 1)
                                Text(wholePercent(item.rub.toDouble() / total), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.9f), maxLines = 1)
                            }
                        }
                    }
                }
            }
            // A legend for the tiles too small for words: a dot of each one's colour, its name and amount.
            val small = tiles.filter { roomy[it.index] != true }.map { items[it.index] }
            if (small.isNotEmpty()) {
                VSpace(Gap.s)
                Row(Modifier.padding(horizontal = Gap.m), horizontalArrangement = Arrangement.spacedBy(Gap.m)) {
                    small.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(item.color))
                            Spacer(Modifier.width(Gap.xs + 2.dp))
                            Text(item.name + " " + item.amount, style = MaterialTheme.typography.labelLarge.merge(Tnum), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** One category: its name and amount on a line, and under them a bar as long as its share of the biggest. */
@Composable
private fun CategoryBar(name: String, amount: String, fraction: Float, color: Color, order: Int) {
    val motion = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    Column {
        LabelAmount(name, amount)
        VSpace(Gap.s)
        // Grows in from the left when the screen opens, one bar after another.
        val grown = remember { Animatable(0f) }
        LaunchedEffect(fraction) { delay(order * 60L); grown.animateTo(fraction.coerceIn(0.01f, 1f), motion) }
        Box(Modifier.fillMaxWidth().height(16.dp)) {
            // The category's own pastel: the same colour wherever it is drawn.
            Box(Modifier.fillMaxHeight().fillMaxWidth(grown.value.coerceAtLeast(0.01f)).background(color, RoundedCornerShape(8.dp)))
        }
    }
}

/** A name on the left, its amount on the right, both whole. */
@Composable
private fun LabelAmount(label: String, amount: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(Gap.m))
        Text(amount, style = MaterialTheme.typography.bodyLarge.merge(Tnum).copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

private fun shortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(if (I18n.russian) "d MMMM" else "MMMM d", I18n.locale))

private val LABEL = 56.dp

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

/**
 * Day bars on a tile: round tops, flat bottoms, the bar 0.62 of its step. A finger scrubs across
 * them (or taps one): the picked bar turns primary and its day and sum ride in a pill above it.
 * The dashed line is the daily budget.
 */
@Composable
private fun DayBars(days: List<Pair<LocalDate, Long>>, today: LocalDate, kind: PeriodKind, budget: Long?, base: Base, stacks: Map<LocalDate, List<Pair<Color, Long>>>) {
    // The picked day's dot in the theme's own colour, like the other leading marks.
    val accent = leitmotifColor(0, LocalTones.current)
    if (days.isEmpty()) return
    // The last day with spending, so the pill opens on something, not on an empty today.
    val start = days.indexOfLast { it.second > 0 }.takeIf { it >= 0 } ?: days.lastIndex
    var picked by remember(days) { mutableStateOf(start) }
    // The text's ink, like every figure: full for the picked day, faint for the rest.
    val strong = MaterialTheme.colorScheme.onSurface
    val normal = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val line = MaterialTheme.colorScheme.outline
    val top = maxOf(days.maxOf { it.second }, budget ?: 0L).coerceAtLeast(1)
    val density = LocalDensity.current
    // The bars rise when the period opens, left to right in a short wave.
    val grow = remember(days) { Animatable(0f) }
    val rise = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(days) { grow.animateTo(1f, rise) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val step = widthPx / days.size
        Column {
            // The pill over the picked bar, kept inside the tile.
            var pillWidth by remember { mutableStateOf(0) }
            val (date, rub) = days[picked.coerceIn(0, days.lastIndex)]
            val centre = step * picked + step / 2
            val x = (centre - pillWidth / 2f).coerceIn(0f, (widthPx - pillWidth).coerceAtLeast(0f))
            Box(Modifier.fillMaxWidth().height(32.dp)) {
                Surface(
                    modifier = Modifier.offset { IntOffset(x.roundToInt(), 0) }.onSizeChanged { pillWidth = it.width },
                    shape = CircleShape,
                    color = pickedColor(),
                    contentColor = onPickedColor(),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                        Text(dayShort(date, today), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text(base.approx(rub), style = MaterialTheme.typography.labelLarge.merge(Tnum), maxLines = 1)
                    }
                }
            }
            VSpace(Gap.s)
            fun index(px: Float) = (px / step).toInt().coerceIn(0, days.lastIndex)
            Canvas(
                Modifier.fillMaxWidth().height(140.dp)
                    .pointerInput(days) { detectTapGestures { picked = index(it.x) } }
                    .pointerInput(days) {
                        detectHorizontalDragGestures(onDragStart = { picked = index(it.x) }) { change, _ -> picked = index(change.position.x) }
                    },
            ) {
                // Bars wider than the gaps between them: a day reads as a block, not a line.
                val barWidth = step * 0.8f
                val gapPx = 1.dp.toPx()
                val radius = minOf(barWidth / 2, 12.dp.toPx())
                days.forEachIndexed { i, (day, value) ->
                    val g = (grow.value * 1.5f - 0.5f * i / days.size).coerceIn(0f, 1f)
                    val h = (if (value > 0) (size.height * value / top).coerceAtLeast(radius) else 3.dp.toPx()) * g
                    val left = step * i + (step - barWidth) / 2
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left, top = size.height - h, right = left + barWidth, bottom = size.height,
                                topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                            ),
                        )
                    }
                    val parts = stacks[day].orEmpty()
                    val sum = parts.sumOf { it.second }
                    if (value > 0 && sum > 0) {
                        // The day's categories stacked in their colours, biggest at the bottom; the picked day full, the rest softer.
                        clipPath(path) {
                            var y = size.height
                            parts.forEach { (color, rub) ->
                                val part = h * rub / sum
                                // A hairline of the card between two categories, so like colours don't merge.
                                drawRect(color, Offset(left, y - part), Size(barWidth, (part - gapPx).coerceAtLeast(0f)))
                                y -= part
                            }
                        }
                    } else {
                        drawPath(path, normal)
                    }
                    // Every day in its full colours; the picked one gets a dot under it, its date and sum are in the pill.
                    if (i == picked) drawCircle(accent, radius = 3.dp.toPx(), center = Offset(left + barWidth / 2, size.height + 6.dp.toPx()))
                }
                budget?.let {
                    val y = size.height - size.height * it / top
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                }
            }
            VSpace(Gap.s)
            // A week names its days; a month marks the 1st, 10th, 20th and today.
            Box(Modifier.fillMaxWidth().height(16.dp)) {
                days.forEachIndexed { i, (d, _) ->
                    val label = when {
                        // Two letters: "Пн Вт Ср", never the ambiguous single "В".
                        kind == PeriodKind.WEEK || days.size <= 7 -> d.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, I18n.locale).take(2).replaceFirstChar { it.titlecase(I18n.locale) }
                        d == today -> d.dayOfMonth.toString()
                        // A fixed mark too near today would run into today's.
                        // The month's first day says which month began; 10 and 20 only when clear of today.
                        d.dayOfMonth == 1 -> "1 " + d.month.getDisplayName(TextStyle.SHORT_STANDALONE, I18n.locale).trimEnd('.')
                        d.dayOfMonth in listOf(10, 20) && kotlin.math.abs(d.toEpochDay() - today.toEpochDay()) > 2 -> d.dayOfMonth.toString()
                        else -> null
                    } ?: return@forEachIndexed
                    // Wider than a bar, centred on it, so "20" is never cut to "2".
                    Text(
                        label,
                        modifier = Modifier.offset(x = with(density) { (step * i + step / 2).toDp() } - LABEL / 2).width(LABEL),
                        style = MaterialTheme.typography.labelSmall, // scale-exempt: a chart's axis, under bars a few dp wide
                        color = if (i == picked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            // What the dashed line is: the plan for a day.
            budget?.let {
                VSpace(Gap.m)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.width(20.dp).height(2.dp)) {
                        drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                    }
                    Spacer(Modifier.width(Gap.s))
                    Text(tr("План ", "Plan ") + base.approx(it) + tr(" в день", " a day"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** "Пт 26" or "Сегодня". */
private fun dayShort(date: LocalDate, today: LocalDate): String =
    if (date == today) tr("Сегодня", "Today")
    else date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, I18n.locale).replaceFirstChar { it.titlecase(I18n.locale) } + " " + date.dayOfMonth

private fun categoryName(data: AppData, spend: CategorySpend): String =
    spend.categoryId?.let { data.categoryById[it]?.label() } ?: tr("Без категории", "No category")

