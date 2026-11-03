package sh.aminov.golda.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import sh.aminov.golda.R
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.tr

/** Which currency setting the list page changes. */
enum class CurrencyPick { SHOWN, LOCAL, BASE }

/** "Грузинский лари", from the system, in the app's language; the code when the system has no name. */
fun currencyName(code: String): String =
    runCatching { java.util.Currency.getInstance(code).getDisplayName(I18n.locale) }.getOrNull()
        ?.replaceFirstChar { it.titlecase(I18n.locale) } ?: code

/**
 * Every currency the app has a rate for, as a plain list with a search on top: the picked ones first,
 * the rest by name. The order is fixed while the page is open, so a row never moves under the finger;
 * next time the newly picked stand at the top. [CurrencyPick.SHOWN] picks several, the others one,
 * and picking one closes the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPage(data: AppData, pick: CurrencyPick, onChange: ((Settings) -> Settings) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val s = data.settings
    val picked: (String) -> Boolean = when (pick) {
        CurrencyPick.SHOWN -> { code -> code in s.displayCurrencies }
        CurrencyPick.LOCAL -> { code -> code == s.localCurrency }
        CurrencyPick.BASE -> { code -> code == s.baseCurrency }
    }
    // Fixed at opening: what was picked then, and the rest by name.
    val (top, rest) = remember {
        val all = (Currencies.common + data.rates.codes).distinct()
        val first = all.filter(picked).sortedBy { s.displayCurrencies.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
        first to (all - first.toSet()).sortedBy { currencyName(it) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    fun matches(code: String) = query.isBlank() || listOf(code, currencyName(code), Currencies.symbol(code)).any { it.contains(query.trim(), ignoreCase = true) }

    fun choose(code: String) {
        when (pick) {
            CurrencyPick.SHOWN -> onChange { toggleDisplayCurrency(it, code) }
            // A currency picked as local or main is shown too.
            CurrencyPick.LOCAL -> { onChange { it.copy(displayCurrencies = (it.displayCurrencies + code).distinct(), localCurrency = code) }; onBack() }
            CurrencyPick.BASE -> { onChange { it.copy(displayCurrencies = (it.displayCurrencies + code).distinct(), baseCurrency = code) }; onBack() }
        }
    }

    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val title = when (pick) {
        CurrencyPick.SHOWN -> tr("Валюты", "Currencies")
        CurrencyPick.LOCAL -> tr("Местная валюта", "Local currency")
        CurrencyPick.BASE -> tr("Основная валюта", "Main currency")
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(bar.nestedScrollConnection),
        topBar = { CollapsingHeader(title, onBack, bar) },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(start = Gap.m, end = Gap.m, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Gap.xl)) {
            item(key = "search") {
                TonalField(query, { query = it }, Modifier.fillMaxWidth().padding(top = Gap.s), placeholder = tr("Найти валюту", "Find a currency"))
            }
            val shownTop = top.filter(::matches)
            val shownRest = rest.filter(::matches)
            if (shownTop.isNotEmpty()) {
                item(key = "top-label") { GroupLabel(if (pick == CurrencyPick.SHOWN) tr("Выбраны", "Picked") else tr("Сейчас", "Now"), Modifier.padding(top = Gap.l)) }
                itemsIndexed(shownTop, key = { _, c -> "top-$c" }) { i, code -> CurrencyRow(code, picked(code), i, shownTop.size) { choose(code) } }
            }
            if (shownRest.isNotEmpty()) {
                item(key = "rest-label") { GroupLabel(tr("Все валюты", "All currencies"), Modifier.padding(top = Gap.l)) }
                itemsIndexed(shownRest, key = { _, c -> "rest-$c" }) { i, code -> CurrencyRow(code, picked(code), i, shownRest.size) { choose(code) } }
            }
        }
    }
}

/** One currency: its name, the code and sign under it, a check when picked. */
@Composable
private fun CurrencyRow(code: String, on: Boolean, index: Int, count: Int, onClick: () -> Unit) {
    GroupRow(
        index, count, Modifier.semantics { selected = on; role = Role.Checkbox },
        onClick = onClick,
        supporting = { Text(currencyLabel(code)) },
        trailing = if (on) ({ Icon(painterResource(R.drawable.ic_check), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface) }) else null,
        trailingOnTitle = false,
        container = if (on) unpickedColor() else tileColor(),
    ) { Text(currencyName(code), style = MaterialTheme.typography.titleMedium) }
}
