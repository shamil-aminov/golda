package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.CollapsingHeader
import sh.aminov.golda.ui.Gap
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.GroupLabel
import sh.aminov.golda.ui.GroupRow
import sh.aminov.golda.ui.IconCircle
import sh.aminov.golda.ui.TonalField
import sh.aminov.golda.ui.bleed
import sh.aminov.golda.ui.currencyLabel
import sh.aminov.golda.ui.currencyName
import sh.aminov.golda.ui.onPickedColor
import sh.aminov.golda.ui.pickedColor
import sh.aminov.golda.ui.tileColor
import java.util.Locale
import java.util.TimeZone

/**
 * TEMPORARY (task `docs/tasks/currency-cards.md`): three mock-ups of the Currencies page as cards, drawn
 * into `docs/tasks/currency-cards/` for the owner to pick from. Delete once the chosen one is built.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CurrencyCardsPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private val picked = listOf("RUB", "USD", "GEL")
    private val rest = (Currencies.common + listOf("AUD", "AZN", "BYN", "GBP", "JPY", "KGS", "UZS", "CHF", "INR", "PLN"))
        .distinct().filter { it !in picked }.sortedBy { currencyName(it) }

    private fun shot(name: String, mode: String, content: @Composable () -> Unit) {
        // Drawn as they were shown to the owner, before the list density setting.
        compose.setContent { GoldaTheme("golda", mode, "COMPACT") { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() } } }
        compose.onRoot().captureRoboImage(
            "../docs/tasks/currency-cards/$name.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }

    @Test fun gridLight() = shot("a_grid_light", "LIGHT") { Page { grid(picked, rest) } }
    @Test fun gridDark() = shot("a_grid_dark", "DARK") { Page { grid(picked, rest) } }
    @Test fun mixedLight() = shot("b_mixed_light", "LIGHT") { Page { mixed(picked, rest) } }
    @Test fun mixedDark() = shot("b_mixed_dark", "DARK") { Page { mixed(picked, rest) } }
    @Test fun signRowsLight() = shot("c_sign_rows_light", "LIGHT") { Page { signRows(picked, rest) } }
    @Test fun signRowsDark() = shot("c_sign_rows_dark", "DARK") { Page { signRows(picked, rest) } }
}

/** The page as it is now: the folding header and the search over a list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Page(body: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(Modifier.fillMaxSize().nestedScroll(bar.nestedScrollConnection), topBar = { CollapsingHeader("Валюты", {}, bar) }) { padding ->
        LazyColumn(contentPadding = PaddingValues(start = Gap.m, end = Gap.m, top = padding.calculateTopPadding(), bottom = Gap.xl)) {
            item { TonalField("", {}, Modifier.bleed(Gap.s).fillMaxWidth().padding(top = Gap.s), placeholder = "Найти валюту") }
            body()
        }
    }
}

/** (а) Every currency a card, three across; the picked ones graphite, on top. */
private fun androidx.compose.foundation.lazy.LazyListScope.grid(picked: List<String>, rest: List<String>) {
    item { GroupLabel("Выбраны", Modifier.padding(top = Gap.l)) }
    cards(picked, picked)
    item { GroupLabel("Все валюты", Modifier.padding(top = Gap.l)) }
    cards(rest, picked)
}

/** (б) The picked ones as cards; the long rest a plain list, as now. */
private fun androidx.compose.foundation.lazy.LazyListScope.mixed(picked: List<String>, rest: List<String>) {
    item { GroupLabel("Выбраны", Modifier.padding(top = Gap.l)) }
    cards(picked, picked)
    item { GroupLabel("Все валюты", Modifier.padding(top = Gap.l)) }
    itemsIndexed(rest) { i, code ->
        GroupRow(i, rest.size, onClick = {}, supporting = { Text(currencyLabel(code)) }) { Text(currencyName(code), style = MaterialTheme.typography.titleMedium) }
    }
}

/** (в) Rows kept, the sign big in a circle at the start; the picked ones' circle graphite with a check. */
private fun androidx.compose.foundation.lazy.LazyListScope.signRows(picked: List<String>, rest: List<String>) {
    fun rows(codes: List<String>) = itemsIndexed(codes) { i, code ->
        val on = code in picked
        GroupRow(
            i, codes.size, onClick = {},
            leading = {
                IconCircle(Modifier.size(48.dp), color = if (on) pickedColor() else MaterialTheme.colorScheme.surface) {
                    Text(sign(code) ?: code, style = if (sign(code) != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge, color = if (on) onPickedColor() else MaterialTheme.colorScheme.onSurface)
                }
            },
            supporting = { Text(code + " · " + country(code), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailing = if (on) ({ Icon(painterResource(R.drawable.ic_check), null, Modifier.size(24.dp)) }) else null,
            trailingOnTitle = false,
        ) { Text(currencyName(code), style = MaterialTheme.typography.titleMedium) }
    }
    item { GroupLabel("Выбраны", Modifier.padding(top = Gap.l)) }
    rows(picked)
    item { GroupLabel("Все валюты", Modifier.padding(top = Gap.l)) }
    rows(rest)
}

private fun androidx.compose.foundation.lazy.LazyListScope.cards(codes: List<String>, picked: List<String>) {
    codes.chunked(3).forEach { three ->
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = Gap.s), horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                three.forEach { CurrencyCard(it, it in picked, Modifier.weight(1f)) }
                repeat(3 - three.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Code on top, the sign big, the country under it; a currency with no sign of its own shows its code big. */
@Composable
private fun CurrencyCard(code: String, on: Boolean, modifier: Modifier) {
    val ink = if (on) onPickedColor() else MaterialTheme.colorScheme.onSurface
    val quiet = if (on) onPickedColor().copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant
    val sign = sign(code)
    Surface(onClick = {}, modifier = modifier.height(120.dp), shape = MaterialTheme.shapes.extraLarge, color = if (on) pickedColor() else tileColor(), contentColor = ink) {
        Column(Modifier.padding(horizontal = Gap.s, vertical = Gap.m), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (sign != null) code else " ", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = quiet)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(sign ?: code, style = if (sign != null) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge, maxLines = 1)
            }
            Text(country(code), style = MaterialTheme.typography.bodyMedium, color = quiet, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The app's own sign, else the system's when it is not just the code. */
private fun sign(code: String): String? =
    Currencies.symbol(code).takeIf { it != code }
        ?: runCatching { java.util.Currency.getInstance(code).getSymbol(I18n.locale) }.getOrNull()?.takeIf { it != code && it.length <= 2 }

/** Names too long for a card, the way people say them. */
private val short = mapOf("US" to "США", "GB" to "Британия", "EU" to "Еврозона", "AE" to "ОАЭ")

/** ISO 4217 starts with the country's ISO 3166 code: "GE" for GEL; the euro's "EU" is the union. */
private fun country(code: String): String =
    short[code.take(2)] ?: Locale.Builder().setRegion(code.take(2)).build().getDisplayCountry(I18n.locale).takeIf { it.isNotBlank() && it != code.take(2) }
        ?.replaceFirstChar { it.titlecase(I18n.locale) } ?: currencyName(code)
