package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.domain.Debts
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.AccountsScreen
import sh.aminov.golda.ui.AddAction
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.Gap
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.GroupLabel
import sh.aminov.golda.ui.GroupRow
import sh.aminov.golda.ui.HeroAmount
import sh.aminov.golda.ui.HeroTile
import sh.aminov.golda.ui.VSpace
import sh.aminov.golda.ui.tileColor
import sh.aminov.golda.ui.typeLabel
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/group-header.md`): the Accounts tab with the group header four ways, against
 * the real screen, drawn into `docs/tasks/group-header/` for the owner to pick from. Delete once one is built.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GroupHeaderPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private val data by lazy { ScreenshotTest().demo() }

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        val (palette, mode) = if (dark) "midnight" to "DARK" else "golda" to "SYSTEM"
        compose.setContent { GoldaTheme(palette, mode) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() } } }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/group-header/$name.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }

    private fun now(dark: Boolean) = shot("0_now_" + if (dark) "dark" else "light", dark) { AccountsScreen(data, PaddingValues(), {}, {}, { _, _, _ -> }) }
    private fun variant(v: Variant, dark: Boolean) = shot(v.file + "_" + if (dark) "dark" else "light", dark) { Mock(data, v) }

    @Test fun nowLight() = now(false)
    @Test fun nowDark() = now(true)
    @Test fun aLight() = variant(Variant.A, false)
    @Test fun aDark() = variant(Variant.A, true)
    @Test fun bLight() = variant(Variant.B, false)
    @Test fun bDark() = variant(Variant.B, true)
    @Test fun cLight() = variant(Variant.C, false)
    @Test fun cDark() = variant(Variant.C, true)
    @Test fun dLight() = variant(Variant.D, false)
    @Test fun dDark() = variant(Variant.D, true)
}

private enum class Variant(val file: String) {
    /** The label and the "+" on the card's edge, 8 from the screen, not on the rows' text. */
    A("a_card_edge"),

    /** As now, with air: 40 over the label, 8 between it and the card. */
    B("b_more_air"),

    /** The label as the card's first row, the "+" at its end (Android 16 Settings). */
    C("c_inside_card"),

    /** Cards 16 from the screen with corner 16, no bleed; the label on the rows' text. */
    D("d_small_corners"),
}

/** The Accounts tab's top: the total, the accounts and the regular payments, the header drawn as [v] says. */
@Composable
private fun Mock(data: AppData, v: Variant) {
    val total = data.states.values.sumOf { it.rubMinor }
    // Г: the screen's cards keep the page margin and a smaller corner, the hero too.
    val edge = if (v == Variant.D) Gap.m else Gap.s
    val shapes = if (v == Variant.D) MaterialTheme.shapes.copy(extraLargeIncreased = RoundedCornerShape(20.dp)) else MaterialTheme.shapes
    MaterialTheme(shapes = shapes) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Gap.s, bottom = Gap.xl)) {
            item {
                HeroTile(Modifier.padding(horizontal = edge)) {
                    val text = MaterialTheme.typography.bodyMedium
                    val quiet = MaterialTheme.colorScheme.onPrimaryFixedVariant
                    Text("Всего на счетах", style = text, color = quiet)
                    VSpace(Gap.s)
                    HeroAmount(data.base.whole(total), MaterialTheme.colorScheme.onPrimaryFixed)
                    VSpace(Gap.s)
                    Text(data.others(total, data.base.code), style = text, color = quiet)
                    Debts.advice(data.accounts, data.states)?.let { advice ->
                        VSpace(Gap.m)
                        val (head, tail) = advice.split("\n", limit = 2).let { it.first() to it.getOrNull(1) }
                        Text(head, style = text, color = quiet)
                        tail?.let { Text(it, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) }
                    }
                }
            }
            group(v, "Счета", data.accounts.map { a ->
                val s = data.states.getValue(a.id)
                // As the real row says it, short of the savings forecast.
                val kind = if (data.envelope?.account?.id == a.id) "Карта трат" else typeLabel(a.type).takeUnless { a.name.contains(it, ignoreCase = true) }
                val sub = listOfNotNull(kind, ("≈ " + data.base.approx(s.rubMinor)).takeIf { s.currency != data.base.code }).joinToString(" · ").ifEmpty { null }
                Line(a.name, sub, Fmt.amount(s.balanceMinor, s.currency))
            })
            group(v, "Регулярные платежи", data.obligations.map { Line(it.name, "${it.dayOfMonth} числа", Fmt.amount(it.amountMinor, it.currency)) })
        }
    }
}

private class Line(val name: String, val sub: String?, val amount: String)

private fun LazyListScope.group(v: Variant, title: String, lines: List<Line>) {
    when (v) {
        Variant.A -> item { ALabel(title) }
        Variant.B -> item { GroupLabel(title, Modifier.padding(horizontal = Gap.m).padding(top = Gap.xl, bottom = Gap.s)) { AddAction("", {}) } }
        Variant.C -> item { VSpace(Gap.l) }
        Variant.D -> item { DLabel(title) }
    }
    when (v) {
        Variant.C -> {
            val count = lines.size + 1
            item {
                GroupRow(0, count, Modifier.padding(horizontal = Gap.m), trailing = { AddAction("", {}) }) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            }
            lines.forEachIndexed { i, l -> item { LineRow(l, i + 1, count) } }
        }
        Variant.D -> lines.forEachIndexed { i, l -> item { SmallRow(l, i, lines.size) } }
        else -> lines.forEachIndexed { i, l -> item { LineRow(l, i, lines.size) } }
    }
}

/** А: the label 8 from the screen, on the card's edge, the "+" ending on it too. */
@Composable
private fun ALabel(title: String) = GroupLabel(title, Modifier.padding(top = Gap.l)) { AddAction("", {}) }

/** Г: the label on the rows' text (32), the "+" ending on the amounts' line, 32 from the screen. */
@Composable
private fun DLabel(title: String) {
    Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = Gap.l, bottom = Gap.xs).heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        AddAction("", {})
    }
}

@Composable
private fun LineRow(l: Line, i: Int, count: Int) = GroupRow(
    i, count, Modifier.padding(horizontal = Gap.m), onClick = {},
    supporting = l.sub?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
    trailing = { Text(l.amount, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
) { Text(l.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }

/** Г's row: 16 from the screen, corner 16 at the group's ends, the text 16 inside. */
@Composable
private fun SmallRow(l: Line, i: Int, count: Int) {
    val r: Dp = 16.dp
    val shape = RoundedCornerShape(
        topStart = if (i == 0) r else 0.dp, topEnd = if (i == 0) r else 0.dp,
        bottomStart = if (i == count - 1) r else 0.dp, bottomEnd = if (i == count - 1) r else 0.dp,
    )
    Surface(Modifier.fillMaxWidth().padding(horizontal = Gap.m), shape = shape, color = tileColor()) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = Gap.m, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                l.sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.width(Gap.s))
            Text(l.amount, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}
