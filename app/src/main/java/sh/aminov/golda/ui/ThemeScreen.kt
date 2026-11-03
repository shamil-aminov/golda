package sh.aminov.golda.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import android.app.WallpaperManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sh.aminov.golda.R
import sh.aminov.golda.domain.tr

/** The theme's name for the settings row: a preset's own name, or "Своя" for a generated one. */
fun themeLabel(key: String?): String = if (ThemeSpec.parse(key) != null) tr("Своя", "Custom") else Palette.of(key).label

/**
 * The theme page. Light or dark on top, for every theme; then the ready themes, calm and bold, and
 * the kept ones; under them a generator that needs one colour and two choices (style, contrast) and
 * builds the rest. Every change is applied at once, so the page itself is the preview; the colour
 * slider applies when let go.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThemeScreen(
    current: String,
    mode: String,
    saved: List<String>,
    onBack: () -> Unit,
    onApply: (String) -> Unit,
    onMode: (String) -> Unit,
    onSave: (String) -> Unit,
    onForget: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    // The chips show the half the app is in now, so a pick looks the way it will.
    val dark = isDarkMode(mode, isSystemInDarkTheme())
    // The generator starts from the theme in use: its own spec, or a preset's hue.
    var spec by remember { mutableStateOf(ThemeSpec.parse(current) ?: ThemeSpec(hue = Palette.of(current).tones(dark).seedHue)) }
    fun apply(next: ThemeSpec) {
        spec = next
        onApply(next.key)
    }
    val custom = ThemeSpec.parse(current) != null
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        // Up top, like Android's own bars: a generated theme not kept yet can be kept; a kept one in use
        // can be deleted (it stays on, unkept, so "Сохранить" brings it back). Presets need neither.
        topBar = {
            CollapsingHeader(tr("Тема", "Theme"), onBack, bar) {
                when {
                    custom && current !in saved -> Button(
                        onClick = { onSave(current) },
                        modifier = Modifier.padding(end = Gap.s),
                        colors = ButtonDefaults.buttonColors(containerColor = actionColor(), contentColor = onActionColor()),
                    ) { Text(tr("Сохранить", "Save"), style = MaterialTheme.typography.labelLarge) }
                    current in saved -> IconButton(onClick = { onForget(current) }) {
                        Icon(painterResource(R.drawable.ic_delete), tr("Удалить тему", "Delete theme"))
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Gap.m).padding(bottom = Gap.l)) {
            // One switch for every theme, ready or own: each has a light and a dark half.
            GroupLabel(tr("Светлая или тёмная", "Light or dark"))
            VSpace(Gap.s)
            // Segmented rows on a page reach the cards' edge, 8 from the screen, as on Insights.
            ChoiceGroup(ThemeMode.entries, ThemeMode.entries.firstOrNull { it.name == mode } ?: ThemeMode.SYSTEM, { onMode(it.name) }, Modifier.bleed(Gap.s).fillMaxWidth()) {
                ChoiceText(
                    when (it) {
                        ThemeMode.SYSTEM -> tr("Как система", "System")
                        ThemeMode.LIGHT -> tr("Светлая", "Light")
                        ThemeMode.DARK -> tr("Тёмная", "Dark")
                    },
                )
            }
            // The ready themes in a grid, four to a row: twenty-odd do not fit a row to scroll.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val chip = (maxWidth - Gap.s * 3) / 4
                @Composable
                fun Grid(label: String, items: List<Triple<String, String, Tones>>, onLong: ((String) -> Unit)? = null) {
                    VSpace(Gap.l)
                    GroupLabel(label)
                    VSpace(Gap.s)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap.s), verticalArrangement = Arrangement.spacedBy(Gap.s)) {
                        items.forEach { (key, name, tones) ->
                            ThemeChip(name, tones, key == current, chip, onClick = {
                                ThemeSpec.parse(key)?.let { spec = it }
                                onApply(key)
                            }, onLongClick = onLong?.let { { it(key) } })
                        }
                    }
                }
                Column {
                    Grid(tr("Спокойные", "Calm"), Palette.entries.filter { !it.bold }.map { Triple(it.key, it.label, it.tones(dark)) })
                    Grid(tr("Смелые", "Bold"), Palette.entries.filter { it.bold }.map { Triple(it.key, it.label, it.tones(dark)) })
                    // A long press forgets a kept one.
                    if (saved.isNotEmpty()) {
                        Grid(tr("Сохранённые", "Kept"), saved.mapIndexed { i, key -> Triple(key, tr("Своя ", "Custom ") + (i + 1), tonesFor(key, dark)) }, onForget)
                    }
                }
            }
            VSpace(Gap.l)
            GroupLabel(tr("Своя из одного цвета", "Your own, from one colour"))
            VSpace(Gap.s)
            // Twelve hues to start from, and the slider for anything between them.
            // Two rows of six, each a 40 dp target that says which colour it is.
            (0 until 2).forEach { row ->
                if (row > 0) VSpace(Gap.s)
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (0 until 6).forEach { col ->
                        val hue = (row * 6 + col) * 30
                        // Round the wheel: 355° is next to 0°.
                        val distance = minOf((spec.hue - hue).mod(360), (hue - spec.hue).mod(360))
                        val on = custom && distance < 15
                        // Picked: a check on the colour, no ring.
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(oklch(0.72, 0.14, hue.toDouble()))
                                .selectable(selected = on, role = Role.RadioButton, onClick = { apply(spec.copy(hue = hue)) })
                                .semantics { contentDescription = hueName(hue) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(20.dp), tint = Color.Black.copy(alpha = 0.78f))
                        }
                    }
                }
            }
            VSpace(Gap.s)
            var dragging by remember { mutableStateOf<Float?>(null) }
            // A rainbow under a slider with clear tracks: the hue is picked on the colours themselves.
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(12.dp).clip(RoundedCornerShape(6.dp))
                        .background(Brush.horizontalGradient((0..12).map { oklch(0.72, 0.14, it * 30.0) })),
                )
                Slider(
                    value = dragging ?: spec.hue.toFloat(),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = { dragging?.let { apply(spec.copy(hue = it.toInt().mod(360))) }; dragging = null },
                    valueRange = 0f..359f,
                    colors = SliderDefaults.colors(
                        thumbColor = oklch(0.72, 0.14, (dragging ?: spec.hue.toFloat()).toDouble()),
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                    ),
                )
            }
            VSpace(Gap.s)
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                TonalAction(tr("Из обоев", "From wallpaper"), {
                    val seed = if (Build.VERSION.SDK_INT >= 27) {
                        WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
                    } else {
                        null
                    }
                    seed?.let { apply(ThemeSpec.of(Color(it)).copy(contrast = spec.contrast)) }
                })
                TonalAction(tr("Случайная", "Surprise me"), { apply(ThemeSpec.random().copy(contrast = spec.contrast)) })
            }

            VSpace(Gap.l)
            GroupLabel(tr("Стиль", "Style"))
            VSpace(Gap.s)
            // Segmented rows on a page reach the cards' edge, 8 from the screen, as on Insights.
            ChoiceGroup(ThemeStyle.entries, spec.style, { apply(spec.copy(style = it)) }, Modifier.bleed(Gap.s).fillMaxWidth()) {
                ChoiceText(
                    when (it) {
                        ThemeStyle.CALM -> tr("Тихая", "Calm")
                        ThemeStyle.VIVID -> tr("Яркая", "Vivid")
                        ThemeStyle.EXPRESSIVE -> tr("Смелая", "Bold")
                        ThemeStyle.MONO -> tr("Моно", "Mono")
                    },
                )
            }
            VSpace(Gap.l)
            GroupLabel(tr("Контраст", "Contrast"))
            VSpace(Gap.s)
            ChoiceGroup(listOf(0, 1, 2), spec.contrast, { apply(spec.copy(contrast = it)) }, Modifier.bleed(Gap.s).fillMaxWidth()) {
                ChoiceText(listOf(tr("Мягкий", "Soft"), tr("Обычный", "Normal"), tr("Высокий", "High"))[it])
            }
        }
    }
}

/**
 * A theme to pick: its page with the card, picked, action and ink on it, and its name under. Picked,
 * the whole chip sits on the picked fill with its name semibold, like every other choice.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThemeChip(name: String, t: Tones, on: Boolean, width: Dp, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val fill by animateColorAsState(if (on) pickedColor() else Color.Transparent, MaterialTheme.motionScheme.fastEffectsSpec(), label = "chip")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // Read as one: the theme's name and whether it is the one in use.
        modifier = Modifier.width(width).clip(RoundedCornerShape(20.dp)).background(fill)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) { selected = on }
            .padding(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).background(t.page).padding(8.dp)) {
            Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(8.dp)).background(t.card))
            Row(Modifier.align(Alignment.BottomStart), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(t.graphite))
                Box(Modifier.size(14.dp).clip(CircleShape).background(t.gold))
                Box(Modifier.size(14.dp).clip(CircleShape).background(t.text))
            }
        }
        VSpace(Gap.xs)
        Text(
            name,
            Modifier.padding(bottom = Gap.xs),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
            color = if (on) onPickedColor() else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}


/** A hue's name for TalkBack, by the nearest of twelve. */
private fun hueName(hue: Int): String = listOf(
    tr("розовый", "pink"), tr("красный", "red"), tr("оранжевый", "orange"), tr("золотой", "gold"), tr("салатовый", "lime"), tr("зелёный", "green"),
    tr("бирюзовый", "teal"), tr("голубой", "cyan"), tr("синий", "blue"), tr("сиреневый", "periwinkle"), tr("фиолетовый", "violet"), tr("пурпурный", "magenta"),
)[(hue / 30) % 12]
