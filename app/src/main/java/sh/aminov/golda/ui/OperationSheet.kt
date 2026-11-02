package sh.aminov.golda.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import sh.aminov.golda.R
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.Bucket
import sh.aminov.golda.data.CategoryKind
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.domain.Budget
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.Draft
import sh.aminov.golda.domain.Buckets
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.VoiceAction
import sh.aminov.golda.domain.VoiceMapper
import sh.aminov.golda.domain.label
import sh.aminov.golda.domain.plural
import sh.aminov.golda.domain.tr
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** What opens the entry sheet: an operation to edit, or nothing for a new one. */
data class EntryRequest(
    val editing: OperationFull? = null,
)

/** Manual entry and editing of expenses, income and transfers. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun EntrySheet(
    data: AppData,
    request: EntryRequest,
    onDismiss: () -> Unit,
    onSave: (Draft) -> Unit,
    onDelete: (OperationFull) -> Unit,
) {
    val editing = request.editing
    val op = editing?.op
    val accounts = data.accounts
    val outPosting = editing?.postings?.firstOrNull { it.amountMinor < 0 } ?: editing?.postings?.firstOrNull()
    val inPosting = editing?.postings?.firstOrNull { it.amountMinor > 0 && it !== outPosting }
    // A new purchase is paid from where voice would pay it: the last account if it is in the purchase's
    // currency (the local one), else a free-money account in that currency,
    // else the last one anyway, and the charge is converted. So changing the local currency moves the
    // default account too, not only the currency.
    val defaultAccount = VoiceMapper.pick(accounts.sortedBy { it.sort }, data.settings.localCurrency, data.settings)

    if (op != null && op.type !in listOf(OpType.EXPENSE, OpType.INCOME, OpType.TRANSFER)) {
        BookkeepingSheet(data, editing, onDismiss, onDelete)
        return
    }

    var type by remember { mutableStateOf(op?.type ?: OpType.EXPENSE) }
    var accountId by remember { mutableStateOf(outPosting?.accountId ?: defaultAccount?.id) }
    var toAccountId by remember { mutableStateOf(inPosting?.accountId ?: accounts.firstOrNull { it.id != accountId }?.id) }
    val account = accountId?.let { data.accountById[it] }
    val toAccount = toAccountId?.let { data.accountById[it] }

    // A new purchase starts in the local currency, as voice does; an edit keeps what was stored.
    // Only an expense has a purchase currency of its own: a voice income in dollars credited to a ruble
    // account keeps the dollars for its row, but the form edits what the account got.
    val purchased = op?.type == OpType.EXPENSE && op.purchaseAmountMinor != null && op.purchaseCurrency != null
    var purchaseCurrency by remember {
        mutableStateOf((if (purchased) op.purchaseCurrency else null) ?: if (op != null) account?.currency ?: "RUB" else data.settings.localCurrency)
    }
    var amountText by remember {
        mutableStateOf(
            when {
                purchased -> Fmt.editable(op.purchaseAmountMinor, op.purchaseCurrency)
                outPosting != null && account != null -> Fmt.editable(kotlin.math.abs(outPosting.amountMinor), account.currency)
                else -> ""
            },
        )
    }
    // Second amount: what the card was charged, or what the other account received.
    var secondText by remember {
        mutableStateOf(
            when {
                purchased && outPosting != null && account != null -> Fmt.editable(-outPosting.amountMinor, account.currency)
                inPosting != null && toAccount != null -> Fmt.editable(inPosting.amountMinor, toAccount.currency)
                else -> ""
            },
        )
    }
    var secondEdited by remember { mutableStateOf(op != null && !op.isEstimate) }
    var editingSecond by remember { mutableStateOf(false) }
    var categoryId by remember { mutableStateOf(op?.categoryId) }
    // An expense's own bucket, set only when it differs from its category's.
    var ownBucket by remember { mutableStateOf(op?.bucket) }
    var note by remember { mutableStateOf(op?.note.orEmpty()) }
    var date by remember { mutableStateOf(op?.let { Ledger.localDate(it.timestamp, data.zone) } ?: data.today) }
    var pickingDate by remember { mutableStateOf(false) }

    val amountCode = if (type == OpType.EXPENSE) purchaseCurrency else account?.currency ?: "RUB"
    val amount = Fmt.parseMinor(amountText, amountCode)?.takeIf { it > 0 }
    // The second amount appears when the money changes currency on its way.
    val secondCode: String? = when (type) {
        OpType.EXPENSE -> account?.currency?.takeIf { it != purchaseCurrency }
        OpType.TRANSFER -> toAccount?.currency?.takeIf { it != account?.currency }
        else -> null
    }
    val estimate: Long? = if (amount == null || secondCode == null || account == null) null else when (type) {
        OpType.EXPENSE -> data.rates.cardCharge(amount, purchaseCurrency, account.currency)
        else -> data.rates.convert(amount, account.currency, secondCode)
    }
    LaunchedEffect(estimate, secondEdited) {
        if (!secondEdited) secondText = estimate?.let { Fmt.editable(it, secondCode!!) } ?: ""
    }
    val second = secondCode?.let { Fmt.parseMinor(secondText, it) }

    val valid = account != null && amount != null &&
        (secondCode == null || (second != null && second > 0)) &&
        (type != OpType.TRANSFER || (toAccount != null && toAccount.id != account.id))

    fun draft(): Draft {
        val acc = account!!
        val timestamp = when {
            op != null && Ledger.localDate(op.timestamp, data.zone) == date -> op.timestamp
            date == LocalDate.now(data.zone) -> System.currentTimeMillis()
            else -> date.atTime(LocalTime.NOON).atZone(data.zone).toInstant().toEpochMilli()
        }
        val foreignPurchase = type == OpType.EXPENSE && secondCode != null
        return Draft(
            id = op?.id ?: 0,
            type = type,
            timestamp = timestamp,
            accountId = acc.id,
            amountMinor = if (foreignPurchase) second!! else amount!!,
            toAccountId = if (type == OpType.TRANSFER) toAccount?.id else null,
            toAmountMinor = if (type == OpType.TRANSFER) second ?: amount else null,
            categoryId = if (type == OpType.TRANSFER) null else categoryId,
            note = note,
            purchaseAmountMinor = if (foreignPurchase) amount else null,
            purchaseCurrency = if (foreignPurchase) purchaseCurrency else null,
            isEstimate = secondCode != null && !secondEdited,
            voiceText = op?.voiceText,
            bucket = if (type == OpType.EXPENSE) ownBucket else null,
        )
    }

    fun save() { if (valid) onSave(draft()) }

    // Where the latest transfer went from and to: the next one is usually the same trip.
    val lastTransfer = remember(data.operations) {
        data.operations.firstOrNull { it.op.type == OpType.TRANSFER }?.let { full ->
            val from = full.postings.firstOrNull { it.amountMinor < 0 }?.accountId
            val to = full.postings.firstOrNull { it.amountMinor > 0 }?.accountId
            from?.let { it to to }
        }
    }

    fun pickAccount(picked: Long) {
        val newAccount = data.accountById.getValue(picked)
        // A purchase in the local currency stays in it on another card (48,5 ₾ charged to the dollar
        // card, SPEC §1.4); one typed in the old account's own currency follows the account.
        val keep = type == OpType.EXPENSE && (purchaseCurrency == data.settings.localCurrency || purchaseCurrency != account?.currency)
        if (!keep) purchaseCurrency = newAccount.currency
        accountId = picked
        if (toAccountId == picked) toAccountId = accounts.firstOrNull { it.id != picked }?.id
        secondEdited = false
    }

    fun pickType(value: OpType) {
        if (op == null && value != type) {
            // A new transfer starts where the last one did; anything else from the usual account.
            if (value == OpType.TRANSFER) {
                accountId = lastTransfer?.first ?: accounts.firstOrNull { it.currency == "RUB" }?.id ?: accountId
                toAccountId = lastTransfer?.second?.takeIf { it != accountId } ?: accounts.firstOrNull { it.id != accountId }?.id
            } else if (type == OpType.TRANSFER) {
                accountId = defaultAccount?.id
            }
        }
        // Back to an expense, a new one is in the currency it started in again (the local one), not in the
        // currency of the account the transfer left from.
        if (value == OpType.EXPENSE && type != OpType.EXPENSE) {
            purchaseCurrency = if (op == null) data.settings.localCurrency
            else accountId?.let { data.accountById[it]?.currency } ?: purchaseCurrency
        }
        type = value
        categoryId = null
        ownBucket = null
        editingSecond = false
        secondEdited = false
        if (value != OpType.EXPENSE) purchaseCurrency = accountId?.let { data.accountById[it]?.currency } ?: purchaseCurrency
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (editing == null) focus.requestFocus() }
    val keyboard = LocalSoftwareKeyboardController.current

    FormPage(
        onClose = onDismiss,
        navigation = { CloseButton(onDismiss) },
        topActions = {
            if (editing != null) DeleteAction { onDelete(editing) }
        },
        actions = {
            PrimaryAction(if (op == null) tr("Записать", "Save") else tr("Сохранить", "Save"), ::save, valid)
        },
    ) {
        // The header: the type.
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            ChoiceGroup(
                options = listOf(OpType.EXPENSE, OpType.INCOME, OpType.TRANSFER),
                selected = type,
                onSelect = ::pickType,
                fill = false,
                height = 40.dp,
                minWidth = 88.dp,
            ) {
                ChoiceText(
                    when (it) {
                        OpType.INCOME -> tr("Доход", "Income")
                        OpType.TRANSFER -> tr("Перевод", "Transfer")
                        else -> tr("Расход", "Expense")
                    },
                )
            }
        }
        VSpace(Gap.l)

        if (type == OpType.TRANSFER) {
            RouteBento(
                data = data,
                from = account,
                to = toAccount,
                onFrom = ::pickAccount,
                onTo = { toAccountId = it; secondEdited = false },
                onSwap = {
                    val a = accountId
                    accountId = toAccountId
                    toAccountId = a
                    // The typed number belongs to the old "from" side: across currencies, what arrived
                    // becomes what is sent, so 100 $ → 9 130 ₽ turns into 9 130 ₽ → 100 $, not 100 ₽.
                    if (secondCode != null && second != null) amountText = secondText
                    secondEdited = false
                },
            )
            VSpace(Gap.l)
        }

        // The amount is the whole point of the sheet: a bare, big, centred number with its currency.
        var currencyMenu by remember { mutableStateOf(false) }
        val currencyChoices = account?.let { data.currencyChoices(it.currency) } ?: data.currencyChoices()
        HeroAmountField(
            text = amountText,
            onText = { amountText = it },
            symbol = Currencies.symbol(amountCode),
            focusRequester = focus,
            maxSp = 80f,
            onDone = { save() },
            onSymbolClick = null,
            symbolMenu = {
                DropdownMenu(expanded = currencyMenu, onDismissRequest = { currencyMenu = false }, properties = KeepKeyboard) {
                    currencyChoices.forEach { code ->
                        DropdownMenuItem(text = { Text(currencyLabel(code)) }, onClick = { purchaseCurrency = code; currencyMenu = false })
                    }
                }
            },
        )
        // Income has no line under the number: nothing it would say helps there.
        if (type != OpType.INCOME) VSpace(Gap.xs)

        // One line under the number. Before there is an amount it says what is safe to spend (a
        // transfer has its balances in the tiles above); after, what the amount comes to.
        when {
            type == OpType.INCOME -> Unit
            amount == null && type == OpType.TRANSFER -> Unit
            amount == null -> {
                UnderLine(tr("Можно сегодня ", "Safe today ") + data.base.approx(data.budget.leftTodayRub))
            }
            secondCode != null && type == OpType.TRANSFER -> ReceivedLine(secondCode, secondText, secondEdited, editingSecond, { editingSecond = it }) {
                secondText = it
                secondEdited = true
            }
            secondCode != null && type == OpType.EXPENSE -> {
                ChargedLine(secondCode, secondText, secondEdited, editingSecond, { editingSecond = it }) {
                    secondText = it
                    secondEdited = true
                }
                // The price in every shown currency too, as for a purchase in the account's own currency:
                // what is charged is one line, what it is worth the other.
                val rub = data.rates.rubMinor(amount, amountCode)
                rub?.let { data.others(it, amountCode) }?.takeIf { it.isNotBlank() }?.let { UnderLine(it) }
            }
            else -> {
                val rub = if (amountCode == "RUB") amount else data.rates.rubMinor(amount, amountCode)
                UnderLine(rub?.let { data.others(it, amountCode) }.orEmpty().ifBlank { Fmt.amount(amount, amountCode) })
            }
        }

        Column {
            when (type) {
                OpType.TRANSFER -> {
                    VSpace(Gap.l)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                        TonalField(note, { note = it }, Modifier.weight(1f), placeholder = tr("Комментарий", "Note"))
                        DatePill(date, data) { pickingDate = true }
                    }
                }
                else -> {
                    if (type == OpType.EXPENSE) {
                        VSpace(Gap.m)
                        CurrencyPicker(currencyChoices, purchaseCurrency, { purchaseCurrency = it; secondEdited = false }, Modifier.align(Alignment.CenterHorizontally))
                    }
                    VSpace(Gap.l)
                    // The placeholder says what the line is for, never the type picked above it.
                    val fallback = categoryId?.let { data.categoryById[it]?.label() } ?: if (type == OpType.INCOME) tr("Откуда", "From where") else tr("Покупка", "Purchase")
                    TonalField(note, { note = it }, Modifier.fillMaxWidth(), placeholder = fallback, onDone = { if (valid) save() })
                    VSpace(Gap.s)
                    Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                        AccountPill(accounts, account, ::pickAccount, Modifier.weight(1f, fill = false))
                        DatePill(date, data) { pickingDate = true }
                    }
                    VSpace(Gap.l)
                    // Always open: every category at once. With the keyboard up the form simply
                    // scrolls under the action bar.
                    CategoryGrid(
                        data = data,
                        kind = if (type == OpType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE,
                        selected = categoryId,
                        onPick = { categoryId = if (categoryId == it) null else it },
                    )
                    if (type == OpType.EXPENSE) {
                        VSpace(Gap.l)
                        val ofCategory = Buckets.of(categoryId?.let { data.categoryById[it] })
                        BucketPicker(ownBucket ?: ofCategory) { picked -> ownBucket = picked.takeIf { it != ofCategory } }
                    }
                }
            }
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text(tr("Готово", "Done")) }
            },
        ) { DatePicker(state) }
    }
}

/** Opening balances and reconciliation adjustments: nothing to edit, only to remove. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookkeepingSheet(data: AppData, full: OperationFull, onDismiss: () -> Unit, onDelete: (OperationFull) -> Unit) {
    val posting = full.postings.first()
    val code = data.accountById[posting.accountId]?.currency ?: "RUB"
    FormSheet(
        onDismiss = onDismiss,
        actions = {
            FilledTonalButton(
                onClick = { onDelete(full) },
                modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) { Text(tr("Удалить", "Delete"), style = MaterialTheme.typography.titleMedium) }
        },
    ) {
        Caption(if (full.op.type == OpType.ADJUSTMENT) tr("Корректировка после сверки", "Reconciliation adjustment") else tr("Начальный баланс", "Opening balance"))
        VSpace(Gap.xs)
        val text = Fmt.amount(posting.amountMinor, code, signed = true)
        BigNumber(text, posting.amountMinor, MaterialTheme.colorScheme.onSurface, maxSp = 64f)
        VSpace(Gap.xs)
        Text(
            data.accountById[posting.accountId]?.name.orEmpty() + " · " + dayLabel(Ledger.localDate(full.op.timestamp, data.zone), data.today),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "≈ 5,87 $ со счёта ✎": what the card is charged; a tap opens it for the bank's real figure. */
