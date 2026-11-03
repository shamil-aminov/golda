package sh.aminov.golda.ui

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.aminov.golda.R
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * An account: the name big and bare, the type as five icon tiles, the currency as the connected
 * group of the shown ones (only when creating; after that it stands beside the name), how much is
 * there now as the hero number, and the few extras a savings account or a debt needs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    data: AppData,
    editing: Account?,
    onDismiss: () -> Unit,
    onSave: (Account, Long?) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var type by remember { mutableStateOf(editing?.type ?: AccountType.CARD) }
    var currency by remember { mutableStateOf(editing?.currency ?: data.settings.localCurrency) }
    var balanceText by remember { mutableStateOf("") }
    var rateText by remember { mutableStateOf(editing?.interestRate?.let { Fmt.number(it) }.orEmpty()) }
    var includeInFree by remember { mutableStateOf(editing?.includeInFree) }
    var paymentDayText by remember { mutableStateOf(editing?.paymentDay?.toString().orEmpty()) }
    var paymentText by remember { mutableStateOf(editing?.paymentMinor?.let { Fmt.editable(it, editing.currency) }.orEmpty()) }
    var graceUntil by remember { mutableStateOf(editing?.graceUntil?.let(LocalDate::ofEpochDay)) }
    var pickingGrace by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val debt = type == AccountType.CREDIT || type == AccountType.LOAN
    val free = includeInFree ?: (type == AccountType.CARD || type == AccountType.CASH)
    val balance = if (balanceText.isBlank()) 0L else Fmt.parseMinor(balanceText, currency)
    val rate = rateText.takeIf { it.isNotBlank() }?.let(Fmt::parseDouble)
    val paymentDay = paymentDayText.takeIf { it.isNotBlank() }?.toIntOrNull()?.takeIf { it in 1..31 }
    val payment = paymentText.takeIf { it.isNotBlank() }?.let { Fmt.parseMinor(it, currency) }
    val valid = name.isNotBlank() && balance != null && (rateText.isBlank() || rate != null) &&
        (paymentDayText.isBlank() || paymentDay != null) && (paymentText.isBlank() || payment != null)

    fun save() {
        if (!valid) return
        val account = Account(
            id = editing?.id ?: 0,
            name = name.trim(),
            currency = currency,
            type = type,
            // Groups are gone: one bank, one account. Saving an account drops its old group.
            groupName = null,
            includeInFree = free,
            interestRate = if (type == AccountType.SAVINGS || debt) rate else null,
            sort = editing?.sort ?: ((data.accounts.maxOfOrNull { it.sort } ?: -1) + 1),
            paymentDay = if (debt) paymentDay else null,
            paymentMinor = if (debt) payment else null,
            graceUntil = if (type == AccountType.CREDIT) graceUntil?.toEpochDay() else null,
        )
        onSave(account, if (debt) -balance else balance)
    }

    val focus = remember { FocusRequester() }
    FocusWhenShown(focus, enabled = editing == null)

    // Like the entry form: no bar across the top; back, delete and the main button at the bottom.
    FormPage(
        onClose = onDismiss,
        topBar = false,
        actions = {
            RoundAction(R.drawable.ic_back, tr("Назад", "Back"), onDismiss)
            if (editing != null) RoundAction(R.drawable.ic_delete, tr("Удалить", "Delete")) { confirmDelete = true }
            PrimaryAction(if (editing == null) tr("Добавить", "Add") else tr("Сохранить", "Save"), ::save, valid)
        },
    ) {
        // Once created, the currency is fixed and simply stands after the name.
        // The name stands where every page title does, 24 dp in; the controls under it reach 16.
        BareField(
            name, { name = it }, tr("Название", "Name"),
            Modifier.padding(horizontal = Gap.s),
            suffix = editing?.let { Currencies.symbol(it.currency) },
            focusRequester = focus,
        )
        VSpace(Gap.l)
        TypeTiles(type) { type = it }

        if (editing == null) {
            VSpace(Gap.l)
            AccountCurrency(data, currency) { currency = it }
            VSpace(Gap.l)
            Caption(if (debt) tr("Сколько должен сейчас", "How much you owe now") else tr("Сколько сейчас", "Balance now"), Modifier.fillMaxWidth())
            HeroAmountField(balanceText, { balanceText = it }, Currencies.symbol(currency), onDone = ::save)
        }

        if (type == AccountType.SAVINGS || debt) {
            VSpace(Gap.l)
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                TonalField(
                    rateText, { rateText = it }, Modifier.weight(1f),
                    label = if (debt) tr("Ставка", "Rate") else tr("Ставка на остаток", "Rate on balance"),
                    suffix = tr("% годовых", "% a year"),
                    keyboardType = KeyboardType.Decimal,
                )
                if (debt) {
                    TonalField(
                        paymentText, { paymentText = it }, Modifier.weight(1f),
                        label = if (type == AccountType.LOAN) tr("Платёж", "Payment") else tr("Минимальный платёж", "Minimum payment"),
                        suffix = Currencies.symbol(currency),
                        keyboardType = KeyboardType.Decimal,
                    )
                }
            }
            if (debt) {
                VSpace(Gap.s)
                Row(horizontalArrangement = Arrangement.spacedBy(Gap.s), verticalAlignment = Alignment.CenterVertically) {
                    TonalField(
                        paymentDayText, { paymentDayText = it }, Modifier.weight(1f),
                        label = tr("Какого числа", "Day of month"),
                        keyboardType = KeyboardType.Number,
                    )
                    if (type == AccountType.CREDIT) {
                        TonalPick(
                            if (graceUntil == null) tr("Льготный период", "Grace period") else tr("Льгота до", "Interest-free until"),
                            graceUntil?.format(DateTimeFormatter.ofPattern(if (I18n.russian) "d MMMM" else "MMMM d", I18n.locale)),
                            { pickingGrace = true },
                            Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        VSpace(Gap.l)
        // The whole line flips the switch.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(free, role = Role.Switch) { includeInFree = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Named as the period sheet and the account row name it; its words on the sheet's 24 line.
            Text(tr("Карта трат", "Spending card"), Modifier.weight(1f).padding(start = LocalTextInset.current), style = MaterialTheme.typography.bodyLarge)
            CheckSwitch(free) { includeInFree = it }
        }
    }

    if (pickingGrace) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (graceUntil ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingGrace = false },
            confirmButton = {
                DoneButton(onClick = {
                    graceUntil = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingGrace = false
                })
            },
            dismissButton = { TextButton(onClick = { graceUntil = null; pickingGrace = false }) { Text(tr("Убрать", "Clear")) } },
        ) { DatePicker(state) }
    }
    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Удалить «${editing.name}»?", "Delete “${editing.name}”?")) },
            text = { Text(tr("Его операции удалятся. Переводы с другими счетами останутся на них поправкой, их балансы не изменятся", "Its operations go too. Transfers with other accounts stay on them as adjustments, so their balances don't change")) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(editing.id) }) {
                    Text(tr("Удалить", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Отмена", "Cancel")) } },
        )
    }
}

