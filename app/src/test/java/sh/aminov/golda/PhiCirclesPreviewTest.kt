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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
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
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.Debts
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.ui.AccountsScreen
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.CardCorner
import sh.aminov.golda.ui.Gap
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HeaderSpace
import sh.aminov.golda.ui.HeroAmount
import sh.aminov.golda.ui.HeroTile
import sh.aminov.golda.ui.HomeScreen
import sh.aminov.golda.ui.LocalTones
import sh.aminov.golda.ui.Pace
import sh.aminov.golda.ui.PeriodBar
import sh.aminov.golda.ui.RowIcon
import sh.aminov.golda.ui.Space
import sh.aminov.golda.ui.TextRole
import sh.aminov.golda.ui.accountSubline
import sh.aminov.golda.ui.contrast
import sh.aminov.golda.ui.dayLabel
import sh.aminov.golda.ui.dayMonth
import sh.aminov.golda.ui.hang
import sh.aminov.golda.ui.heroFigureSize
import sh.aminov.golda.ui.letters
import sh.aminov.golda.ui.operationColor
import sh.aminov.golda.ui.operationIcon
import sh.aminov.golda.ui.operationLine
import sh.aminov.golda.ui.paceColor
import sh.aminov.golda.ui.tileColor
import sh.aminov.golda.ui.typeIcon
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/screen-rhythm.md`): φ by the circles — Home and Accounts whole at «Просторно» in Golda light, as
 * the code has them and laid out by the headers' rules (every gap from the letters, on the φ row), into
 * `docs/tasks/screen-rhythm/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class PhiCirclesPreviewTest(private val variant: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = listOf("now_home", "now_accounts", "phi_home", "phi_accounts").map { arrayOf<Any>(it) }
    }

    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    @Test fun goldaLight() {
        val data = ScreenshotTest().demo()
        compose.setContent {
            GoldaTheme("golda", "LIGHT", "ROOMY") {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (variant) {
                        "now_home" -> HomeScreen(data, PaddingValues(), rememberLazyListState(), {}, {}, onEdit = {})
                        "now_accounts" -> AccountsScreen(data, PaddingValues(), {}, {}, { _, _, _ -> })
                        "phi_home" -> RulesHome(data)
                        else -> RulesAccounts(data)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/screen-rhythm/circles-$variant.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }
}

private fun whole(minor: Long, code: String) = Fmt.whole(minor, code) + " " + Currencies.symbol(code)

@Composable private fun V(h: Dp) = Spacer(Modifier.height(h))

/**
 * A card of the list, φ by the circles: the plate's edge (26) over the title's letters and under the last
 * circle, the title a block (26) over the first circle, the circles 16 apart, each row's name and second
 * line a value caption's step (16) apart from the letters; a row is its circle's 48 high, the text centred.
 */
@Composable
private fun Card(title: String, action: Boolean, rows: List<@Composable () -> Unit>) {
    Surface(Modifier.padding(horizontal = Gap.s).fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(CardCorner), color = tileColor()) {
        Column(Modifier.padding(horizontal = Space.card, vertical = HeaderSpace.edge)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f).letters(TextRole.groupTitle.fontSize), style = TextRole.groupTitle)
                if (action) {
                    Box(Modifier.height(16.dp).wrapContentHeight(unbounded = true)) {
                        FilledTonalIconButton(onClick = {}, modifier = Modifier.size(32.dp)) { Icon(painterResource(R.drawable.ic_add), null, Modifier.size(20.dp)) }
                    }
                }
            }
            rows.forEachIndexed { i, row ->
                V(if (i == 0) HeaderSpace.block else HeaderSpace.valueCaption)
                row()
            }
        }
    }
}

