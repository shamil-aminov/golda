package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Envelope
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.Light
import sh.aminov.golda.domain.tr
import sh.aminov.golda.domain.plural
import sh.aminov.golda.ui.*
import java.util.TimeZone
import java.util.Locale
import java.io.File
import org.junit.Assert.assertTrue

/** Temporary decision frames. All alternatives stay in the test source set. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w432dp-h936dp-mdpi")
class CodexBatch1PreviewTest {
    @get:Rule val compose = createComposeRule()

    @Before fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private fun shot(name: String, recording: Boolean = false, mode: String = "LIGHT", content: @Composable () -> Unit) {
        if (recording) compose.mainClock.autoAdvance = false
        compose.setContent {
            GoldaTheme("golda", mode, "ROOMY") {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        if (recording) compose.mainClock.advanceTimeBy(960)
        compose.onRoot().captureRoboImage("build/codex-batch1/$name.png")
    }

    @Test fun tabs0() = tabs(0)
    @Test fun tabsA() = tabs(1)
    @Test fun tabsB() = tabs(2)
    @Test fun tabsC() = tabs(3)
    @Test fun tabsD() = tabs(4)

    private fun tabs(variant: Int) = shot("tabs_$variant") {
        Scaffold(topBar = { PreviewTabs(variant) }) { padding ->
            HomeScreen(ScreenshotTest().demo(), padding, rememberLazyListState(), {}, onEdit = {})
        }
    }

    @Test fun tabs20() = tabs2(0)
    @Test fun tabs2A() = tabs2(1)
    @Test fun tabs2B() = tabs2(2)
    @Test fun tabs2C() = tabs2(3)
    @Test fun tabs2D() = tabs2(4)

    private fun tabs2(variant: Int) = shot("tabs2_$variant") {
        Scaffold(topBar = {
            Column {
                AiryTabs(variant)
                if (variant >= 2) VSpace(HeaderSpace.valueCaption)
            }
        }) { padding ->
            HomeScreen(ScreenshotTest().demo(), padding, rememberLazyListState(), {}, onEdit = {})
        }
    }

    @Test fun goldA() = gold(1)
    @Test fun goldB() = gold(2)
    @Test fun goldC() = gold(3)
    @Test fun goldD() = gold(4)
    @Test fun goldE() = gold(5)
    @Test fun goldF() = gold(6)

    private fun gold(index: Int) {
        val captions = GoldCandidates.map { candidate ->
            val ratio = contrast(candidate.container, candidate.ink)
            assertTrue("Gold icon contrast must reach 4.5:1", ratio >= 4.5)
            "${candidate.hex} / ${candidate.onHex} · ${String.format(Locale.US, "%.2f", ratio)}:1"
        }
        File("build/codex-batch1").mkdirs()
        File("build/codex-batch1/gold-captions.txt").writeText(captions.joinToString("\n"))
        shot("gold_$index") { IslandPage(index + 2, false) }
    }

    @Test fun island0Rest() = island(0, false)
    @Test fun island0Recording() = island(0, true)
    @Test fun islandARest() = island(1, false)
    @Test fun islandARecording() = island(1, true)
    @Test fun islandBRest() = island(2, false)
    @Test fun islandBRecording() = island(2, true)
    @Test fun islandCRest() = island(3, false)
    @Test fun islandCRecording() = island(3, true)
    @Test fun islandDRest() = island(4, false)
    @Test fun islandDRecording() = island(4, true)

    private fun island(variant: Int, recording: Boolean) =
        shot("island_${variant}_${if (recording) "record" else "rest"}", recording) { IslandPage(variant, recording) }

    @Test fun champagneLightRest() = champagne("LIGHT", false)
    @Test fun champagneLightRecording() = champagne("LIGHT", true)
    @Test fun champagneDarkRest() = champagne("DARK", false)
    @Test fun champagneDarkRecording() = champagne("DARK", true)

    private fun champagne(mode: String, recording: Boolean) =
        shot("champagne_${mode}_${if (recording) "record" else "rest"}", recording, mode) { IslandPage(-1, recording) }

    @Test fun mana0() = mana(0)
    @Test fun manaA() = mana(1)
    @Test fun manaB() = mana(2)
    @Test fun manaC() = mana(3)
    @Test fun manaD() = mana(4)
    @Test fun manaE() = mana(5)

    private fun mana(variant: Int) = shot("mana_$variant") {
        val demo = ScreenshotTest().demo().envelope!!
        val envelope = when (variant) {
            4 -> demo.copy(spentMinor = 30_000, burnMinor = 3_000)
            5 -> demo.copy(spentMinor = demo.spentMinor + 6_000, balanceMinor = demo.balanceMinor - 6_000,
                spentTodayMinor = demo.spentTodayMinor + 6_000, leftTodayMinor = demo.leftTodayMinor - 6_000, burnMinor = 12_800)
            else -> demo
        }
        Column(Modifier.padding(top = Gap.s)) { ManaHero(envelope, variant) }
    }

    @Test fun mana2A() = wholeMana(1)
    @Test fun mana2B() = wholeMana(2)
    @Test fun mana2C() = wholeMana(3)
    @Test fun mana2D() = wholeMana(4)
    @Test fun mana2E() = wholeMana(5)

    private fun wholeMana(variant: Int) = shot("mana2_${variant - 1}") {
        Column(Modifier.padding(top = Gap.s)) { ManaHero(ScreenshotTest().demo().envelope!!, variant + 10) }
    }

    @Test fun mana2Over() = manaState(0)
    @Test fun mana2OnPlan() = manaState(1)
    @Test fun mana2Under() = manaState(2)
    @Test fun mana2BelowZero() = manaState(3)
    @Test fun mana2Start() = manaState(4)

    private fun manaState(state: Int) {
        val demo = ScreenshotTest().demo().envelope!!
        val funds = demo.fundsMinor
        val spent = when (state) {
            1 -> demo.normMinor
            2 -> funds / 5
            3 -> demo.spentMinor + 6_000
            4 -> 0L
            else -> demo.spentMinor
        }
        val daysLeft = if (state == 4) demo.totalDays else demo.daysLeft
        val spentToday = when (state) {
            3 -> demo.spentTodayMinor + 6_000
            4 -> 0L
            else -> demo.spentTodayMinor
        }
        val balance = funds - spent
        val plan = (balance + spentToday) / daysLeft
        val burn = when (state) {
            1 -> demo.normMinor / demo.elapsedDays
            2 -> 3_000L
            3 -> 12_800L
            4 -> null
            else -> demo.burnMinor
        }
        val envelope = demo.copy(balanceMinor = balance, spentMinor = spent, spentTodayMinor = spentToday,
            daysLeft = daysLeft, perDayMinor = plan, leftTodayMinor = plan - spentToday, burnMinor = burn,
            runwayDays = burn?.let { balance / it })
        assertTrue("Period funds stay fixed", envelope.fundsMinor == funds)
        when (state) {
            0 -> assertTrue(envelope.overNormMinor > 0)
            1 -> assertTrue(envelope.overNormMinor == 0L)
            2 -> assertTrue(envelope.overNormMinor < 0)
            3 -> assertTrue(envelope.leftTodayMinor < 0)
            4 -> assertTrue(envelope.spentShare == 0f && envelope.daysLeft == envelope.totalDays)
        }
        shot("mana2-states_$state") {
            Column(Modifier.padding(top = Gap.s)) { ManaHero(envelope, 11) }
        }
    }
}

private data class GoldCandidate(val hex: String, val onHex: String) {
    val container = Color(android.graphics.Color.parseColor(hex))
    val ink = Color(android.graphics.Color.parseColor(onHex))
}

private val GoldCandidates = listOf(
    GoldCandidate("#786126", "#FFF9EA"),
    GoldCandidate("#B4A16A", "#25210F"),
    GoldCandidate("#DACBA5", "#332C1A"),
    GoldCandidate("#8A6542", "#FFF9F0"),
    GoldCandidate("#A58A3D", "#211B0D"),
    GoldCandidate("#62643A", "#FFFBEA"),
)

@Composable
private fun AiryTabs(variant: Int) {
    val words = listOf(tr("Главная", "Home"), tr("Аналитика", "Insights"), tr("Счета", "Accounts"))
    if (variant == 0) {
        TabBar(words, { 0f }, {}, {})
        return
    }
    val scheme = MaterialTheme.colorScheme
    val pill = variant >= 3
    val edge = if (pill) Gap.s else HeaderSpace.group
    Row(Modifier.fillMaxWidth().padding(horizontal = edge), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(if (pill) HeaderSpace.chartCaption else HeaderSpace.block)) {
            words.forEachIndexed { index, word ->
                val selected = index == 0
                val surface = when {
                    !selected || !pill -> Color.Transparent
                    variant == 4 -> Color(0xFFDACBA5).copy(alpha = 0.45f)
                    else -> scheme.surfaceContainerHighest.copy(alpha = 0.55f)
                }
                Box(
                    Modifier.height(48.dp).background(surface, CircleShape)
                        .padding(horizontal = if (pill) HeaderSpace.valueCaption else 0.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(word, style = TextRole.rowName.copy(fontWeight = if (selected) TextRole.Accent else TextRole.Base),
                        color = if (selected) scheme.onSurface else scheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.weight(1f).widthIn(min = HeaderSpace.valueCaption))
        IconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_settings), tr("Настройки", "Settings"), tint = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IslandPage(variant: Int, recording: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val sample = remember { intArrayOf(0) }
    val levels = listOf(0.01f, 0.03f, 0.08f, 0.16f, 0.28f, 0.12f, 0.06f, 0.02f,
        0.05f, 0.18f, 0.30f, 0.14f, 0.07f, 0.025f, 0.01f, 0.02f)
    val colors = when (variant) {
        -1 -> LocalEntryColors.current.let { it.container to it.content }
        0 -> scheme.surfaceContainerHigh to scheme.onSurface
        1 -> scheme.primaryContainer to scheme.onPrimaryContainer
        2 -> scheme.inverseSurface to scheme.inverseOnSurface
        else -> GoldCandidates[variant - 3].let { it.container to it.ink }
    }
    Scaffold(
        topBar = { PreviewTabs(0) },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            CompositionLocalProvider(LocalEntryColors provides ActionColors(colors.first, colors.second)) {
                EntryBar(VoiceControl(if (recording) VoiceState.Recording else VoiceState.Idle, {}, level = { levels[sample[0]++ % levels.size] }, camera = {}), {})
            }
        },
    ) { padding ->
        HomeScreen(ScreenshotTest().demo(), padding, rememberLazyListState(), {}, onEdit = {})
    }
}

/** The existing Home card copied here so bar experiments never change the shipping screen. */
@Composable
private fun ManaHero(envelope: Envelope, variant: Int) {
    val ink = MaterialTheme.colorScheme.onPrimaryFixed
    HeroTile(Modifier.padding(horizontal = Gap.s), content = ink, vertical = HeaderSpace.edge) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Остаток дня", "Left today"), Modifier.weight(1f).letters(TextRole.groupTitle.fontSize), style = TextRole.groupTitle, color = ink)
            Box(Modifier.height(0.dp).wrapContentHeight(unbounded = true)) {
                val days = envelope.daysLeft
                CornerPill(tr("Ещё $days ", "$days ") + plural(days, "день", "дня", "дней", "day", "days"), tr("Период", "Period"), onClick = {})
            }
        }
        VSpace(HeaderSpace.figureCaption)
        val figure = "${Fmt.whole(envelope.leftTodayMinor, envelope.currency)} ₾"
        val tail = envelope.dayOutOf?.let { tr("из ", "of ") + Fmt.whole(it, envelope.currency) }
        val sized = figure + "0".repeat(tail.orEmpty().length / 3)
        BoxWithConstraints {
            HeroAmount(figure, ink, Modifier.letters(heroFigureSize(sized, maxWidth)), signColor = ink, sizedFor = sized, tail = tail)
        }
        VSpace(HeaderSpace.block)
        if (variant >= 10) WholeMana(envelope, variant - 10) else ManaTiles(envelope, variant)
    }
}

