package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextOverflow
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
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.ui.AccountsScreen
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.CardTitle
import sh.aminov.golda.ui.Gap
import sh.aminov.golda.ui.GlyphCircle
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.GroupHeader
import sh.aminov.golda.ui.GroupRow
import sh.aminov.golda.ui.HomeScreen
import sh.aminov.golda.ui.LocalTones
import sh.aminov.golda.ui.Space
import sh.aminov.golda.ui.TextRole
import sh.aminov.golda.ui.Tnum
import sh.aminov.golda.ui.accountSubline
import sh.aminov.golda.ui.dayLabel
import sh.aminov.golda.ui.inkOn
import sh.aminov.golda.ui.operationColor
import sh.aminov.golda.ui.operationIcon
import sh.aminov.golda.ui.operationLine
import sh.aminov.golda.ui.tileColor
import sh.aminov.golda.ui.typeIcon
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/home-type-spacing.md`): the Home feed and the Accounts list side by side
 * — the rows' height one rule three ways (s1, s2, s3) — and the feed's icons against its amounts five
 * ways (z1–z5), in Golda light and dark at «Просторно», into `docs/tasks/home-type-spacing/balance-*`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi")
class RowBalancePreviewTest(private val variant: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = listOf("home", "accounts", "s1", "s2", "s3", "z1", "z2", "z3", "z4", "z5").map { arrayOf<Any>(it) }
    }

    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private fun shot(theme: String, mode: String) {
        val data = ScreenshotTest().demo()
        compose.setContent {
            GoldaTheme("golda", mode, "ROOMY") {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (variant) {
                        "home" -> HomeScreen(data, PaddingValues(), rememberLazyListState(), {}, {}, onEdit = {})
                        "accounts" -> AccountsScreen(data, PaddingValues(), {}, {}, { _, _, _ -> })
                        "s1", "s2", "s3" -> Accounts(data, variant)
                        else -> Feed(data, variant)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/home-type-spacing/balance-${variant}_$theme.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }

    @Test fun goldaLight() = shot("golda_light", "LIGHT")
    @Test fun goldaDark() = shot("golda_dark", "DARK")
}

/**
 * The Accounts list. s1: every row as tall as a row with a 48 icon (the icon's 48 against the two lines'
 * 52 — it is the same already, so s1 is the room as it would read: the rows' amounts 16, as the feed's).
 * s2: a 48 circle of the account's type before each row. s3: the 48 circle in the page's tone, the amounts
 * 16 as the feed's, one row for both lists.
 */
@Composable
private fun Accounts(data: AppData, v: String) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Gap.s, bottom = 40.dp)) {
        item { GroupHeader("Счета", "Добавить счёт", {}, Modifier.padding(horizontal = Gap.m)) }
        data.accounts.forEachIndexed { i, a ->
            item {
                val s = data.states.getValue(a.id)
                GroupRow(
                    i + 1, data.accounts.size + 1, Modifier.padding(horizontal = Gap.m), onClick = {},
                    leading = if (v == "s1") null else ({ GlyphCircle(typeIcon(a.type), size = 48.dp) }),
                    supporting = { Text(accountSubline(data, s), style = TextRole.caption, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailing = {
                        val style = if (v == "s3" || v == "s1") TextRole.rowName.merge(Tnum) else TextRole.rowAmount
                        Text(Fmt.amount(s.balanceMinor, s.currency), style = style, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    },
                ) { Text(a.name, style = TextRole.rowName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/**
 * The feed's first days. z1: as now (48 circles in the category's colour, amounts 16 · 500). z2: tonal
 * circles (the colour at a fifth over the card, the glyph the colour towards the text). z3: 40 circles. z4: amounts in
 * the accent (16 · 700). z5: tonal circles and amounts in the accent.
 */
@Composable
private fun Feed(data: AppData, v: String) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Gap.s, bottom = 40.dp)) {
        data.visibleOperations.groupBy { Ledger.localDate(it.op.timestamp, data.zone) }.entries.take(3).forEach { (date, list) ->
            item { CardTitle(dayLabel(date, data.today), Modifier.padding(horizontal = Gap.m).padding(top = Space.cards)) }
            list.forEachIndexed { i, op -> item { FeedRow(data, op, i + 1, list.size + 1, v) } }
        }
    }
}

@Composable
private fun FeedRow(data: AppData, full: OperationFull, i: Int, count: Int, v: String) {
    val line = operationLine(data, full, null)
    val category = full.op.categoryId?.let { data.categoryById[it] }
    val tonal = v == "z2" || v == "z5"
    val accent = v == "z4" || v == "z5"
    val ink = if (line.transfer) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    GroupRow(
        i, count, Modifier.padding(horizontal = Gap.m), onClick = {},
        leading = {
            val fill = operationColor(category?.key, full.op.type, LocalTones.current)
            val size = if (v == "z3") 40.dp else 48.dp
            // Tonal: the colour faint over the card, the glyph the colour pulled towards the text's ink.
            if (tonal) GlyphCircle(operationIcon(category, full.op.type), color = fill.copy(alpha = 0.22f).compositeOver(tileColor()), ink = lerp(fill, MaterialTheme.colorScheme.onSurface, 0.45f), size = size)
            else GlyphCircle(operationIcon(category, full.op.type), color = fill, ink = inkOn(fill), size = size)
        },
        supporting = { Text(line.supporting.orEmpty(), style = TextRole.caption, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(line.main, style = TextRole.rowName.merge(Tnum).let { if (accent) it.copy(fontWeight = TextRole.Accent) else it }, color = ink, maxLines = 1)
                Spacer(Modifier.height(Space.text))
                Text(line.secondary?.let { (if (line.approximate) "≈ " else "") + it }.orEmpty(), style = TextRole.caption, maxLines = 1)
            }
        },
    ) { Text(line.title, style = TextRole.rowName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
