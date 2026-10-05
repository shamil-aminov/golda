package sh.aminov.golda.ui

import kotlin.math.roundToInt
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.label
import sh.aminov.golda.domain.tr
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** Inside a sheet, back first hides the keyboard instead of throwing the half-typed form away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackHidesKeyboard() {
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = WindowInsets.isImeVisible) { keyboard?.hide() }
}

/**
 * A number whose characters roll to their new values when [value] changes: up when it grows,
 * down when it shrinks. Characters in [quiet] (the kopecks) take [quietColor].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RollingNumber(
    text: String,
    value: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    quiet: IntRange = IntRange.EMPTY,
    quietColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val last = remember { mutableLongStateOf(value) }
    val rising = value >= last.longValue
    SideEffect { last.longValue = value }
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val tabular = style.merge(Tnum)
    // The currency symbol after the last space stands still and sits on the digits' baseline. It may
    // come from a fallback font ("฿") with other metrics, so it is aligned by baseline. The digits
    // all share one font and simply top-align; their baseline is measured once from a static "0",
    // never from the rolling digits, whose alignment lines go stale mid-roll.
    val space = text.lastIndexOf(' ')
    val symbol = text.substring(space + 1).takeIf { space > 0 && it.isNotEmpty() && it.none(Char::isDigit) }
    val body = if (symbol != null) text.substring(0, space + 1) else text
    val measurer = rememberTextMeasurer()
    val baseline = remember(tabular) { measurer.measure("0", tabular).firstBaseline }
    Row(modifier.clearAndSetSemantics { contentDescription = text }) {
        Row(Modifier.alignBy { baseline.roundToInt() }) {
            // Keyed from the right, so the ones stay the ones when the number grows a digit.
            body.forEachIndexed { i, char ->
                key(body.length - i) {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            (slideInVertically(spec) { if (rising) it else -it } togetherWith slideOutVertically(spec) { if (rising) -it else it })
                                .using(SizeTransform(clip = true))
                        },
                        label = "digit",
                    ) { c -> Text(c.toString(), style = tabular, color = if (i in quiet) quietColor else color, maxLines = 1) }
                }
            }
        }
        if (symbol != null) Text(symbol, Modifier.alignByBaseline(), style = tabular, color = color, maxLines = 1)
    }
}

/** "174,75 $": the cents, for [RollingNumber.quiet]. */
fun centsOf(amount: String): IntRange {
    val comma = amount.indexOf(',')
    return if (comma < 0) IntRange.EMPTY else comma until amount.lastIndexOf(' ')
}