/** A single remaining-money track; only its comparison with the plan changes between candidates. */
@Composable
private fun WholeMana(envelope: Envelope, variant: Int) {
    val scheme = MaterialTheme.colorScheme
    val tones = LocalTones.current
    val actual = 1f - envelope.spentShare
    val norm = if (envelope.fundsMinor > 0) {
        ((envelope.fundsMinor - envelope.normMinor).toDouble() / envelope.fundsMinor).toFloat().coerceIn(0f, 1f)
    } else 0f
    val neutral = scheme.secondary
    val track = tones.soft.copy(alpha = 0.55f)
    val ghost = neutral.copy(alpha = 0.35f)
    val delta = if (envelope.overNormMinor > 0) paceColor(Pace.OVER) else leitmotifColor(2, tones)
    val plate = scheme.primaryFixed
    Canvas(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(6.dp))) {
        val radius = CornerRadius(6.dp.toPx())
        val end = size.width * actual
        val planned = size.width * norm
        drawRoundRect(track, cornerRadius = radius)
        when (variant) {
            1 -> {
                // The striped deficit is a comparison, while the solid part is the money left.
                if (end < planned) {
                    drawRoundRect(delta.copy(alpha = 0.4f), Offset((end - radius.x).coerceAtLeast(0f), 0f),
                        Size(planned - (end - radius.x).coerceAtLeast(0f), size.height), radius)
                    clipRect(left = end, right = planned) {
                        var x = end - size.height
                        while (x < planned) {
                            drawLine(delta, Offset(x, size.height), Offset(x + size.height, 0f), 2.dp.toPx())
                            x += 10.dp.toPx()
                        }
                    }
                }
                if (end > 0f) drawRoundRect(neutral, size = Size(end, size.height), cornerRadius = radius)
                if (end > planned) drawRoundRect(delta, Offset(planned, 0f), Size(end - planned, size.height), radius)
            }
            2 -> {
                if (planned > 0f) drawRoundRect(ghost, size = Size(planned, size.height), cornerRadius = radius)
                if (end > 0f) drawRoundRect(neutral, size = Size(end, size.height), cornerRadius = radius)
            }
            3 -> {
                if (end > 0f) drawRoundRect(neutral, size = Size(end, size.height), cornerRadius = radius)
                // Two soft cuts mark the norm without a stroke through the fill.
                drawCircle(plate, 4.dp.toPx(), Offset(planned, 0f))
                drawCircle(plate, 4.dp.toPx(), Offset(planned, size.height))
            }
            4 -> {
                if (end > 0f) drawRoundRect(neutral, size = Size(end, 22.dp.toPx()), cornerRadius = radius)
                if (planned > 0f) drawRoundRect(tones.muted.copy(alpha = 0.5f), Offset(0f, 26.dp.toPx()),
                    Size(planned, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
            }
            5 -> {
                if (planned > 0f) drawRoundRect(tones.muted.copy(alpha = 0.5f), Offset(1.dp.toPx(), 1.dp.toPx()),
                    Size((planned - 2.dp.toPx()).coerceAtLeast(0f), size.height - 2.dp.toPx()), radius, style = Stroke(2.dp.toPx()))
                if (end > 0f) drawRoundRect(neutral, Offset(0f, 6.dp.toPx()), Size(end, 20.dp.toPx()), radius)
            }
        }
    }
    VSpace(HeaderSpace.valueCaption)
    val left = Fmt.whole(envelope.balanceMinor, envelope.currency)
    val spent = Fmt.whole(envelope.spentMinor, envelope.currency)
    Text(tr("Осталось $left ₾ · Ушло $spent ₾", "Left $left ₾ · Spent $spent ₾"),
        Modifier.letters(TextRole.caption.fontSize), style = TextRole.caption, color = scheme.onPrimaryFixed)
    VSpace(HeaderSpace.chartCaption)
    val planned = Fmt.whole((envelope.fundsMinor - envelope.normMinor).coerceAtLeast(0), envelope.currency)
    val difference = Fmt.whole(kotlin.math.abs(envelope.overNormMinor), envelope.currency)
    val status = when {
        envelope.overNormMinor > 0 -> tr("Сверх плана $difference ₾", "$difference ₾ over plan")
        envelope.overNormMinor < 0 -> tr("Запас к плану $difference ₾", "$difference ₾ ahead of plan")
        else -> tr("По плану", "On plan")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HeaderSpace.chartCaption)) {
        Box(Modifier.size(6.dp).background(if (envelope.overNormMinor == 0L) tones.muted else delta, CircleShape))
        Text(status, Modifier.letters(TextRole.caption.fontSize), style = TextRole.caption, color = scheme.onPrimaryFixed)
    }
    VSpace(HeaderSpace.chartCaption)
    Text(tr("По плану осталось $planned ₾", "Planned remainder $planned ₾"),
        Modifier.letters(TextRole.caption.fontSize), style = TextRole.caption)
    VSpace(HeaderSpace.valueCaption)
    val allowed = Fmt.whole(envelope.perDayMinor, envelope.currency)
    val pace = envelope.burnMinor?.let { Fmt.whole(it, envelope.currency) }
    Text(if (pace == null) tr("Трат пока нет · Можно $allowed ₾/день", "No spending yet · $allowed ₾/day available")
        else tr("Темп $pace ₾/день · Можно $allowed ₾/день", "Pace $pace ₾/day · $allowed ₾/day available"),
        Modifier.letters(TextRole.caption.fontSize), style = TextRole.caption)
}

