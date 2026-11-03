package sh.aminov.golda.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.navigationBarsPadding
import sh.aminov.golda.domain.Regulars
import sh.aminov.golda.data.Obligation
import sh.aminov.golda.R
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.domain.AccountState
import sh.aminov.golda.domain.Budget
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.Debts
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.plural
import sh.aminov.golda.domain.tr
import java.time.LocalDate

/** Keeps the last row clear of the floating toolbar (64 dp, 16 dp above the gesture bar) by 24 dp. */
fun PaddingValues.aboveActions() = PaddingValues(top = calculateTopPadding() + Gap.s, bottom = calculateBottomPadding() + 88.dp + Gap.l)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    data: AppData,
    padding: PaddingValues,
    listState: LazyListState,
    onSettings: () -> Unit,
    onPeriod: () -> Unit,
    onEdit: (OperationFull) -> Unit,
) {
    val today = data.today
    val envelope = data.envelope
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding.aboveActions()) {
        item(key = "hero") {
            // One vivid tile holds the day: what may go today, what is on the card until when, and
            // whether it lasts at the pace it actually goes. Spent past today's share, the whole tile
            // turns to the error container.
            // Spent past today's share, or nothing left on the card at all.
            val over = envelope != null && (envelope.leftTodayMinor < 0 || envelope.balanceMinor <= 0)
            val scheme = MaterialTheme.colorScheme
            val ink = scheme.onSurface
            val quiet = scheme.onSurfaceVariant
            // Two kinds of type on the tile and no more: the big number, and one style in one colour for
            // every word around it.
            // Golden-ratio steps: 14 for the words round the figure, 22 for the main line.
            val text = MaterialTheme.typography.bodyMedium.merge(Tnum)
            HeroTile(Modifier.padding(horizontal = Gap.s), content = ink) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Можно сегодня", "Safe to spend today"), Modifier.weight(1f), style = text, color = quiet, maxLines = 1)
                    IconButton(onClick = onSettings, modifier = Modifier.overhang()) {
                        Icon(painterResource(R.drawable.ic_settings), tr("Настройки", "Settings"), tint = quiet)
                    }
                }
                // No account to spend from (or the data is still loading): the tile stays quiet.
                if (envelope == null) return@HeroTile
                // The card's currency. Spacing: 8 between a caption and what it names, 16 between blocks,
                // 4 between the two lines that say one thing.
                val code = envelope.currency
                val left = envelope.leftTodayMinor
                VSpace(Gap.s)
                HeroAmount(whole(left, code), ink, Modifier.testTag(Tags.HOME_BIG))
                // Measured on the screen, every gap in the tile is the same 28 dp of air: above the
                // caption, caption to figures, figures to bar, bar to text, under the text. The figures'
                // line keeps its empty descent under the digits, so the spacer here is that much shorter.
                VSpace(Gap.m - HeroDescent)
                // What is still left of today's share; flat and full once it is spent.
                val share = when {
                    over -> 1f
                    envelope.perDayMinor > 0 -> (left.toFloat() / envelope.perDayMinor).coerceIn(0f, 1f)
                    else -> 0f
                }
                // The one colour on the screen: how it goes. Green while the money lasts the period,
                // ochre when it runs out sooner, muted red once today is overspent.
                val pace = when {
                    over -> Pace.OVER
                    envelope.lasts(today) -> Pace.LASTS
                    else -> Pace.SHORT
                }
                PaceCapsule(share, paceColor(pace))
                VSpace(Gap.m)
                // Looking ahead from today: the days still to get through, and the days the money lasts
                // at the pace it really goes. Worked out again every day. A tap changes the period.
                Column(Modifier.fillMaxWidth().clickable(onClick = onPeriod).testTag(Tags.HOME_PERIOD)) {
                    val ahead = envelope.daysLeft
                    fun days(n: Long) = plural(n, "день", "дня", "дней", "day", "days")
                    // "из 13 дней", "из 21 дня": the genitive after "из".
                    val ofDays = plural(ahead, "дня", "дней", "дней", "day", "days")
                    val lasts = envelope.runwayDays
                    Text(
                        when {
                            envelope.balanceMinor <= 0 -> tr("Денег нет, впереди $ahead ${days(ahead)}", "No money left, $ahead ${days(ahead)} to go")
                            left < 0 -> tr("Сегодня сверх плана на ${whole(-left, code)}", "${whole(-left, code)} over today's plan")
                            ahead <= 1L -> tr("Последний день", "The last day")
                            lasts == null -> tr("Впереди $ahead ${days(ahead)}", "$ahead ${days(ahead)} to go")
                            envelope.lasts(today) -> (lasts - ahead).let { spare ->
                                // A week or more to spare is worth saying; a day or two is noise.
                                tr("Хватит на все $ahead ${days(ahead)}", "Lasts all $ahead ${days(ahead)}") + if (spare >= 7) ", +$spare" else ""
                            }
                            else -> tr("Хватит на $lasts из $ahead $ofDays", "Lasts $lasts of $ahead $ofDays")
                        },
                        Modifier.testTag(Tags.HOME_RUNWAY),
                        // The tile's main statement: the one semibold line, in full ink, a size up.
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = ink,
                        maxLines = 1,
                    )
                    VSpace(Gap.xs)
                    // What a day was meant to be and what it actually is, in the same words and ink.
                    val plan = tr("План ${whole(envelope.perDayMinor, code)} в день", "Plan ${whole(envelope.perDayMinor, code)} a day")
                    val fact = envelope.burnMinor?.let { tr("факт ${whole(it, code)}", "actual ${whole(it, code)}") }
                    Text(listOfNotNull(plan, fact).joinToString(" · "), style = text, color = quiet, maxLines = 1)
                    // The last line's box ends tighter than the caption's begins: 3 dp evens the two.
                    VSpace(3.dp)
                }
                data.states.values.mapNotNull { graceWarning(data, it, today) }.forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = ink, modifier = Modifier.padding(top = Gap.xs))
                }
            }
        }
        // A regular payment due in the next two days, so it is no surprise on the card.
        val due = Regulars.upcoming(data.obligations, today)
        if (due.isNotEmpty()) {
            item(key = "upcoming") {
                Column(Modifier.padding(start = Gap.m, end = Gap.m, top = Gap.m)) {
                    due.forEachIndexed { i, (payment, days) ->
                        GroupRow(
                            i, due.size,
                            supporting = { Text(Regulars.whenLabel(days)) },
                            trailing = { AmountColumn(if (payment.amountMinor == 0L) tr("пробный", "trial") else Fmt.amount(payment.amountMinor, payment.currency)) },
                        ) { Text(payment.name, style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
        if (data.visibleOperations.isEmpty()) {
            item(key = "empty") {
                Text(
                    tr("Пока пусто. Нажми микрофон и назови трату: «шаурма 15 лари». Или «+», чтобы ввести руками.", "Nothing yet. Tap the mic and name a purchase, like “shawarma 15 lari”. Or “+” to type it."),
                    modifier = Modifier.padding(Gap.l),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        operationItems(data, data.visibleOperations, onClick = onEdit)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountsScreen(
    data: AppData,
    padding: PaddingValues,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    /** The account, the balance the bank shows, and whether a shortfall is unrecorded everyday spending. */
    onReconcile: (Long, Long, Boolean) -> Unit,
    reconcileMode: Boolean = false,
    onAllReconciled: () -> Unit = {},
    onSavePayment: (Obligation) -> Unit = {},
    onDeletePayment: (Long) -> Unit = {},
) {
    val total = data.states.values.sumOf { it.rubMinor }
    var payment by remember { mutableStateOf<Obligation?>(null) }
    val suggestions = remember(data) { Regulars.suggest(data.operations, data.accountById, data.categoryById, data.obligations, data.today, data.zone) }
    var reconciling by remember { mutableStateOf<AccountState?>(null) }
    // In reconcile mode, the accounts already checked this round: true when the balance matched.
    var checked by remember(reconcileMode) { mutableStateOf(mapOf<Long, Boolean>()) }
    fun done(state: AccountState, actual: Long, asSpending: Boolean = false) {
        onReconcile(state.account.id, actual, asSpending)
        if (reconcileMode) {
            checked = checked + (state.account.id to (actual == state.balanceMinor))
            if (checked.keys.containsAll(data.accounts.map { it.id })) onAllReconciled()
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.aboveActions()) {
        item(key = "total") {
            // The total is the hero here, as big as Home's number.
            val scheme = MaterialTheme.colorScheme
            // Like Home's tile: the big number, and one style in one colour for every word around it.
            // Golden-ratio steps: 14 for the words round the figure, 22 for the main line.
            val text = MaterialTheme.typography.bodyMedium.merge(Tnum)
            val quiet = scheme.onPrimaryFixedVariant
            HeroTile(Modifier.padding(horizontal = Gap.s)) {
                Text(tr("Всего на счетах", "Total in accounts"), style = text, color = quiet, maxLines = 1)
                VSpace(Gap.s)
                HeroAmount(data.base.whole(total), scheme.onPrimaryFixed, Modifier.testTag(Tags.ACCOUNTS_TOTAL))
                VSpace(Gap.s)
                Text(data.others(total, data.base.code), style = text, color = quiet)
                // What the debt costs: the label quiet, the money it eats semibold, like Home's main line.
                Debts.advice(data.accounts, data.states)?.let { advice ->
                    VSpace(Gap.m)
                    val (head, tail) = advice.split("\n", limit = 2).let { it.first() to it.getOrNull(1) }
                    Text(head, style = text, color = quiet)
                    tail?.let { Text(it, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = scheme.onSurface) }
                }
            }
        }
        if (reconcileMode) {
            item(key = "reconcile-hint") {
                GroupLabel(
                    tr("Сверка", "Reconciling"),
                    Modifier.padding(horizontal = Gap.m).padding(top = Gap.l),
                    supporting = tr("Совпало с банком: «Сходится». Если нет, нажми на счёт.", "Matches the bank: “Matches”. Doesn’t: tap the account."),
                )
            }
        }
        // One list: one bank, one account, so there is nothing to group.
        val count = data.accounts.size + 1
        // A label over the accounts too, like the dates on Home and the payments below.
        item(key = "gap") { if (!reconcileMode) GroupLabel(tr("Счета", "Accounts"), Modifier.padding(horizontal = Gap.m).padding(top = Gap.l)) }
        data.accounts.forEachIndexed { i, account ->
            item(key = account.id) {
                val state = data.states.getValue(account.id)
                AccountRow(
                    data, state, i, count,
                    reconcileMode = reconcileMode,
                    checked = checked[account.id],
                    onClick = { if (reconcileMode) reconciling = state else onOpen(account.id) },
                    onMatch = { done(state, state.balanceMinor) },
                )
            }
        }
        item(key = "add") { AddRow(count - 1, count, tr("Счёт", "Account"), onAdd) }
        if (!reconcileMode) regularPayments(data, suggestions) { payment = it }
    }
    payment?.let { shown ->
        PaymentSheet(
            data, shown,
            onDismiss = { payment = null },
            onSave = { saved -> onSavePayment(saved); payment = null },
            onDelete = { onDeletePayment(shown.id); payment = null },
        )
    }
    reconciling?.let { state ->
        ReconcileSheet(state, onDismiss = { reconciling = null }) { actual, asSpending ->
            reconciling = null
            done(state, actual, asSpending)
        }
    }
}

/** The last row of a list that adds one more: "+ Счёт", "+ Платёж". */
@Composable
fun AddRow(index: Int, count: Int, text: String, onClick: () -> Unit, modifier: Modifier = Modifier.padding(horizontal = Gap.m)) {
    GroupRow(
        index, count, modifier,
        onClick = onClick,
        // A bare "+" glyph, the same everywhere.
        leading = { Icon(painterResource(R.drawable.ic_add), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

/**
 * "Карта · ≈ 16 001 ₽" (the equivalent in the main currency, unless the account is in it), or for
 * savings with a rate just "+1 605 ₽ за октябрь" in the account's own currency: that says what it is.
 */
private fun accountSubline(data: AppData, state: AccountState): String {
    val account = state.account
    val today = data.today
    val forecast = if (account.type == AccountType.SAVINGS && account.interestRate != null) {
        Budget.interestForecast(account, data.operations, today, data.zone)?.let {
            interestLine(it, account.currency, today)
        }
    } else {
        null
    }
    val spendCard = data.envelope?.account?.id == account.id
    val parts = mutableListOf(forecast ?: if (spendCard) tr("Карта трат", "Spending card") else typeLabel(account.type))
    if (state.currency != data.base.code) parts += "≈ " + data.base.approx(state.rubMinor)
    return parts.joinToString(" · ")
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountRow(
    data: AppData,
    state: AccountState,
    index: Int,
    count: Int,
    reconcileMode: Boolean,
    checked: Boolean?,
    onClick: () -> Unit,
    onMatch: () -> Unit,
) {
    val amount = Fmt.amount(state.balanceMinor, state.currency)
    GroupRow(
        index, count, Modifier.padding(horizontal = Gap.m),
        onClick = onClick,
        supporting = { Text(accountSubline(data, state), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Like every amount in a list (payments, operations): one size, one ink, cents and all.
                Text(amount, style = MaterialTheme.typography.titleMedium.merge(Tnum), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                if (reconcileMode) {
                    Spacer(Modifier.width(Gap.s))
                    // One tap when the bank shows the same; a check once done.
                    val pop = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                    AnimatedContent(checked != null, transitionSpec = { scaleIn(pop) + fadeIn() togetherWith fadeOut() }, label = "check") { done ->
                        if (done) {
                            Box(Modifier.size(40.dp).background(pickedColor(), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_check), tr("Сверено", "Checked"), tint = onPickedColor())
                            }
                        } else {
                            FilledTonalButton(onClick = onMatch, modifier = Modifier.height(40.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                                Text(tr("Сходится", "Matches"), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.account.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountScreen(
    data: AppData,
    accountId: Long,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onEditAccount: () -> Unit,
    onEditOperation: (OperationFull) -> Unit,
    onReconcile: (Long, Boolean) -> Unit,
) {
    val state = data.states[accountId] ?: return
    val account = state.account
    var reconciling by remember { mutableStateOf(false) }
    val operations = data.visibleOperations.filter { full -> full.postings.any { it.accountId == accountId } }
    BackHandler(onBack = onBack)
    Scaffold(
        // Like the expense form: back, then the page's action as the wide button.
        bottomBar = {
            // On the page's own colour, so the list ends above it, as the form's does.
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(Gap.m), horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                RoundAction(R.drawable.ic_back, tr("Назад", "Back"), onBack)
                TonalAction(tr("Изменить счёт", "Edit account"), onEditAccount)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Gap.l)) {
            item(key = "title") {
                Text(
                    account.name,
                    Modifier.padding(start = Gap.l, end = Gap.l, top = Gap.l, bottom = Gap.m),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item(key = "hero") {
                // Neutral unless the money is free to spend: only then the vivid tile.
                val scheme = MaterialTheme.colorScheme
                val free = account.includeInFree
                val ink = scheme.onSurface
                val quiet = scheme.onSurfaceVariant
                HeroTile(
                    Modifier.padding(horizontal = Gap.s),
                    container = tileColor(),
                    content = ink,
                ) {
                    Caption(typeLabel(account.type), color = quiet)
                    VSpace(Gap.xs)
                    val amount = Fmt.amount(state.balanceMinor, state.currency)
                    // The kopecks are the same ink, only quieter.
                    HeroAmount(amount, ink)
                    data.others(state.rubMinor, state.currency).takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge.merge(Tnum), color = quiet)
                    }
                    if (state.currency != "RUB") {
                        state.costBasis?.let {
                            Text(
                                tr("курс покупки ", "bought at ") + "${Fmt.number(it, 2)} ₽/${Currencies.symbol(state.currency)}",
                                style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                                color = quiet,
                            )
                        }
                    }
                    if (account.type == AccountType.SAVINGS && account.interestRate != null) {
                        val today = data.today
                        Budget.interestForecast(account, data.operations, today, data.zone)?.let {
                            Text(
                                interestLine(it, account.currency, today),
                                style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                                color = quiet,
                            )
                        }
                    }
                    VSpace(Gap.m)
                    // Inside the fixed tile: a tint of the tile's quiet ink, never pure black; the same in both themes.
                    FilledTonalButton(
                        onClick = { reconciling = true },
                        modifier = Modifier.heightIn(min = ButtonDefaults.MinHeight),
                        contentPadding = PaddingValues(horizontal = Gap.l),
                        colors = if (free) ButtonDefaults.filledTonalButtonColors(containerColor = scheme.onPrimaryFixedVariant.copy(alpha = 0.14f), contentColor = scheme.onPrimaryFixedVariant)
                        else ButtonDefaults.filledTonalButtonColors(),
                    ) { Text(tr("Сверить", "Reconcile"), style = MaterialTheme.typography.labelLarge) }
                }
            }
            if (account.type == AccountType.CREDIT || account.type == AccountType.LOAN) {
                item(key = "debt") {
                    Tile(Modifier.fillMaxWidth().padding(horizontal = Gap.s).padding(top = Gap.s)) { DebtDetails(data, state) }
                }
            }
            operationItems(data, operations, accountId, onEditOperation)
        }
    }
    if (reconciling) ReconcileSheet(state, onDismiss = { reconciling = false }) { actual, asSpending -> reconciling = false; onReconcile(actual, asSpending) }
}

/**
 * "Сколько на самом деле?" as a small sheet: the balance stands there as the big number, and
 * one pill says "Сходится" — until a different number is typed, then it says what will be recorded.
 */
@Composable
fun ReconcileSheet(state: AccountState, onDismiss: () -> Unit, onReconcile: (Long, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    // Less money than recorded is usually spending nobody wrote down: by default it is booked as
    // everyday spending, so the pace stays honest (SPEC §5). A debt's difference is never spending.
    var asSpending by remember { mutableStateOf(true) }
    // A debt is checked the way the bank shows it: what is owed. "15 000" on a credit card is a balance
    // of −15 000; only "+…" means money on it. Any other account takes the number as it is, "−" included.
    val debt = state.account.type == AccountType.CREDIT || state.account.type == AccountType.LOAN
    val negative = text.startsWith("-") || text.startsWith("−")
    val plus = text.startsWith("+")
    val typed = Fmt.parseMinor(text.removePrefix("-").removePrefix("−").removePrefix("+"), state.currency)
    val value = if (text.isBlank()) state.balanceMinor
    else typed?.let { if (debt) (if (plus) it else -it) else (if (negative) -it else it) }
    val diff = value?.minus(state.balanceMinor)
    val shortfall = !debt && diff != null && diff < 0
    FormSheet(
        onDismiss = onDismiss,
        actions = {
            val label = if (diff == null || diff == 0L) tr("Сходится", "It matches")
            else tr("Записать ", "Record ") + Fmt.amount(diff, state.currency, signed = true)
            AnimatedContent(label, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "reconcile") { text ->
                Row(Modifier.fillMaxWidth()) { PrimaryAction(text, { value?.let { onReconcile(it, shortfall && asSpending) } }, enabled = value != null) }
            }
        },
    ) {
        Caption(
            if (debt) tr("${state.account.name} · сколько должен на самом деле?", "${state.account.name} · how much is really owed?")
            else tr("${state.account.name} · сколько на самом деле?", "${state.account.name} · how much is really there?"),
            Modifier.fillMaxWidth(),
        )
        VSpace(Gap.l)
        // Not focused: a glance and "Сходится" is the common case. Typing replaces the balance shown.
        val (whole, fraction) = Fmt.split(if (debt) -state.balanceMinor else state.balanceMinor, state.currency)
        HeroAmountField(
            text = text,
            onText = { text = it },
            symbol = Currencies.symbol(state.currency),
            placeholder = whole + fraction,
            placeholderColor = MaterialTheme.colorScheme.onSurface,
        )
        VSpace(Gap.s)
        Text(
            when {
                diff == null || diff == 0L -> " "
                shortfall && asSpending -> tr("Разница ", "Difference ") + Fmt.amount(diff, state.currency, signed = true) + tr(" запишется тратой «Не записано»", " goes in as spending “Not recorded”")
                else -> tr("Разница ", "Difference ") + Fmt.amount(diff, state.currency, signed = true) + tr(" запишется корректировкой", " goes in as an adjustment")
            },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium.merge(Tnum),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (shortfall) {
            VSpace(Gap.s)
            // The whole line flips the switch.
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(asSpending, role = Role.Switch) { asSpending = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Это ежедневные траты", "This was everyday spending"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                CheckSwitch(asSpending) { asSpending = it }
            }
        }
    }
}

/** "+1 605 ₽ за октябрь": a savings account's interest this month, in the account's own currency. */
private fun interestLine(minor: Long, currency: String, today: LocalDate): String {
    val amount = Fmt.approx(Currencies.toMajor(minor, currency), currency)
    return tr("+$amount за ${monthName(today)}", "+$amount for ${monthName(today)}")
}

/** "20 ₾": whole units only, for the numbers around the big one, which should be read at a glance. */
private fun whole(minor: Long, code: String): String = Fmt.whole(minor, code) + " " + Currencies.symbol(code)

/**
 * Today's share as a chunky capsule: the pace's colour filling a pale track of the same colour,
 * gliding to a new value with a little give.
 */
@Composable
private fun PaceCapsule(share: Float, color: Color) {
    val shown by animateFloatAsState(share.coerceIn(0f, 1f), MaterialTheme.motionScheme.slowSpatialSpec(), label = "capsule")
    Box(Modifier.fillMaxWidth().height(20.dp).clip(CircleShape).background(color.copy(alpha = 0.22f))) {
        if (shown > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(shown.coerceAtLeast(0.06f)).clip(CircleShape).background(color))
    }
}


/** The empty band under [HeroAmount]'s digits (the font's descent at 72 sp): spacing after it is that much shorter. */
val HeroDescent = 10.dp

/**
 * A tile's big amount: written the way every amount in the lists is (the same face, "2 000 ₽"), only
 * bigger, as big as the width allows up to 72 sp. No smaller sign, no trimmed line box.
 */
@Composable
fun HeroAmount(text: String, color: Color, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Tabular figures run about 0.6 em wide; keep a little air at the edge.
        val fit = with(LocalDensity.current) { (maxWidth * 0.94f / (text.length * 0.6f)).toSp().value }
        // One size on every tab, so the figure does not jump when swiping; smaller only to fit.
        val size = minOf(fit, BIG_FIGURE_SP).sp
        // The one big thing on a tab, still: the sign as in the entry field, one space away, in the
        // muted ink, so the digits are read first.
        val sign = text.substringAfterLast(' ', "").takeIf { it.isNotEmpty() && it.none(Char::isDigit) }
        val style = bigFigure(size)
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val digits = if (sign == null) text else text.dropLast(sign.length).trimEnd()
        Text(
            buildAnnotatedString {
                append(digits)
                if (sign != null) withStyle(SpanStyle(color = muted)) { append(" $sign") }
            },
            style = style,
            color = color,
            maxLines = 1,
        )
    }
}
