package sh.aminov.golda.ui

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
    val text = MaterialTheme.typography.bodyLarge.merge(Tnum)
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant

    // The whole page, not just the content: a short list left the pager without a back swipe here.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.aboveActions(), verticalArrangement = Arrangement.spacedBy(Gap.s)) {
        item(key = "period") {
            ChoiceGroup(
                options = listOf(PeriodKind.WEEK, PeriodKind.MONTH, PeriodKind.CUSTOM),
                selected = kind,
                onSelect = { if (it == PeriodKind.CUSTOM) picking = true else kind = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gap.m),
                height = 40.dp,
            ) {
                when (it) {
                    PeriodKind.WEEK -> ChoiceText(tr("Неделя", "Week"))
                    PeriodKind.MONTH -> ChoiceText(tr("Месяц", "Month"))
                    PeriodKind.CUSTOM -> Icon(painterResource(R.drawable.ic_calendar), tr("Свой период", "Custom range"), Modifier.size(20.dp))
                }
            }
        }
        item(key = "header") {
            Tile(Modifier.fillMaxWidth().padding(horizontal = Gap.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${shortDate(period.from)} – ${shortDate(period.to)}", Modifier.weight(1f), style = text, color = quiet, maxLines = 1)
                    // The one ice-blue thing in the app: the way to Gemini's reviews.
                    Button(
                        onClick = { onReviews(period) },
                        colors = ButtonDefaults.buttonColors(containerColor = iceColor(), contentColor = onIceColor()),
                        contentPadding = PaddingValues(horizontal = Gap.m),
                        modifier = Modifier.height(36.dp),
                    ) { Text(tr("Разбор", "Review"), style = MaterialTheme.typography.labelLarge) }
                }
                VSpace(Gap.s)
                HeroAmount(data.base.whole(shown.spentRub), MaterialTheme.colorScheme.onSurface, Modifier.testTag(Tags.INSIGHTS_TOTAL))
                Text(tr("≈ ${data.base.approx(report.averagePerDayRub)} в день на ежедневное", "≈ ${data.base.approx(report.averagePerDayRub)} a day on everyday things"), style = text, color = quiet)
                VSpace(Gap.m)
                Row(horizontalArrangement = Arrangement.spacedBy(Gap.xs)) {
                    listOf(Bucket.DAILY to report.dailyRub, Bucket.REGULAR to report.regularRub, Bucket.BIG to report.bigRub).forEach { (bucket, rub) ->
                        BucketChip(bucketLabel(bucket), data.base.whole(rub), filter == bucket, Modifier.weight(1f)) {
                            filter = if (filter == bucket) null else bucket
                        }
                    }
                }
            }
        }
        if (shown.categories.isEmpty()) {
            item(key = "empty") {
                Text(tr("За эти дни трат нет.", "No spending in these days."), Modifier.padding(Gap.l), style = text, color = quiet)
            }
        } else {
            item(key = "bars") {
                // Each category a bar as long as its share of the biggest: the chart and the list in one.
                Tile(Modifier.fillMaxWidth().padding(horizontal = Gap.m)) {
                    val top = shown.categories.first().rubMinor.coerceAtLeast(1)
                    shown.categories.forEachIndexed { index, spend ->
                        if (index > 0) VSpace(Gap.s)
                        CategoryBar(categoryName(data, spend), data.base.whole(spend.rubMinor), spend.rubMinor.toFloat() / top)
                    }
                }
            }
            item(key = "chart") {
                Tile(Modifier.fillMaxWidth().padding(horizontal = Gap.m)) {
                    DayBars(shown.days, today, kind, data.envelope?.let { e -> data.rates.rubMinor(e.perDayMinor, e.currency) }?.takeIf { it > 0 && filter in listOf(null, Bucket.DAILY) }, data.base)
                }
            }
        }
        item(key = "more") {
            val loss = if (report.fxVolumeRub > 0) tr(" · обмен −", " · exchange −") + data.base.approx(report.fxLossRub) else ""
            Text(
                tr("Доходы ", "Income ") + data.base.approx(report.incomeRub) + loss,
                Modifier.padding(horizontal = Gap.m + Gap.xs, vertical = Gap.xs),
                style = text,
                color = quiet,
            )
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
    val container by animateColorAsState(if (on) pickedColor() else MaterialTheme.colorScheme.surfaceContainerHighest, spec, label = "bucket")
    val ink by animateColorAsState(if (on) onPickedColor() else MaterialTheme.colorScheme.onSurface, spec, label = "bucket-ink")
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp), color = container, contentColor = ink) {
        Column(Modifier.padding(horizontal = Gap.s, vertical = Gap.s)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(amount, style = MaterialTheme.typography.titleSmall.merge(Tnum), maxLines = 1)
        }
    }
}

/** One category: name, a bar as long as its share of the biggest one, the amount. */
@Composable
private fun CategoryBar(name: String, amount: String, fraction: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.width(96.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f).height(8.dp)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.02f, 1f)).background(pickedColor(), RoundedCornerShape(4.dp)))
        }
        Text(amount, Modifier.width(88.dp), style = MaterialTheme.typography.bodyLarge.merge(Tnum), textAlign = TextAlign.End, maxLines = 1)
    }
}

private fun shortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(if (I18n.russian) "d MMM" else "MMM d", I18n.locale))

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
                        TextButton(
                            enabled = state.selectedStartDateMillis != null,
                            onClick = {
                                val from = day(state.selectedStartDateMillis!!)
                                onPick(from, state.selectedEndDateMillis?.let(::day) ?: from)
                            },
                        ) { Text(tr("Готово", "Done")) }
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
                    val from = state.selectedStartDateMillis?.let { shortDate(day(it)) } ?: "—"
                    val to = state.selectedEndDateMillis?.let { shortDate(day(it)) } ?: "—"
                    Text(
                        "$from – $to",
                        modifier = Modifier.padding(start = Gap.l, end = Gap.l, top = Gap.m, bottom = Gap.m),
                        style = MaterialTheme.typography.headlineMedium,
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
private fun DayBars(days: List<Pair<LocalDate, Long>>, today: LocalDate, kind: PeriodKind, budget: Long?, base: Base) {
    if (days.isEmpty()) return
    val start = days.indexOfFirst { it.first == today }.takeIf { it >= 0 } ?: days.lastIndex
    var picked by remember(days) { mutableStateOf(start) }
    val strong = MaterialTheme.colorScheme.secondary
    val normal = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
    val line = MaterialTheme.colorScheme.outline
    val top = maxOf(days.maxOf { it.second }, budget ?: 0L).coerceAtLeast(1)
    val density = LocalDensity.current
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
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) {
                    Text(
                        "${dayShort(date, today)} · ${base.approx(rub)}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge.merge(Tnum),
                        maxLines = 1,
                    )
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
                val barWidth = step * 0.62f
                val radius = minOf(barWidth / 2, 12.dp.toPx())
                days.forEachIndexed { i, (_, value) ->
                    val h = if (value > 0) (size.height * value / top).coerceAtLeast(radius) else 3.dp.toPx()
                    val left = step * i + (step - barWidth) / 2
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left, top = size.height - h, right = left + barWidth, bottom = size.height,
                                topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                            ),
                        )
                    }
                    drawPath(path, if (i == picked) strong else normal)
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
                        d == today || d.dayOfMonth in listOf(1, 10, 20) -> d.dayOfMonth.toString()
                        else -> null
                    } ?: return@forEachIndexed
                    val w = with(density) { step.toDp() }
                    Text(
                        label,
                        modifier = Modifier.offset(x = with(density) { (step * i).toDp() }).width(w),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i == picked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
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