/**
 * A rolling number that fills the width it is given, up to [maxSp]: the size comes from the
 * available width and the number of characters (tabular figures run about 0.6 em wide).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BigNumber(
    text: String,
    value: Long,
    color: Color,
    modifier: Modifier = Modifier,
    maxSp: Float = 120f,
    alignment: Alignment = Alignment.CenterStart,
    quiet: IntRange = IntRange.EMPTY,
    quietColor: Color = color,
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = alignment) {
        // Emphasized digits run about two thirds of an em wide; keep a little air at the edge.
        val fit = with(LocalDensity.current) { (maxWidth * 0.94f / (text.length * 0.66f)).toSp().value }
        val size = minOf(fit, maxSp).sp
        val style = MaterialTheme.typography.displayLargeEmphasized.copy(fontSize = size, lineHeight = size * 1.1f)
        RollingNumber(text, value, style, color = color, quiet = quiet, quietColor = quietColor)
    }
}

/** A trailing amount: one line by default; [secondary] only where the second currency matters. */
@Composable
fun AmountColumn(
    main: String,
    secondary: String? = null,
    approximate: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(horizontalAlignment = Alignment.End) {
        Text(main, style = MaterialTheme.typography.titleMedium.merge(Tnum), color = color, maxLines = 1)
        if (secondary != null) {
            Text(
                (if (approximate) "≈ " else "") + secondary,
                style = MaterialTheme.typography.bodySmall.merge(Tnum),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Operations by day, each day a grouped list under its label, the way Accounts and Settings are.
 * On an account's page pass [accountId]: amounts are then that account's own side, in two lines.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.operationItems(data: AppData, operations: List<OperationFull>, accountId: Long? = null, onClick: (OperationFull) -> Unit) {
    val today = data.today
    operations.groupBy { Ledger.localDate(it.op.timestamp, data.zone) }.forEach { (date, list) ->
        item(key = "day-$date") { GroupLabel(dayLabel(date, today), Modifier.padding(horizontal = Gap.m).padding(top = Gap.l)) }
        itemsIndexed(list, key = { _, it -> it.op.id }) { i, it ->
            // New entries slide in, the rest make room.
            OperationRow(
                data, it, accountId, onClick, i, list.size,
                Modifier.padding(horizontal = Gap.m).animateItem(placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
            )
        }
    }
}

@Composable
fun OperationRow(data: AppData, full: OperationFull, accountId: Long?, onClick: (OperationFull) -> Unit, index: Int, count: Int, modifier: Modifier = Modifier) {
    val op = full.op
    val category = op.categoryId?.let { data.categoryById[it] }
    val out = full.postings.firstOrNull { it.amountMinor < 0 } ?: full.postings.first()
    val into = full.postings.firstOrNull { it.amountMinor > 0 && it !== out }
    val outAccount = data.accountById[out.accountId]
    val intoAccount = into?.let { data.accountById[it.accountId] }
    var title = op.note.ifBlank { category?.label() ?: if (op.type == OpType.ADJUSTMENT) tr("Сверка", "Reconciliation") else if (op.type == OpType.TRANSFER) tr("Перевод", "Transfer") else tr("Без категории", "No category") }
    val code = outAccount?.currency ?: "RUB"
    // On an account's page, the amount is this account's own posting, signed as the account sees it.
    val own = accountId?.let { id -> full.postings.firstOrNull { it.accountId == id } }
    val ownCode = own?.let { data.accountById[it.accountId]?.currency } ?: code

    var main: String
    var secondary: String? = null
    var approximate = false
    var supporting: String? = null
    val transfer = op.type == OpType.TRANSFER && into != null && intoAccount != null
    // The price as said or paid, signed the way the money went: a voice income in dollars is "+100 $".
    val said = op.purchaseAmountMinor?.let { if (op.type == OpType.INCOME) it else -it }
    // An estimated amount (a card charge or a received sum worked out at a rate) carries "≈".
    fun estimated(text: String) = if (op.isEstimate) "≈ $text" else text
    when {
        transfer && own != null -> {
            val incoming = own.amountMinor > 0
            val otherAccount = if (incoming) outAccount else intoAccount
            val otherCode = otherAccount?.currency ?: code
            val signed = Fmt.amount(own.amountMinor, ownCode, signed = true)
            // What arrived is the estimated side of a transfer.
            main = if (incoming) estimated(signed) else signed
            if (otherCode != ownCode) secondary = Fmt.amount(abs((if (incoming) out else into).amountMinor), otherCode)
            title = "${outAccount?.name} → ${intoAccount.name}"
            supporting = op.note.takeUnless { it.isBlank() || restatesTransfer(it, outAccount?.name, intoAccount.name) }
        }
        transfer -> {
            // A transfer is neither spent nor earned: no sign, a neutral color.
            main = Fmt.amount(-out.amountMinor, code)
            if (intoAccount.currency != code) {
                secondary = "→ " + (if (op.isEstimate) "≈ " else "") + Fmt.amount(into.amountMinor, intoAccount.currency)
            }
            // The accounts say what a transfer is; a note, if any, goes underneath.
            title = "${outAccount?.name} → ${intoAccount.name}"
            supporting = op.note.takeUnless { it.isBlank() || restatesTransfer(it, outAccount?.name, intoAccount.name) }
        }
        own != null -> {
            main = Fmt.amount(own.amountMinor, ownCode, signed = true)
            if (op.purchaseCurrency != null && said != null) {
                // The account's side was worked out from the price: "≈ −18,99 $" over "−48,50 ₾".
                main = estimated(main)
                secondary = Fmt.amount(said, op.purchaseCurrency, signed = op.type == OpType.INCOME)
            } else if (ownCode != data.base.code) {
                // What the account's own amount comes to in the main currency.
                secondary = data.base.approx(own.rubMinor)
                approximate = true
            }
        }
        // On Home one amount says it: the price in the currency it was paid in.
        op.purchaseCurrency != null && said != null -> {
            main = Fmt.amount(said, op.purchaseCurrency, signed = op.type == OpType.INCOME)
            supporting = outAccount?.takeIf { it.id != data.usualAccountId }?.name
        }
        else -> {
            main = Fmt.amount(out.amountMinor, code, signed = true)
            supporting = outAccount?.takeIf { it.id != data.usualAccountId }?.name
        }
    }
    val color = when {
        transfer -> MaterialTheme.colorScheme.onSurfaceVariant
        op.type == OpType.INCOME || (own ?: out).amountMinor > 0 -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface
    }

    GroupRow(
        index, count, modifier,
        onClick = { onClick(full) },
        leading = { GlyphCircle(operationIcon(category, op.type)) },
        supporting = supporting?.let { text -> { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailing = { AmountColumn(main, secondary, approximate, color) },
    ) { Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/**
 * Whether a transfer's note only says what "A → B" already does: it starts with "перевод"/"transfer",
 * or names both accounts (by the first four letters of their first word).
 */
private fun restatesTransfer(note: String, from: String?, to: String?): Boolean {
    val text = note.lowercase()
    if (text.startsWith("перевод") || text.startsWith("transfer")) return true
    fun stem(name: String?) = name?.lowercase()?.split(' ', '-')?.firstOrNull { it.length >= 3 }?.take(4)
    val a = stem(from) ?: return false
    val b = stem(to) ?: return false
    return a in text && b in text
}

/** A number typed as text: keeps what was typed while it does not parse yet. */
@Composable
fun NumberField(
    value: Double,
    onValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    suffix: String? = null,
    decimals: Boolean = true,
) {
    var text by rememberSaveable { mutableStateOf(if (value == 0.0) "" else Fmt.number(value)) }
    TonalField(
        value = text,
        onValueChange = { new ->
            text = new
            // An emptied field is zero, not the last number that parsed; a negative number is not taken.
            if (new.isBlank()) onValue(0.0) else Fmt.parseDouble(new)?.takeIf { it >= 0 }?.let(onValue)
        },
        modifier = modifier,
        label = label,
        suffix = suffix,
        keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number,
    )
}

/** Hourly or monthly pay, tax, hours and payday: onboarding's first step. */
@Composable
fun IncomeFields(settings: Settings, onChange: ((Settings) -> Settings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap.s)) {
        ChoiceGroup(
            options = listOf(true, false),
            selected = settings.incomeHourly,
            onSelect = { hourly -> onChange { it.copy(incomeHourly = hourly) } },
            modifier = Modifier.fillMaxWidth(),
        ) { ChoiceText(if (it) tr("В час", "Hourly") else tr("В месяц", "Monthly")) }
        if (settings.incomeHourly) {
            NumberField(settings.hourlyRate, { v -> onChange { it.copy(hourlyRate = v) } }, Modifier.fillMaxWidth(), tr("Ставка в час", "Rate per hour"), "₽")
        } else {
            NumberField(settings.monthlySalary, { v -> onChange { it.copy(monthlySalary = v) } }, Modifier.fillMaxWidth(), tr("Зарплата в месяц", "Salary per month"), "₽")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
            NumberField(settings.taxPercent, { v -> onChange { it.copy(taxPercent = v) } }, Modifier.weight(1f), tr("Налог", "Tax"), "%")
            NumberField(settings.hoursPerWeek, { v -> onChange { it.copy(hoursPerWeek = v) } }, Modifier.weight(1f), tr("Часов в неделю", "Hours a week"))
        }
        NumberField(
            settings.payday.toDouble(),
            { v -> onChange { it.copy(payday = v.toInt().coerceIn(1, 31)) } },
            Modifier.fillMaxWidth(),
            tr("День зарплаты", "Payday"),
            decimals = false,
        )
        Text(
            tr("Час на руки ", "An hour after tax ") + Fmt.approx(settings.hourNet, "RUB"),
            modifier = Modifier.padding(top = Gap.s),
            style = MaterialTheme.typography.titleMedium.merge(Tnum),
        )
    }
}

/** What last month's pay came to, for the income group in settings. */
fun lastMonthPay(settings: Settings): String {
    val lastMonth = YearMonth.now().minusMonths(1)
    return tr("за ${monthName(lastMonth.atDay(1))} ≈ ", "${monthName(lastMonth.atDay(1))} ≈ ") + Fmt.approx(settings.salaryFor(lastMonth), "RUB")
}

/** Toggling a currency in the shown ones; the ruble always stays, and the local one follows. */
fun toggleDisplayCurrency(s: Settings, code: String): Settings {
    if (code == "RUB") return s
    val list = if (code in s.displayCurrencies) s.displayCurrencies - code else s.displayCurrencies + code
    // A hidden currency can't stay local or main: both fall back to rubles.
    val local = if (s.localCurrency in list) s.localCurrency else "RUB"
    val base = if (s.baseCurrency in list) s.baseCurrency else "RUB"
    return s.copy(displayCurrencies = Currencies.common.filter { it in list }, localCurrency = local, baseCurrency = base)
}

/** Every currency as a toggle in one fixed order, so nothing jumps when one is switched. */
@Composable
fun DisplayCurrencyToggles(settings: Settings, onChange: ((Settings) -> Settings) -> Unit) {
    ToggleFlow(
        options = Currencies.common,
        isOn = { it in settings.displayCurrencies },
        onToggle = { code -> onChange { toggleDisplayCurrency(it, code) } },
        label = ::currencyLabel,
    )
}

/** Where you are now, out of the shown currencies. */
@Composable
fun LocalCurrencyChoice(settings: Settings, onChange: ((Settings) -> Settings) -> Unit) {
    ShownCurrencyChoice(settings, settings.localCurrency) { code -> onChange { it.copy(localCurrency = code) } }
}

/** The main currency, out of the shown currencies. */
@Composable
fun BaseCurrencyChoice(settings: Settings, onChange: ((Settings) -> Settings) -> Unit) {
    ShownCurrencyChoice(settings, settings.baseCurrency) { code -> onChange { it.copy(baseCurrency = code) } }
}

/** One of the shown currencies: a connected group, or loose toggles when there are many. */
@Composable
private fun ShownCurrencyChoice(settings: Settings, selected: String, onPick: (String) -> Unit) {
    val options = settings.displayCurrencies
    if (options.size <= 5) {
        ChoiceGroup(options, selected, onPick, Modifier.fillMaxWidth()) { ChoiceText(currencyLabel(it)) }
    } else {
        ToggleFlow(options, { it == selected }, onPick, ::currencyLabel)
    }
}

/** Onboarding's currency step: what to show, then where you are. */
@Composable
fun CurrencyFields(settings: Settings, onChange: ((Settings) -> Settings) -> Unit) {
    Column {
        GroupLabel(tr("Показывать суммы в", "Show amounts in"))
        DisplayCurrencyToggles(settings, onChange)
        VSpace(Gap.l)
        GroupLabel(tr("Местная валюта — в ней суммы без валюты", "Local currency, for amounts said without one"))
        LocalCurrencyChoice(settings, onChange)
    }
}
