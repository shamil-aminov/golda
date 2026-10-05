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
 * The period's spending on one screen: the donut carries the total and the split, the legend lists
 * it, the day bars can be scrubbed with a finger, and income and exchange losses close it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnalyticsScreen(data: AppData, padding: PaddingValues) {
    val today = data.today
    var kind by rememberSaveable { mutableStateOf(PeriodKind.WEEK) }
    // Saved like the kind it belongs to (as two epoch days), so the two never disagree after a restart.
    var customDays by rememberSaveable { mutableStateOf<LongArray?>(null) }
    val custom = customDays?.let { (from, to) -> Period(LocalDate.ofEpochDay(from), LocalDate.ofEpochDay(to)) }
    var picking by remember { mutableStateOf(false) }
    var selectedSlice by remember { mutableStateOf<Int?>(null) }
    val palette = sliceColors()
    val period = Analytics.period(kind, today, data.settings, custom)
    val report = remember(data, period) { Analytics.report(data.operations, data.accountById, period, today, data.zone, data.rates) }
    fun toggleSlice(slice: Int) { selectedSlice = if (selectedSlice == slice) null else slice }
    val lastPayday = Analytics.period(PeriodKind.SINCE_PAYDAY, today, data.settings).from

    // The whole page, not just the content: a short list left the pager without a back swipe here.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.aboveActions()) {
        item(key = "period") {
            Column(Modifier.padding(horizontal = Gap.m)) {
                // The pay period says which day it starts; the calendar is a range of your own.
                val options = listOf(PeriodKind.WEEK, PeriodKind.MONTH, PeriodKind.SINCE_PAYDAY, PeriodKind.CUSTOM)
                Row(Modifier.fillMaxWidth()) {
                    ChoiceGroup(
                        options = options,
                        selected = kind,
                        onSelect = {
                            if (it == PeriodKind.CUSTOM) picking = true
                            else { selectedSlice = null; kind = it }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        when (it) {
                            PeriodKind.WEEK -> ChoiceText(tr("Неделя", "Week"))
                            PeriodKind.MONTH -> ChoiceText(tr("Месяц", "Month"))
                            PeriodKind.SINCE_PAYDAY -> ChoiceText(tr("С ", "Since ") + shortDate(lastPayday))
                            PeriodKind.CUSTOM -> Icon(painterResource(R.drawable.ic_calendar), tr("Свой период", "Custom range"), Modifier.size(20.dp))
                        }
                    }
                }
                if (kind == PeriodKind.CUSTOM) {
                    Text(
                        "${shortDate(period.from)} – ${shortDate(period.to)}",
                        modifier = Modifier.fillMaxWidth().padding(top = Gap.s),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (report.categories.isEmpty()) {
            item(key = "empty") {
                Text(
                    tr("За эти дни трат нет.", "No spending in these days."),
                    modifier = Modifier.padding(Gap.l),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Up to three categories get their own slice; anything under 4 % and the rest share "Остальное".
            val own = report.categories.take(TOP_SLICES).filter { report.spentRub > 0 && it.rubMinor * 100 >= report.spentRub * MIN_PERCENT }
            val rest = report.categories.drop(own.size)
            val slices = own.map { it.rubMinor } + listOfNotNull(rest.sumOf { it.rubMinor }.takeIf { rest.isNotEmpty() })
            val sliceColors = own.indices.map { palette[it] } + listOfNotNull(palette.last().takeIf { rest.isNotEmpty() })
            val focus = selectedSlice?.takeIf { it < slices.size }
            item(key = "donut") {
                Donut(
                    shares = slices.map { it.toFloat() },
                    colors = sliceColors,
                    selected = focus,
                    onTap = ::toggleSlice,
                    modifier = Modifier.fillMaxWidth().padding(top = Gap.l),
                ) {
                    // The period's total by default; a tapped slice shows itself, and a second tap goes back.
                    Column(Modifier.fillMaxWidth(0.6f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val label = when {
                            focus == null -> null
                            focus < own.size -> categoryName(data, own[focus])
                            rest.size == 1 -> categoryName(data, rest.single())
                            else -> tr("Остальное", "Other")
                        }
                        val rub = focus?.let { slices[it] } ?: report.spentRub
                        label?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        BigNumber(data.base.approx(rub), data.base.minor(rub), MaterialTheme.colorScheme.onSurface, Modifier.testTag(Tags.INSIGHTS_TOTAL), maxSp = 56f, alignment = Alignment.Center)
                        Text(
                            if (focus == null) tr("≈ ${data.base.approx(report.averagePerDayRub)}/день", "≈ ${data.base.approx(report.averagePerDayRub)}/day")
                            else if (report.spentRub > 0) wholePercent(rub.toDouble() / report.spentRub) else "",
                            style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "legend-gap") { VSpace(Gap.l) }
            report.categories.forEachIndexed { index, spend ->
                item(key = "cat-${spend.categoryId}") {
                    val category = spend.categoryId?.let { data.categoryById[it] }
                    val share = if (report.spentRub > 0) spend.rubMinor.toDouble() / report.spentRub else 0.0
                    // Every category keeps its own row; the folded ones wear "Остальное"'s dot.
                    val slice = minOf(index, own.size)
                    GroupRow(
                        index, report.categories.size,
                        Modifier.padding(horizontal = Gap.m).animateItem(placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                        onClick = { toggleSlice(slice) },
                        leading = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // "The rest" is the tile's own tone in dark: a hairline keeps its dot visible.
                                val dot = sliceColors.getOrElse(slice) { palette.last() }
                                Box(
                                    Modifier.size(12.dp).background(dot, CircleShape)
                                        .then(if (dot == palette.last()) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier),
                                )
                                Spacer(Modifier.width(Gap.m))
                                Icon(painterResource(categoryIcon(category?.key)), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(data.base.approx(spend.rubMinor), style = MaterialTheme.typography.titleMedium.merge(Tnum))
                                Text(
                                    " · " + wholePercent(share),
                                    style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    ) { Text(categoryName(data, spend), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            item(key = "chart") {
                val budget = remember(data) { Budget.today(data.states, data.operations, data.settings, today, data.zone, data.allObligations, data.rates) }
                Tile(Modifier.fillMaxWidth().padding(horizontal = Gap.m).padding(top = Gap.l)) {
                    DayBars(report.days, today, kind, budget.perDayRub.takeIf { it > 0 }, data.base)
                }
            }
        }
        item(key = "more") {
            Bento(Modifier.padding(horizontal = Gap.m).padding(top = Gap.s)) {
                Tile(Modifier.weight(1f).fillMaxHeight()) {
                    Caption(tr("Доходы", "Income"))
                    VSpace(Gap.xs)
                    Text(data.base.approx(report.incomeRub), style = MaterialTheme.typography.headlineSmall.merge(Tnum), maxLines = 1)
                }
                Tile(Modifier.weight(1f).fillMaxHeight()) {
                    Caption(tr("Потери на обмене", "Lost on exchange"))
                    VSpace(Gap.xs)
                    Text(data.base.approx(report.fxLossRub), style = MaterialTheme.typography.headlineSmall.merge(Tnum), maxLines = 1)
                    Text(
                        if (report.fxVolumeRub > 0) wholePercent(report.fxLossRub.toDouble() / report.fxVolumeRub) + tr(" против ЦБ", " against the CBR")
                        else tr("обменов не было", "no exchanges"),
                        style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (picking) {
        RangeDialog(period, onDismiss = { picking = false }) { from, to ->
            customDays = longArrayOf(from.toEpochDay(), to.toEpochDay())
            kind = PeriodKind.CUSTOM
            selectedSlice = null
            picking = false
        }
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

/** The donut shows this many categories on their own; the rest share one slice. */
private const val TOP_SLICES = 3

/** Below this share of the period a category folds into "Остальное". */
private const val MIN_PERCENT = 4

private fun categoryName(data: AppData, spend: CategorySpend): String =
    spend.categoryId?.let { data.categoryById[it]?.label() } ?: tr("Без категории", "No category")

/**
 * A ring of slices with round ends and gaps of one constant length, starting at twelve o'clock.
 * Tapping the ring picks a slice; the others fade.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Donut(
    shares: List<Float>,
    colors: List<Color>,
    selected: Int?,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    val total = shares.sum().takeIf { it > 0 } ?: return
    // The fade follows a tap smoothly instead of snapping.
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val alphas = shares.indices.map { i ->
        key(i) { animateFloatAsState(if (selected != null && selected != i) 0.35f else 1f, fadeSpec, label = "slice").value }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxWidth(0.7f).aspectRatio(1f).pointerInput(shares) {
                detectTapGestures { offset ->
                    val dx = offset.x - size.width / 2f
                    val dy = offset.y - size.height / 2f
                    val stroke = STROKE.toPx()
                    val radius = size.width / 2f
                    // Only the ring itself, with a little slack for fingers.
                    if (hypot(dx, dy) !in (radius - stroke - 12.dp.toPx())..radius) return@detectTapGestures
                    // Angle clockwise from twelve o'clock, where the first slice starts; the slices as drawn.
                    val angle = (Math.toDegrees(atan2(dy, dx).toDouble()) + 90 + 360) % 360
                    var start = 0.0
                    ringSweeps(shares, stroke, GAP.toPx(), radius - stroke / 2).forEachIndexed { i, sweep ->
                        if (angle < start + sweep) { onTap(i); return@detectTapGestures }
                        start += sweep
                    }
                }
            },
        ) {
            drawGappedRing(shares, colors.mapIndexed { i, c -> c.copy(alpha = alphas[i]) }, STROKE.toPx(), GAP.toPx())
        }
        center()
    }
}

private val STROKE = 18.dp
private val GAP = 6.dp