/** A row as tall as its circle (48), the two lines cut to their letters and centred on it. */
@Composable
private fun Line(icon: Int, colour: androidx.compose.ui.graphics.Color?, name: String, sub: String, amount: String, came: String) {
    Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (colour != null) RowIcon(icon, colour) else RowIcon(icon)
        Spacer(Modifier.width(Space.card))
        Column(Modifier.weight(1f)) {
            Row {
                Text(name, Modifier.weight(1f).letters(TextRole.rowName.fontSize), style = TextRole.rowName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(Space.card))
                Text(amount, Modifier.letters(TextRole.rowAmount.fontSize), style = TextRole.rowAmount, maxLines = 1)
            }
            V(HeaderSpace.valueCaption)
            Row {
                Text(sub, Modifier.weight(1f).letters(TextRole.caption.fontSize), style = TextRole.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(came, Modifier.letters(TextRole.caption.fontSize), style = TextRole.caption, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RulesHome(data: AppData) {
    val e = data.envelope!!
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Gap.s, bottom = 40.dp)) {
        item {
            val scheme = MaterialTheme.colorScheme
            val ink = scheme.onPrimaryFixed
            HeroTile(Modifier.padding(horizontal = Gap.s), content = ink, vertical = HeaderSpace.edge) {
                val words = TextRole.caption
                val status = TextRole.headerFigures
                val cut = Modifier.letters(words.fontSize)
                val figure = whole(e.leftTodayMinor, e.currency)
                Row(cut) {
                    Text("Можно сегодня", Modifier.weight(1f), style = words)
                    IconButton(onClick = {}, modifier = Modifier.hang()) { Icon(painterResource(R.drawable.ic_settings), null, tint = scheme.onPrimaryFixedVariant) }
                }
                V(HeaderSpace.figureCaption)
                BoxWithConstraints { HeroAmount(figure, ink, Modifier.letters(heroFigureSize(figure, maxWidth)), signColor = ink) }
                V(HeaderSpace.block)
                PeriodBar(e.timeShare, e.spentShare, paceColor(Pace.LASTS), paceColor(Pace.OVER))
                V(HeaderSpace.chartCaption)
                Row(Modifier.fillMaxWidth().then(cut)) { Text(dayMonth(e.start), Modifier.weight(1f), style = words); Text(dayMonth(e.end), style = words) }
                V(HeaderSpace.block)
                val overInk = if (contrast(scheme.onErrorContainer, scheme.primaryFixed) >= 4.5) scheme.onErrorContainer else ink
                Text(buildAnnotatedString { append("Сверх нормы "); withStyle(SpanStyle(color = overInk)) { append(whole(e.overNormMinor, e.currency)) } }, Modifier.letters(status.fontSize), style = status, color = ink)
                V(HeaderSpace.valueCaption)
                Text("План ${whole(e.perDayMinor, e.currency)} в день · факт ${whole(e.burnMinor ?: 0, e.currency)}", cut, style = words)
            }
        }
        data.visibleOperations.groupBy { Ledger.localDate(it.op.timestamp, data.zone) }.forEach { (date, list) ->
            item {
                V(HeaderSpace.group)
                Card(dayLabel(date, data.today), false, list.map { op ->
                    {
                        val line = operationLine(data, op, null)
                        val category = op.op.categoryId?.let { data.categoryById[it] }
                        Line(operationIcon(category, op.op.type), operationColor(category?.key, op.op.type, LocalTones.current), line.title, line.supporting.orEmpty(), line.main,
                            line.secondary?.let { (if (line.approximate) "≈ " else "") + it }.orEmpty())
                    }
                })
            }
        }
    }
}

@Composable
private fun RulesAccounts(data: AppData) {
    val total = data.states.values.sumOf { it.rubMinor }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Gap.s, bottom = 40.dp)) {
        item {
            val ink = MaterialTheme.colorScheme.onPrimaryFixed
            HeroTile(Modifier.padding(horizontal = Gap.s), vertical = HeaderSpace.edge) {
                val words = TextRole.caption
                val figures = TextRole.headerFigures
                val cut = Modifier.letters(words.fontSize)
                val (head, tail) = Debts.advice(data.accounts, data.states)!!.split("\n", limit = 2).let { it.first() to it.getOrNull(1).orEmpty() }
                val text = data.base.whole(total)
                Text("Всего на счетах", cut, style = words)
                V(HeaderSpace.figureCaption)
                BoxWithConstraints { HeroAmount(text, ink, Modifier.letters(heroFigureSize(text, maxWidth)), signColor = ink) }
                V(HeaderSpace.block)
                Text(data.others(total, data.base.code), Modifier.letters(figures.fontSize), style = figures, color = ink)
                V(HeaderSpace.group)
                Text(head, cut, style = words)
                V(HeaderSpace.valueCaption)
                Text(tail, Modifier.letters(figures.fontSize), style = figures, color = ink)
            }
        }
        item {
            V(HeaderSpace.group)
            Card("Счета", true, data.accounts.map { a ->
                {
                    val s = data.states.getValue(a.id)
                    Line(typeIcon(a.type), null, a.name, accountSubline(data, s), Fmt.amount(s.balanceMinor, s.currency), "")
                }
            })
        }
        item {
            V(HeaderSpace.group)
            Card("Регулярные платежи", true, data.obligations.map { p ->
                { Line(R.drawable.ic_repeat, null, p.name, "${p.dayOfMonth} числа", Fmt.amount(p.amountMinor, p.currency), "") }
            })
        }
    }
}
