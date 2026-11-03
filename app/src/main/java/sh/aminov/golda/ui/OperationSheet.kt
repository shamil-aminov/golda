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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import sh.aminov.golda.data.AccountType
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.FlowRow
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
import sh.aminov.golda.domain.OpTags
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
    /** The category a note most likely means: the history, else Gemini; null when neither knows. */
    suggest: suspend (String, CategoryKind) -> Long? = { _, _ -> null },
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
    val spentFrom = remember(data.operations) { VoiceMapper.spentFrom(data.operations) }
    val defaultAccount = VoiceMapper.pick(accounts.sortedBy { it.sort }, data.settings.localCurrency, data.settings, spentFrom)

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
    val purchased = (op?.type == OpType.EXPENSE || op?.type == OpType.INCOME) && op.purchaseAmountMinor != null && op.purchaseCurrency != null
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
                purchased && outPosting != null && account != null -> Fmt.editable(kotlin.math.abs(outPosting.amountMinor), account.currency)
                inPosting != null && toAccount != null -> Fmt.editable(inPosting.amountMinor, toAccount.currency)
                else -> ""
            },
        )
    }
    var secondEdited by remember { mutableStateOf(op != null && !op.isEstimate) }
    var editingSecond by remember { mutableStateOf(false) }
    var categoryId by remember { mutableStateOf(op?.categoryId) }
    // A new entry's category and account are worked out by the app until one is picked by hand: then
    // that pill keeps what was picked. An edit keeps what was stored.
    var categoryAuto by remember { mutableStateOf(op == null) }
    var accountAuto by remember { mutableStateOf(op == null) }
    var thinking by remember { mutableStateOf(false) }
    // The bucket comes first: everyday, regular or big; its four categories follow. A new entry's bucket
    // follows the category the app picks until one is tapped.
    var bucket by remember { mutableStateOf(op?.let { Buckets.of(it, data.categoryById) } ?: Bucket.DAILY) }
    var bucketAuto by remember { mutableStateOf(op == null) }
    var note by remember { mutableStateOf(op?.note.orEmpty()) }
    var date by remember { mutableStateOf(op?.let { Ledger.localDate(it.timestamp, data.zone) } ?: data.today) }
    var pickingDate by remember { mutableStateOf(false) }

    // An expense and an income are typed in a currency of their own (the account's or another);
    // a transfer in its source account's.
    val priced = type == OpType.EXPENSE || type == OpType.INCOME
    val amountCode = if (priced) purchaseCurrency else account?.currency ?: "RUB"
    val amount = Fmt.parseMinor(amountText, amountCode)?.takeIf { it > 0 }
    // The second amount appears when the money changes currency on its way.
    val secondCode: String? = when (type) {
        OpType.EXPENSE, OpType.INCOME -> account?.currency?.takeIf { it != purchaseCurrency }
        OpType.TRANSFER -> toAccount?.currency?.takeIf { it != account?.currency }
        else -> null
    }
    val estimate: Long? = if (amount == null || secondCode == null || account == null) null else when (type) {
        OpType.EXPENSE -> data.rates.cardCharge(amount, purchaseCurrency, account.currency)
        // Money coming in at the display rate, as voice credits it.
        OpType.INCOME -> data.rates.convert(amount, purchaseCurrency, account.currency)
        else -> data.rates.convert(amount, account.currency, secondCode)
    }
    LaunchedEffect(estimate, secondEdited) {
        if (!secondEdited) secondText = estimate?.let { Fmt.editable(it, secondCode!!) } ?: ""
    }
    // Shown and saved straight from the estimate until it is corrected by hand: the state above
    // catches up a frame later, and reading it would flash "—" on every change of currency or card.
    val secondShown = if (secondEdited) secondText else estimate?.let { Fmt.editable(it, secondCode!!) } ?: ""
    val second = secondCode?.let { Fmt.parseMinor(secondShown, it) }

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
        val foreignPurchase = priced && secondCode != null
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
            // Stored only when it is not the category's own: a bucket picked with no category, say.
            bucket = if (type == OpType.EXPENSE) bucket.takeIf { it != Buckets.of(categoryId?.let { data.categoryById[it] }) } else null,
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
        accountAuto = false
        val newAccount = data.accountById.getValue(picked)
        // A purchase in the local currency stays in it on another card (48,5 ₾ charged to the dollar
        // card, SPEC §1.4); one typed in the old account's own currency follows the account.
        val keep = priced && (purchaseCurrency == data.settings.localCurrency || purchaseCurrency != account?.currency)
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
        if ((value == OpType.EXPENSE || value == OpType.INCOME) && type == OpType.TRANSFER) {
            purchaseCurrency = if (op == null) data.settings.localCurrency
            else accountId?.let { data.accountById[it]?.currency } ?: purchaseCurrency
        }
        type = value
        categoryId = null
        categoryAuto = op == null
        editingSecond = false
        secondEdited = false
        if (value == OpType.TRANSFER) purchaseCurrency = accountId?.let { data.accountById[it]?.currency } ?: purchaseCurrency
    }

    // The card follows the currency while it is not picked: lari from the lari card, rubles from the
    // ruble one, dollars with no dollar card from the usual one, converted (as voice does).
    LaunchedEffect(purchaseCurrency, type) {
        if (accountAuto && type != OpType.TRANSFER) {
            VoiceMapper.pick(accounts.sortedBy { it.sort }, purchaseCurrency, data.settings, spentFrom)?.let { accountId = it.id; secondEdited = false }
        }
    }
    // The category follows what is typed while it is not picked: a moment after typing stops.
    LaunchedEffect(note, type) {
        if (!categoryAuto || type == OpType.TRANSFER) return@LaunchedEffect
        if (note.isBlank()) { categoryId = null; return@LaunchedEffect }
        delay(600)
        thinking = true
        val found = suggest(note, if (type == OpType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE)
        thinking = false
        if (categoryAuto) {
            categoryId = found
            // An auto bucket follows the category the app found ("Netflix" → regular).
            if (bucketAuto && type == OpType.EXPENSE) found?.let { data.categoryById[it] }?.let { bucket = Buckets.of(it) }
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (editing == null) focus.requestFocus() }
    val keyboard = LocalSoftwareKeyboardController.current

    FormPage(
        onClose = onDismiss,
        topBar = false,
        // Back first, by the save button, as on every page; not in a bar across the top.
        actions = {
            RoundAction(R.drawable.ic_back, tr("Назад", "Back"), onDismiss)
            if (editing != null) RoundAction(R.drawable.ic_delete, tr("Удалить", "Delete")) { onDelete(editing) }
            PrimaryAction(if (op == null) tr("Записать", "Save") else tr("Сохранить", "Save"), ::save, valid)
        },
    ) {
        // The header: the type.
        ChoiceGroup(
            options = listOf(OpType.EXPENSE, OpType.INCOME, OpType.TRANSFER),
            selected = type,
            onSelect = ::pickType,
            modifier = Modifier.fillMaxWidth(),
            height = 40.dp,
        ) {
            ChoiceText(
                when (it) {
                    OpType.INCOME -> tr("Доход", "Income")
                    OpType.TRANSFER -> tr("Перевод", "Transfer")
                    else -> tr("Расход", "Expense")
                },
            )
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
                    if (secondCode != null && second != null) amountText = secondShown
                    secondEdited = false
                },
            )
            VSpace(Gap.l)
        }

        // The amount is the whole point of the sheet: a bare, big, centred number with its currency.
        val currencyChoices = account?.let { data.currencyChoices(it.currency) } ?: data.currencyChoices()
        HeroAmountField(
            text = amountText,
            onText = { amountText = it },
            symbol = Currencies.symbol(amountCode),
            focusRequester = focus,
            maxSp = 80f,
            onDone = { save() },
            onSymbolClick = null,
        )
        VSpace(Gap.xs)

        // One line under the number. Before there is an amount it says what is safe to spend (a
        // transfer has its balances in the tiles above); after, what the amount comes to.
        when {
            amount == null && type == OpType.INCOME -> UnderLine(" ")
            amount == null && type == OpType.TRANSFER -> Unit
            amount == null -> {
                data.envelope?.let { UnderLine(tr("Можно сегодня ", "Safe today ") + Fmt.amount(it.leftTodayMinor, it.currency)) }
            }
            secondCode != null && type == OpType.TRANSFER -> ReceivedLine(secondCode, secondShown, secondEdited, editingSecond, { editingSecond = it }) {
                secondText = it
                secondEdited = true
            }
            secondCode != null && priced -> {
                // One line, as for a purchase in the account's own currency: what the card is charged
                // (✎ to put the bank's figure), then what it is in the other shown currencies. The
                // card's currency is the charge, so it is not said twice.
                val rest = data.rates.rubMinor(amount, amountCode)?.let { data.others(it, amountCode, secondCode) }?.takeIf { it.isNotBlank() }
                ChargedLine(secondCode, secondShown, secondEdited, editingSecond, rest, { editingSecond = it }) {
                    secondText = it
                    secondEdited = true
                }
            }
            else -> {
                val rub = if (amountCode == "RUB") amount else data.rates.rubMinor(amount, amountCode)
                UnderLine(rub?.let { data.others(it, amountCode) }.orEmpty().ifBlank { Fmt.amount(amount, amountCode) })
            }
        }

        Column {
            when (type) {
                OpType.TRANSFER -> {
                    // The same note and the same dates as an expense's: one way to write either.
                    VSpace(Gap.l)
                    TonalField(note, { note = it }, Modifier.fillMaxWidth().heightIn(min = 56.dp), placeholder = tr("Комментарий", "Note"))
                    VSpace(Gap.l)
                    DateChoices(date, data.today) { picked -> if (picked == null) pickingDate = true else date = picked }
                }
                else -> {
                    // Everything on the first level: currency, card and date are buttons, not menus.
                    if (priced) {
                        VSpace(Gap.s)
                        CurrencyPicker(currencyChoices, purchaseCurrency, { purchaseCurrency = it; secondEdited = false }, Modifier.align(Alignment.CenterHorizontally))
                    }
                    VSpace(Gap.l)
                    TonalField(
                        note, { note = it }, Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        placeholder = if (type == OpType.INCOME) tr("Откуда", "From where") else tr("Что купил", "What it was"),
                        onDone = { if (valid) save() },
                    )
                    VSpace(Gap.l)
                    // The bucket over its four categories: picking it changes them.
                    if (type == OpType.EXPENSE) {
                        ChoiceGroup(listOf(Bucket.DAILY, Bucket.REGULAR, Bucket.BIG), bucket, { picked ->
                            bucketAuto = false
                            bucket = picked
                            // A category of another bucket does not belong any more.
                            if (categoryId?.let { data.categoryById[it] }?.let { Buckets.of(it) } != picked) { categoryId = null }
                        }, Modifier.fillMaxWidth(), height = 36.dp) { ChoiceText(bucketLabel(it)) }
                        VSpace(Gap.m)
                    }
                    // The four of the bucket (or of income). While the app picks, the tile is outlined.
                    CategoryGrid(
                        data = data,
                        kind = if (type == OpType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE,
                        bucket = if (type == OpType.EXPENSE) bucket else null,
                        selected = categoryId,
                        auto = categoryAuto,
                        onPick = { categoryId = if (categoryId == it && !categoryAuto) null else it; categoryAuto = false },
                    )
                    VSpace(Gap.m)
                    AccountCards(accounts.filter { it.type != AccountType.LOAN }, accountId, ::pickAccount)
                    VSpace(Gap.m)
                    DateChoices(date, data.today) { picked -> if (picked == null) pickingDate = true else date = picked }
                    // Gemini's precise tag, under all that was chosen, while its note is unchanged.
                    op?.tag?.takeIf { OpTags.key(op.note) == OpTags.key(note) }?.let { tag ->
                        VSpace(Gap.m)
                        UnderLine(tr("Метка Gemini: ", "Gemini tag: ") + tag)
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
private fun ChargedLine(code: String, text: String, edited: Boolean, open: Boolean, rest: String?, onOpen: (Boolean) -> Unit, onText: (String) -> Unit) {
    SecondAmount(code, text, open, onOpen, onText) {
        val amount = Fmt.parseMinor(text, code)?.let { Fmt.amount(it, code) } ?: "—"
        val style = MaterialTheme.typography.bodyLarge.merge(Tnum)
        val ink = MaterialTheme.colorScheme.onSurfaceVariant
        // The other currencies first, the charge last with ✎ at the very end: the one thing to tap is
        // always at the edge. "≈" says it is the app's estimate; no words needed.
        Row(verticalAlignment = Alignment.CenterVertically) {
            rest?.let { Text("$it · ", Modifier.weight(1f, fill = false), style = style, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text((if (edited) "" else "≈ ") + amount, style = style, color = ink, maxLines = 1)
            Icon(painterResource(R.drawable.ic_edit), tr("Поправить", "Correct"), Modifier.padding(start = Gap.xs).size(14.dp), tint = ink)
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
            // No vertical padding: the line takes the same height as the plain one it replaces, so
            // switching currency moves nothing.
            Box(Modifier.clip(MaterialTheme.shapes.large).clickable { onOpen(true) }.padding(horizontal = Gap.s)) { closed() }
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
    // A tap on the tile while the list is open first dismisses the list (a tap outside it), and the
    // click would open it again at once: a tap right after a dismissal only closes.
    var dismissedAt by remember { mutableLongStateOf(0L) }
    Box(modifier) {
        Tile(Modifier.fillMaxWidth().height(96.dp), container = tileColor(), onClick = { if (System.currentTimeMillis() - dismissedAt > 300) open = !open }, padding = padding) {
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
        DropdownMenu(expanded = open, onDismissRequest = { open = false; dismissedAt = System.currentTimeMillis() }, properties = KeepKeyboard) {
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
private fun CategoryGrid(data: AppData, kind: CategoryKind, bucket: Bucket?, selected: Long?, auto: Boolean = false, onPick: (Long) -> Unit) {
    // In their own order, so a tile never moves under the finger.
    val ordered = remember(data.categories, kind, bucket) {
        data.categories.filter { it.kind == kind && (bucket == null || Buckets.of(it) == bucket) }.sortedBy { it.sort }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Gap.s)) {
        ordered.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                row.forEach { category ->
                    CategoryTile(categoryIcon(category.key), category.label(), category.id == selected, auto, Modifier.weight(1f)) { onPick(category.id) }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryTile(icon: Int, label: String, on: Boolean, auto: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // Picked by the app, not by you: the tile is outlined, not filled.
    val filled = on && !auto
    val corner by animateDpAsState(if (on) 24.dp else 16.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "corner")
    val container by animateColorAsState(if (filled) pickedColor() else unpickedColor(), MaterialTheme.motionScheme.fastEffectsSpec(), label = "tile")
    val ink = if (filled) onPickedColor() else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = ink,
        border = if (on && auto) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(horizontal = Gap.xs), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(painterResource(icon), null, Modifier.size(24.dp), tint = if (filled) ink else MaterialTheme.colorScheme.onSurfaceVariant)
            VSpace(Gap.xs)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

/** The date as three short buttons across: today, yesterday, or another (the calendar; null asks for it). */
@Composable
private fun DateChoices(date: LocalDate, today: LocalDate, onPick: (LocalDate?) -> Unit) {
    val yesterday = today.minusDays(1)
    // The third holds any other day: the day itself once picked, the calendar before.
    val other = date.takeIf { it != today && it != yesterday }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
        listOf<LocalDate?>(today, yesterday, other).forEach { day ->
            val on = day == date
            val spec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
            val container by animateColorAsState(if (on) pickedColor() else unpickedColor(), spec, label = "day")
            val ink by animateColorAsState(if (on) onPickedColor() else MaterialTheme.colorScheme.onSurface, spec, label = "day-ink")
            Surface(
                onClick = { onPick(if (day == null || day == other) null else day) },
                modifier = Modifier.weight(1f).height(36.dp),
                shape = CircleShape,
                color = container,
                contentColor = ink,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(day?.let { dayLabel(it, today) } ?: tr("Другой день", "Another day"), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The accounts as cards in one row that scrolls sideways: the name, and the currency under it. They
 * are what the money leaves from, so they look like the cards in a wallet, not like the other buttons.
 */
@Composable
private fun AccountCards(accounts: List<Account>, selected: Long?, onPick: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Gap.s),
    ) {
        accounts.forEach { account ->
            val on = account.id == selected
            // The same easing as every other choice on the form: no hard switch between cards.
            val spec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
            val container by animateColorAsState(if (on) pickedColor() else MaterialTheme.colorScheme.surfaceContainerLowest, spec, label = "card")
            val ink by animateColorAsState(if (on) onPickedColor() else MaterialTheme.colorScheme.onSurface, spec, label = "card-ink")
            val quiet by animateColorAsState(if (on) onPickedColor() else MaterialTheme.colorScheme.onSurfaceVariant, spec, label = "card-quiet")
            Surface(
                onClick = { onPick(account.id) },
                modifier = Modifier.width(116.dp).height(72.dp),
                shape = RoundedCornerShape(12.dp),
                color = container,
                contentColor = ink,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(account.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        Currencies.symbol(account.currency),
                        style = MaterialTheme.typography.labelLarge,
                        color = quiet,
                    )
                }
            }
        }
    }
}
