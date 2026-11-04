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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import sh.aminov.golda.data.OpType
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.ui.Pace
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.CardTitle
import sh.aminov.golda.ui.Gap
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HeroAmount
import sh.aminov.golda.ui.HeroDescent
import sh.aminov.golda.ui.HeroTile
import sh.aminov.golda.ui.HomeScreen
import sh.aminov.golda.ui.OperationRow
import sh.aminov.golda.ui.PeriodBar
import sh.aminov.golda.ui.Space
import sh.aminov.golda.ui.TextRole
import sh.aminov.golda.ui.bleed
import sh.aminov.golda.ui.groupShape
import sh.aminov.golda.ui.tileColor
import sh.aminov.golda.ui.Tnum
import sh.aminov.golda.ui.contrast
import sh.aminov.golda.ui.dayLabel
import sh.aminov.golda.ui.dayMonth
import sh.aminov.golda.ui.paceColor
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/home-type-spacing.md`): Home by the rules of Accounts (TextRole, Space),
 * three ways, against the screen as it is, plain and with the gaps painted by level, in Marathon and
 * Midnight dark at «Обычно» and «Просторно», into `docs/tasks/home-type-spacing/`. Delete once one is picked.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi")
class HomeRulesPreviewTest(private val variant: String, private val spacing: String, private val painted: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1} painted={2}")
        fun cases() = listOf("now", "a", "b", "c").flatMap { v ->
            listOf("NORMAL", "ROOMY").flatMap { s -> listOf(false, true).map { p -> arrayOf<Any>(v, s, p) } }
        }
    }

    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private fun shot(theme: String, palette: String) {
        val data = ScreenshotTest().demo()
        compose.setContent {
            GoldaTheme(palette, "DARK", spacing) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (variant == "now" && !painted) HomeScreen(data, PaddingValues(), rememberLazyListState(), {}, {}, onEdit = {})
                    else Mock(data, variant, painted)
                }
            }
        }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/home-type-spacing/${variant}${if (painted) "_levels" else ""}_${spacing.lowercase()}_$theme.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }

    @Test fun marathonDark() = shot("marathon_dark", "marathon")
    @Test fun midnightDark() = shot("midnight_dark", "midnight")
}

private val levelColors = listOf(Color(0xCCFF4D9D), Color(0xCCFF9F1C), Color(0xCC2EC4B6), Color(0xCC3A86FF))

@Composable
private fun Gap(height: Dp, level: Int, painted: Boolean) =
    Spacer(Modifier.fillMaxWidth().height(height).let { if (painted && height > 0.dp) it.background(levelColors[level]) else it })

private fun whole(minor: Long, code: String) = Fmt.whole(minor, code) + " " + Currencies.symbol(code)

/**
 * now: the screen as it is (its gaps copied to paint them). a: TextRole and Space, the order as now (label
 * over the figure). b: as a, the figure first and its label under it, like Accounts. c: as b, the feed's
 * amounts at 16 instead of 22.
 */
@Composable
private fun Mock(data: AppData, v: String, painted: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Gap.s, bottom = 40.dp)) {
        item { Hero(data, v, painted) }
        days(data, v, painted)
    }
}

@Composable
private fun Hero(data: AppData, v: String, painted: Boolean) {
    val e = data.envelope ?: return
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onPrimaryFixed
    val now = v == "now"
    val quiet = if (now) scheme.onPrimaryFixedVariant else ink
    val words: TextStyle = if (now) MaterialTheme.typography.bodyMedium.merge(Tnum) else TextRole.caption
    val status: TextStyle = if (now) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold) else TextRole.headerFigures
    val text = if (now) 8.dp else Space.text
    val block = if (now) 16.dp else Space.block
    val code = e.currency
    val caption = @Composable { Text("Можно сегодня", style = words, color = quiet, maxLines = 1) }
    val settings = @Composable { Icon(painterResource(R.drawable.ic_settings), null, Modifier.size(24.dp), tint = quiet) }
    HeroTile(Modifier.padding(horizontal = Gap.s), content = ink) {
        if (v == "b" || v == "c") {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { HeroAmount(whole(e.leftTodayMinor, code), ink, signColor = ink) }
                settings()
            }
            Gap(text, 0, painted)
            caption()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { caption() }; settings() }
            Gap(text, 0, painted)
            HeroAmount(whole(e.leftTodayMinor, code), ink, signColor = if (now) scheme.onSurfaceVariant else ink)
        }
        Gap(if (now) Gap.m - HeroDescent else block, 1, painted)
        PeriodBar(e.timeShare, e.spentShare, paceColor(Pace.LASTS), paceColor(Pace.OVER))
        Gap(if (now) Gap.xs else Space.text, 0, painted)
        Row(Modifier.fillMaxWidth()) {
            Text(dayMonth(e.start), Modifier.weight(1f), style = words, color = quiet)
            Text(dayMonth(e.end), style = words, color = quiet)
        }
        Gap(block, 1, painted)
        val beyond = e.overNormMinor
        val overInk = if (contrast(scheme.onErrorContainer, scheme.primaryFixed) >= 4.5) scheme.onErrorContainer else ink
        Text(
            buildAnnotatedString {
                if (beyond > 0) {
                    append("Сверх нормы ")
                    withStyle(SpanStyle(color = overInk)) { append(whole(beyond, code)) }
                } else {
                    append("В норме")
                }
            },
            style = status, color = ink, maxLines = 1,
        )
        Gap(if (now) Gap.xs else Space.text, 0, painted)
        val plan = "План ${whole(e.perDayMinor, code)} в день"
        val fact = e.burnMinor?.let { "факт ${whole(it, code)}" }
        Text(listOfNotNull(plan, fact).joinToString(" · "), style = words, color = quiet, maxLines = 1)
    }
}

/** The feed: each day a card opened by its date (16, the accent, its sum at the end), the rows by the roles. */
private fun LazyListScope.days(data: AppData, v: String, painted: Boolean) {
    val now = v == "now"
    data.visibleOperations.groupBy { Ledger.localDate(it.op.timestamp, data.zone) }.forEach { (date, list) ->
        val spent = list.filter { it.op.type == OpType.EXPENSE }.sumOf { full -> -full.postings.sumOf { p -> p.rubMinor } }
        val total = spent.takeIf { it > 0 }?.let { data.dayTotal(it) }
        item {
            Gap(if (now) Gap.l else Space.cards, 3, painted)
            if (now) {
                CardTitle(dayLabel(date, data.today), Modifier.padding(horizontal = Gap.m), quiet = true) {
                    total?.let { Text(it, style = MaterialTheme.typography.titleMedium.merge(Tnum), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            } else {
                DayTitle(dayLabel(date, data.today), total)
            }
        }
        list.forEachIndexed { i, op ->
            item { Rows(v) { OperationRow(data, op, null, {}, i + 1, list.size + 1, Modifier.padding(horizontal = Gap.m)) } }
        }
    }
}

/**
 * The feed's rows drawn by the roles without touching their code: its styles remapped. In a, b: the
 * name 16 and every word 14 in the base weight, the amount 22 (the rows' amounts are titleMedium);
 * in c the amount stays 16.
 */
@Composable
private fun Rows(v: String, content: @Composable () -> Unit) {
    if (v == "now") return content()
    val t = MaterialTheme.typography
    val base = TextRole.Base
    val amount = if (v == "c") t.titleMedium else t.titleLarge
    MaterialTheme(
        typography = t.copy(
            bodyLarge = t.bodyLarge.copy(fontWeight = base),
            bodyMedium = t.bodyMedium.copy(fontWeight = base),
            titleMedium = amount.copy(fontWeight = base),
        ),
    ) { content() }
}

/** CardTitle's geometry with the day by the roles: the date 16 in the accent, its sum 16 in the base. */
@Composable
private fun DayTitle(title: String, total: String?) {
    Surface(Modifier.padding(horizontal = Gap.m).bleed(Gap.s).fillMaxWidth(), shape = groupShape(0, 2), color = tileColor()) {
        Row(Modifier.padding(start = Space.card, end = Space.card, top = Space.card, bottom = Space.half), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = TextRole.rowName.copy(fontWeight = TextRole.Accent))
            total?.let { Text(it, style = TextRole.rowName.merge(Tnum), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