@Composable
private fun ManaTiles(envelope: Envelope, variant: Int) {
    val tones = LocalTones.current
    val fill = when (envelope.light) {
        Light.GREEN -> leitmotifColor(2, tones)
        Light.YELLOW -> paceColor(Pace.LASTS)
        Light.RED -> paceColor(Pace.OVER)
    }
    val markerInk = MaterialTheme.colorScheme.onPrimaryFixed
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val room = maxWidth - 4.dp
        val share = if (variant == 0) envelope.spentShare else 1f - envelope.spentShare
        val width = (room * share).coerceIn(6.dp, room - 6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(width).height(32.dp).clip(RoundedCornerShape(6.dp)).background(fill))
            Box(Modifier.width(room - width).height(32.dp).clip(RoundedCornerShape(6.dp)).background(tones.soft))
        }
        if (variant >= 2) {
            // The planned remainder uses the period's norm, including the current day.
            val normShare = if (envelope.fundsMinor > 0) {
                ((envelope.fundsMinor - envelope.normMinor).toDouble() / envelope.fundsMinor).toFloat().coerceIn(0f, 1f)
            } else 0f
            Canvas(Modifier.fillMaxWidth().height(32.dp)) {
                val x = room.toPx() * normShare
                if (variant == 3) {
                    drawLine(markerInk, Offset(x, -8.dp.toPx()), Offset(x, -2.dp.toPx()), 2.dp.toPx())
                } else {
                    drawLine(markerInk, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                }
            }
        }
    }
    VSpace(ChartSpace.tilesToWords)
    Row(Modifier.fillMaxWidth()) {
        val left = variant != 0
        ManaWords(envelope, left, Alignment.Start, Modifier.weight(1f))
        ManaWords(envelope, !left, Alignment.End, Modifier)
    }
    VSpace(ChartSpace.wordToSum)
}

