package sh.aminov.golda.ui

import android.app.WallpaperManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
 * The theme page. Ready palettes and kept themes in a row at the top; under them a generator that
 * needs one colour and three choices (style, light or dark, contrast) and builds the rest. Every
 * change is applied at once, so the page itself is the preview; the colour slider applies when let go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    current: String,
    saved: List<String>,
    onBack: () -> Unit,
    onApply: (String) -> Unit,
    onSave: (String) -> Unit,
    onForget: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    var spec by remember { mutableStateOf(ThemeSpec.parse(current) ?: ThemeSpec(hue = 85)) }
    fun apply(next: ThemeSpec) {
        spec = next
        onApply(next.key)
    }
    val custom = ThemeSpec.parse(current) != null
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { CollapsingHeader(tr("Тема", "Theme"), onBack, bar) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(Gap.m), horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                PrimaryAction(if (current in saved) tr("Тема сохранена", "Theme kept") else tr("Сохранить тему", "Keep this theme"), { onSave(current) }, enabled = custom && current !in saved)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Gap.m).padding(bottom = Gap.l)) {
            GroupLabel(tr("Готовые", "Ready"))
            VSpace(Gap.s)
            // The presets and the kept themes, each a swatch strip; a long press forgets a kept one.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                Palette.entries.forEach { palette ->
                    ThemeChip(palette.label, palette.tones(systemDark), palette.key == current, onClick = { onApply(palette.key) })
                }
                saved.forEachIndexed { i, key ->
                    ThemeChip(tr("Своя ", "Custom ") + (i + 1), tonesFor(key, systemDark), key == current, onClick = {
                        ThemeSpec.parse(key)?.let { spec = it }
                        onApply(key)
                    }, onLongClick = { onForget(key) })
                }
            }

            VSpace(Gap.l)
            GroupLabel(tr("Своя из одного цвета", "Your own, from one colour"))
            VSpace(Gap.s)
            // Twelve hues to start from, and the slider for anything between them.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (0 until 12).forEach { i ->
                    val hue = i * 30
                    val on = custom && kotlin.math.abs(spec.hue - hue) < 15
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(oklch(0.72, 0.14, hue.toDouble()))
                            .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .combinedClickable(onClick = { apply(spec.copy(hue = hue)) }),
                    )
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
                    seed?.let { apply(ThemeSpec.of(Color(it)).copy(mode = spec.mode, contrast = spec.contrast)) }
                })
                TonalAction(tr("Случайная", "Surprise me"), { apply(ThemeSpec.random().copy(mode = spec.mode, contrast = spec.contrast)) })
            }

            VSpace(Gap.l)
            Caption(tr("Стиль", "Style"))
            VSpace(Gap.s)
            ChoiceGroup(ThemeStyle.entries, spec.style.takeIf { custom }, { apply(spec.copy(style = it)) }, Modifier.fillMaxWidth()) {
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
            Caption(tr("Светлая или тёмная", "Light or dark"))
            VSpace(Gap.s)
            ChoiceGroup(ThemeMode.entries, spec.mode.takeIf { custom }, { apply(spec.copy(mode = it)) }, Modifier.fillMaxWidth()) {
                ChoiceText(
                    when (it) {
                        ThemeMode.SYSTEM -> tr("Как система", "System")
                        ThemeMode.LIGHT -> tr("Светлая", "Light")
                        ThemeMode.DARK -> tr("Тёмная", "Dark")
                    },
                )
            }
            VSpace(Gap.l)
            Caption(tr("Контраст", "Contrast"))
            VSpace(Gap.s)
            ChoiceGroup(listOf(0, 1, 2), spec.contrast.takeIf { custom }, { apply(spec.copy(contrast = it)) }, Modifier.fillMaxWidth()) {
                ChoiceText(listOf(tr("Мягкий", "Soft"), tr("Обычный", "Normal"), tr("Высокий", "High"))[it])
            }
        }
    }
}

/** A theme to pick: its page with the card, picked, action and ink on it, and its name under. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThemeChip(name: String, t: Tones, on: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Box(
            Modifier.size(76.dp, 64.dp).clip(RoundedCornerShape(16.dp)).background(t.page)
                .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(16.dp)) else Modifier)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(8.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(8.dp)).background(t.card))
            Row(Modifier.align(Alignment.BottomStart), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(t.graphite))
                Box(Modifier.size(14.dp).clip(CircleShape).background(t.gold))
                Box(Modifier.size(14.dp).clip(CircleShape).background(t.text))
            }
        }
        VSpace(Gap.xs)
        Text(name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1)
    }
}

