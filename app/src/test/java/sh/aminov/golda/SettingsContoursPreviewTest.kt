package sh.aminov.golda

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
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
import sh.aminov.golda.ui.CardTitle
import sh.aminov.golda.ui.CheckSwitch
import sh.aminov.golda.ui.ChoiceGroup
import sh.aminov.golda.ui.ChoiceText
import sh.aminov.golda.ui.Gap
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.GroupRow
import sh.aminov.golda.ui.ListSpace
import sh.aminov.golda.ui.Palette
import sh.aminov.golda.ui.RowIcon
import sh.aminov.golda.ui.SettingsScreen
import sh.aminov.golda.ui.ThemeScreen
import sh.aminov.golda.ui.Tones
import sh.aminov.golda.ui.bleed
import java.util.TimeZone

/**
 * TEMPORARY (docs/tasks/settings-contours.md): Settings and the Theme page as they are, and two ways to
 * lay them out in levels, drawn from the app's own pieces (CardTitle, GroupRow, RowIcon, ChoiceGroup) on
 * Golda light, «Просторно». Nothing here is wired; the prototypes only show the order and the levels.
 * Excluded from pitest. Delete once the user has picked.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2000dp-xxhdpi")
class SettingsContoursPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private val dir = "build/settings-contours"

    private fun frames(vararg shots: Pair<String, @Composable () -> Unit>) {
        var current by mutableStateOf(0)
        compose.setContent {
            GoldaTheme("golda", "LIGHT", "ROOMY") {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { shots[current].second() }
            }
        }
        shots.forEachIndexed { i, (name, _) ->
            current = i
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("$dir/$name.png")
        }
    }

    @Test
    fun now() = frames(
        "now_settings" to { SettingsScreen(ScreenshotTest().demo(), {}, {}, SnackbarHostState(), {}, {}, {}, {}, {}) },
        "now_theme" to { ThemeScreen("golda", "SYSTEM", emptyList(), {}, {}, {}, {}, {}, spacing = "ROOMY") },
    )

    @Test
    fun variantA() = frames(
        "a1_settings" to { SettingsA() },
        "a2_theme" to { ThemeA() },
        "a3_theme_fine" to { ThemeFine() },
        "a4_theme_own" to { ThemeOwn() },
        "a5_data" to { DataPage() },
    )

    @Test
    fun variantB() = frames(
        "b1_settings" to { SettingsB() },
        "b2_money" to { MoneyPage() },
        "b3_theme" to { ThemeB() },
        "b4_theme_own" to { ThemeOwn() },
    )
}

// The prototypes' pieces. A row: a value in the second line, an arrow-less row opens its page or sheet.

private class Item(val title: String, val value: String? = null, @param:DrawableRes val icon: Int? = null, val switch: Boolean? = null, val danger: Boolean = false)

@Composable
private fun Page(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Gap.m)) {
        Row(Modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_back), null, Modifier.padding(start = Gap.s))
        }
        Text(title, Modifier.padding(start = Gap.s, top = 24.dp, bottom = 32.dp), style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold))
        content()
    }
}

@Composable
private fun Card(title: String?, vararg items: Item, gap: Dp = 0.dp) {
    if (gap > 0.dp) Box(Modifier.height(gap))
    val icons = items.any { it.icon != null }
    if (title != null) CardTitle(title, quiet = false, icons = icons)
    val head = if (title != null) 1 else 0
    items.forEachIndexed { i, it ->
        GroupRow(
            i + head, items.size + head,
            onClick = {},
            leading = it.icon?.let { icon -> { RowIcon(icon) } },
            supporting = it.value?.let { v -> { Text(v) } },
            trailing = it.switch?.let { on -> { CheckSwitch(on, null) } },
            trailingOnTitle = false,
        ) { Text(it.title, color = if (it.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun CardsGap() = Box(Modifier.height(ListSpace.cards))

@Composable
private fun Modes(selected: Int = 0) {
    ChoiceGroup(listOf(0, 1, 2), selected, {}, Modifier.bleed(Gap.s).fillMaxWidth()) {
        ChoiceText(listOf("Как система", "Светлая", "Тёмная")[it])
    }
}

/** The ready themes as the Theme page shows them: a chip of the page, the hero, three dots. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(label: String, items: List<Palette>, picked: Palette? = Palette.GOLDA) {
    Text(label, Modifier.padding(start = Gap.s, top = 26.dp, bottom = 16.dp), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap.s), verticalArrangement = Arrangement.spacedBy(Gap.s)) {
        items.forEach { p -> Chip(p.label, p.light, p == picked) }
    }
}

@Composable
private fun Chip(name: String, t: Tones, on: Boolean) {
    val fill = if (on) MaterialTheme.colorScheme.secondary else androidx.compose.ui.graphics.Color.Transparent
    Column(Modifier.width(88.dp).clip(RoundedCornerShape(20.dp)).background(fill).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).background(t.page).padding(8.dp)) {
            Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(8.dp)).background(t.plate))
            Row(Modifier.align(Alignment.BottomStart), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(t.graphite, t.gold, t.text).forEach { Box(Modifier.size(14.dp).clip(CircleShape).background(it)) }
            }
        }
        Text(name, Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

// Variant A: by how often. The first screen holds what a trip needs and how the app looks; the rest are one
// row each, a level down.

@Composable
private fun SettingsA() = Page("Настройки") {
    Card("Где я",
        Item("Местная валюта", "₾ GEL"),
        Item("Основная валюта", "₽ RUB"),
        Item("Валюты", "₽ $ ₾"),
    )
    CardsGap()
    Card("Курс",
        Item("Наценка к ЦБ", "10 %"),
        Item("Обновить курс", "1 $ = 101,2 ₽ · 1 ₾ = 37,4 ₽ · ЦБ на 24 октября"),
    )
    CardsGap()
    Card("Вид",
        Item("Тема", "Golda · как система"),
        Item("Язык", "Как в системе"),
    )
    CardsGap()
    Card(null,
        Item("Голос и разборы", "Gemini · ключа нет"),
        Item("Копии и данные", "Копия раз в неделю выключена"),
        Item("Напоминать о сверке", "По воскресеньям", switch = true),
    )
}

@Composable
private fun ThemeA() = Page("Тема") {
    Modes()
    Chips("Спокойные", Palette.entries.filter { !it.bold })
    Chips("Смелые", Palette.entries.filter { it.bold })
    CardsGap()
    Card(null,
        Item("Своя тема", "Из одного цвета"),
        Item("Тонкие настройки", "Плотность, чистый чёрный, цветные траты"),
    )
}

@Composable
private fun ThemeFine() = Page("Тонкие настройки") {
    Card("Списки",
        Item("Плотность", "Просторно"),
        Item("Цветные траты", switch = true),
    )
    CardsGap()
    Card("Тёмная тема",
        Item("Чистый чёрный", switch = false),
    )
}

@Composable
private fun ThemeOwn() = Page("Своя тема") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (0 until 6).forEach { Box(Modifier.size(40.dp).clip(CircleShape).background(sh.aminov.golda.ui.oklch(0.72, 0.14, it * 30.0))) }
    }
    Box(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (6 until 12).forEach { Box(Modifier.size(40.dp).clip(CircleShape).background(sh.aminov.golda.ui.oklch(0.72, 0.14, it * 30.0))) }
    }
    CardsGap()
    Card(null,
        Item("Из обоев"),
        Item("Случайная"),
    )
    CardsGap()
    Text("Стиль", Modifier.padding(start = Gap.s, bottom = 16.dp), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
    ChoiceGroup(listOf(0, 1, 2, 3), 0, {}, Modifier.bleed(Gap.s).fillMaxWidth()) { ChoiceText(listOf("Спокойная", "Яркая", "Выразит.", "Моно")[it]) }
    Text("Контраст", Modifier.padding(start = Gap.s, top = 26.dp, bottom = 16.dp), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
    ChoiceGroup(listOf(0, 1, 2), 1, {}, Modifier.bleed(Gap.s).fillMaxWidth()) { ChoiceText(listOf("Мягкий", "Обычный", "Высокий")[it]) }
}

@Composable
private fun DataPage() = Page("Копии и данные") {
    Card("Копия",
        Item("Копия каждую неделю", switch = false),
        Item("Сохранить копию сейчас"),
        Item("Восстановить из файла"),
    )
    CardsGap()
    Card(null, Item("Стереть всё", danger = true))
}

// Variant B: the first screen is only the sections, as Android's own settings; everything is a level down.

@Composable
private fun SettingsB() = Page("Настройки") {
    Card(null,
        Item("Валюты и курс", "₾ местная · ₽ основная · наценка 10 %", R.drawable.ic_cash),
        Item("Тема", "Golda · как система", R.drawable.ic_home),
        Item("Голос и разборы", "Gemini · ключа нет", R.drawable.ic_mic),
        Item("Копии и данные", "Копия раз в неделю выключена", R.drawable.ic_card),
        Item("Напоминания", "Сверка по воскресеньям", R.drawable.ic_calendar),
        Item("Язык", "Как в системе", R.drawable.ic_send),
    )
}

@Composable
private fun MoneyPage() = Page("Валюты и курс") {
    Card("Где я",
        Item("Местная валюта", "₾ GEL"),
        Item("Основная валюта", "₽ RUB"),
        Item("Валюты", "₽ $ ₾"),
    )
    CardsGap()
    Card("Курс",
        Item("Наценка к ЦБ", "10 %"),
        Item("1 $ = 101,2 ₽", "ЦБ 92"),
        Item("1 ₾ = 37,4 ₽", "ЦБ 34"),
        Item("Обновить курс", "ЦБ на 24 октября"),
    )
}

@Composable
private fun ThemeB() = Page("Тема") {
    Modes()
    Box(Modifier.height(16.dp))
    Card(null,
        Item("Плотность", "Просторно"),
        Item("Цветные траты", switch = true),
        Item("Чистый чёрный", switch = false),
    )
    Chips("Спокойные", Palette.entries.filter { !it.bold })
    Chips("Смелые", Palette.entries.filter { it.bold })
    CardsGap()
    Card(null, Item("Своя тема", "Из одного цвета", R.drawable.ic_edit))
}