@Composable
private fun ChargedLine(code: String, text: String, edited: Boolean, open: Boolean, onOpen: (Boolean) -> Unit, onText: (String) -> Unit) {
    SecondAmount(code, text, open, onOpen, onText) {
        val amount = Fmt.parseMinor(text, code)?.let { Fmt.amount(it, code) } ?: "—"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (edited) "" else "≈ ") + amount + tr(" со счёта", " from the account"),
                style = MaterialTheme.typography.bodyLarge.merge(Tnum),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Gap.s))
            Icon(painterResource(R.drawable.ic_edit), tr("Поправить", "Correct"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "→ ≈ 109,21 $": what the other account receives, big; a tap corrects it. */
@Composable
private fun ReceivedLine(code: String, text: String, edited: Boolean, open: Boolean, onOpen: (Boolean) -> Unit, onText: (String) -> Unit) {
    SecondAmount(code, text, open, onOpen, onText) {
        val amount = Fmt.parseMinor(text, code)?.let { Fmt.amount(it, code) } ?: "—"
        Text(
            "→ " + (if (edited) "" else "≈ ") + amount,
            style = MaterialTheme.typography.headlineMedium.merge(Tnum),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SecondAmount(code: String, text: String, open: Boolean, onOpen: (Boolean) -> Unit, onText: (String) -> Unit, closed: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (open) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            TonalField(
                value = text,
                onValueChange = onText,
                modifier = Modifier.width(220.dp),
                suffix = Currencies.symbol(code),
                keyboardType = KeyboardType.Decimal,
                onDone = { onOpen(false) },
                textStyle = MaterialTheme.typography.titleLarge.merge(Tnum),
                focusRequester = focus,
            )
        } else {
            Box(Modifier.clip(MaterialTheme.shapes.large).clickable { onOpen(true) }.padding(horizontal = Gap.s, vertical = Gap.xs)) { closed() }
        }
    }
}

/** The currency it cost in: a connected row of symbols, centred. */
@Composable
fun CurrencyPicker(choices: List<String>, selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (choices.size <= 5) {
        ChoiceGroup(choices, selected, onPick, modifier, fill = false, height = 48.dp, minWidth = 56.dp) {
            Text(Currencies.symbol(it), style = MaterialTheme.typography.titleMedium)
        }
    } else {
        ToggleFlow(choices, { it == selected }, onPick, { Currencies.symbol(it) }, modifier)
    }
}

/** "Мультивалютная USD · $ ▾": the account, rarely changed, as a pill with a menu. */
@Composable
private fun AccountPill(accounts: List<Account>, account: Account?, onPick: (Long) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        MetaPill(
            text = account?.let { "${it.name} · ${Currencies.symbol(it.currency)}" } ?: tr("Счёт", "Account"),
            onClick = { open = true },
            painter = painterResource(account?.let { typeIcon(it.type) } ?: R.drawable.ic_card),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, properties = KeepKeyboard) {
            accounts.forEach { a ->
                DropdownMenuItem(text = { Text("${a.name} · ${Currencies.symbol(a.currency)}") }, onClick = { onPick(a.id); open = false })
            }
        }
    }
}

@Composable
private fun DatePill(date: LocalDate, data: AppData, onClick: () -> Unit) {
    MetaPill(dayLabel(date, data.today), onClick, painter = painterResource(R.drawable.ic_calendar))
}

/** From and to as two tiles with a round swap button over the gap; a tile opens the list of accounts. */
@Composable
private fun RouteBento(data: AppData, from: Account?, to: Account?, onFrom: (Long) -> Unit, onTo: (Long) -> Unit, onSwap: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Bento {
            // The sides facing the swap button keep 32 dp clear, so it never sits on a name.
            RouteTile(data, tr("Откуда", "From"), from, data.accounts, onFrom, Modifier.weight(1f), PaddingValues(start = Gap.m, top = Gap.m, bottom = Gap.m, end = 32.dp))
            RouteTile(data, tr("Куда", "To"), to, data.accounts.filter { it.id != from?.id }, onTo, Modifier.weight(1f), PaddingValues(start = 32.dp, top = Gap.m, bottom = Gap.m, end = Gap.m))
        }
        // A ring of the sheet's colour cuts the button out of the tiles.
        Box(Modifier.size(48.dp).background(sheetColor(), CircleShape), contentAlignment = Alignment.Center) {
            FilledTonalIconButton(
                onClick = onSwap,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) { Icon(painterResource(R.drawable.ic_swap), tr("Поменять местами", "Swap"), Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun RouteTile(data: AppData, label: String, account: Account?, choices: List<Account>, onPick: (Long) -> Unit, modifier: Modifier, padding: PaddingValues) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Tile(Modifier.fillMaxWidth().height(96.dp), onClick = { open = true }, padding = padding) {
            Caption(label)
            Spacer(Modifier.weight(1f))
            Text(account?.name ?: "—", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            account?.let { a ->
                Text(
                    Fmt.amount(data.states[a.id]?.balanceMinor ?: 0, a.currency),
                    style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, properties = KeepKeyboard) {
            choices.forEach { a ->
                DropdownMenuItem(
                    text = { Text("${a.name} · ${Fmt.amount(data.states[a.id]?.balanceMinor ?: 0, a.currency)}") },
                    onClick = { onPick(a.id); open = false },
                )
            }
        }
    }
}

/** Every category as a grid of tiles, four across, the most used first. The picked one grows into a pill. */
@Composable
private fun CategoryGrid(data: AppData, kind: CategoryKind, selected: Long?, onPick: (Long) -> Unit) {
    val ordered = remember(data.categories, data.operations, kind) {
        val uses = data.operations.groupingBy { it.op.categoryId }.eachCount()
        data.categories.filter { it.kind == kind }.sortedWith(compareByDescending<Category> { uses[it.id] ?: 0 }.thenBy { it.sort })
    }
    Column(verticalArrangement = Arrangement.spacedBy(Gap.s)) {
        ordered.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                row.forEach { category ->
                    CategoryTile(categoryIcon(category.key), category.label(), category.id == selected, Modifier.weight(1f)) { onPick(category.id) }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryTile(icon: Int, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val corner by animateDpAsState(if (on) 36.dp else 20.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "corner")
    val container by animateColorAsState(if (on) pickedColor() else unpickedColor(), MaterialTheme.motionScheme.fastEffectsSpec(), label = "tile")
    val ink = if (on) onPickedColor() else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, modifier = modifier.height(72.dp), shape = RoundedCornerShape(corner), color = container, contentColor = ink) {
        Column(Modifier.padding(horizontal = Gap.xs), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(painterResource(icon), null, Modifier.size(24.dp), tint = if (on) ink else MaterialTheme.colorScheme.onSurfaceVariant)
            VSpace(Gap.xs)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}


/**
 * Which bucket an expense is in: "Каждый день" counts against the day's limit, "Регулярно" and
 * "Крупное" do not. It starts at the category's; picking another sets this expense's own.
 */
@Composable
private fun BucketPicker(selected: Bucket, onPick: (Bucket) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ChoiceGroup(
            options = listOf(Bucket.DAILY, Bucket.REGULAR, Bucket.BIG),
            selected = selected,
            onSelect = onPick,
            fill = false,
            height = 40.dp,
            minWidth = 88.dp,
        ) { ChoiceText(bucketLabel(it)) }
    }
}