@Composable
private fun ManaWords(envelope: Envelope, left: Boolean, side: Alignment.Horizontal, modifier: Modifier) {
    val sum = if (left) envelope.balanceMinor.coerceAtLeast(0) else envelope.spentMinor.coerceAtLeast(0)
    val pace = if (left) envelope.perDayMinor else envelope.burnMinor
    Column(modifier, horizontalAlignment = side) {
        Text(if (left) tr("Осталось", "Left") else tr("Ушло", "Gone"), Modifier.letters(TextRole.caption.fontSize), style = TextRole.caption)
        VSpace(ChartSpace.wordToSum)
        Text("${Fmt.whole(sum, envelope.currency)} ₾", Modifier.letters(TextRole.rowAmount.fontSize), style = TextRole.rowAmount, color = MaterialTheme.colorScheme.onPrimaryFixed)
        pace?.let {
            VSpace(ChartSpace.wordToSum)
            val amount = Fmt.whole(it, envelope.currency)
            Text(tr("$amount в день", "$amount a day"), Modifier.letters(TextRole.caption.fontSize), style = TextRole.caption)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewTabs(variant: Int) {
    val words = listOf(tr("Главная", "Home"), tr("Аналитика", "Insights"), tr("Счета", "Accounts"))
    if (variant == 0) {
        TabBar(words, { 0f }, {}, {})
        return
    }
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = Gap.s), verticalAlignment = Alignment.CenterVertically) {
        if (variant <= 2) {
            PrimaryTabRow(
                selectedTabIndex = 0,
                modifier = Modifier.weight(1f),
                containerColor = scheme.background,
                contentColor = scheme.onSurface,
                divider = {},
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        Modifier.tabIndicatorOffset(0, matchContentSize = variant == 1),
                        width = Dp.Unspecified,
                        color = scheme.onSurface,
                    )
                },
            ) {
                words.forEachIndexed { index, word ->
                    Tab(selected = index == 0, onClick = {}, text = {
                        Text(word, style = TextRole.rowName.copy(fontWeight = if (index == 0) TextRole.Accent else TextRole.Base), maxLines = 1)
                    })
                }
            }
        } else {
            Row(Modifier.weight(1f)) {
                words.forEachIndexed { index, word ->
                    Box(Modifier.weight(1f).height(TabBarHeight), contentAlignment = Alignment.Center) {
                        Text(
                            word,
                            modifier = Modifier.background(if (variant == 3 && index == 0) scheme.secondaryContainer else scheme.background, CircleShape)
                                .padding(horizontal = Gap.s, vertical = Gap.xs),
                            style = TextRole.rowName.copy(fontWeight = if (index == 0) TextRole.Accent else TextRole.Base),
                            color = scheme.onSurface,
                            maxLines = 1,
                        )
                        if (variant == 4 && index == 0) {
                            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Gap.xs).size(4.dp).background(scheme.onSurface, CircleShape))
                        }
                    }
                }
            }
        }
        IconButton(onClick = {}) { Icon(painterResource(R.drawable.ic_settings), tr("Настройки", "Settings")) }
    }
}