/** The short name a type tile has room for. */
private fun shortTypeLabel(type: AccountType) = when (type) {
    AccountType.CARD -> tr("Карта", "Card")
    AccountType.CASH -> tr("Наличные", "Cash")
    AccountType.SAVINGS -> tr("Вклад", "Savings")
    AccountType.CREDIT -> tr("Кредитка", "Credit card")
    AccountType.LOAN -> tr("Кредит", "Loan")
}

/** Five tiles in a row; the picked one rounds into a full pill in the soft accent, like every pick. */
@Composable
private fun TypeTiles(selected: AccountType, onPick: (AccountType) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
        AccountType.entries.forEach { type ->
            ChoiceTile(typeIcon(type), shortTypeLabel(type), type == selected, Modifier.weight(1f)) { onPick(type) }
        }
    }
}

/** The shown currencies as a connected group, and "Ещё" for any other one. */
@Composable
private fun AccountCurrency(data: AppData, currency: String, onPick: (String) -> Unit) {
    val base = data.currencyChoices().take(4)
    val shown = if (currency in base) base else base.take(3) + currency
    val others = Currencies.common.filter { it !in shown }
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ChoiceGroup(
            options = shown + MORE,
            selected = currency,
            onSelect = { if (it == MORE) menu = true else onPick(it) },
            fill = false,
            minWidth = 56.dp,
        ) { Text(if (it == MORE) tr("Ещё", "More") else Currencies.symbol(it), style = MaterialTheme.typography.titleMedium) }
        Box(Modifier.align(Alignment.CenterEnd)) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                others.forEach { code ->
                    DropdownMenuItem(text = { Text(currencyLabel(code)) }, onClick = { onPick(code); menu = false })
                }
            }
        }
    }
}

/** The choice that opens the rest of the currencies. */
private const val MORE = "more"
