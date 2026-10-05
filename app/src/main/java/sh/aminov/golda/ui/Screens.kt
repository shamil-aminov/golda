package sh.aminov.golda.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
fun HomeScreen(data: AppData, padding: PaddingValues, listState: LazyListState, onSettings: () -> Unit, onEdit: (OperationFull) -> Unit) {
    val today = data.today
    val budget = Budget.today(data.states, data.operations, data.settings, today, data.zone, data.allObligations, data.rates)
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding.aboveActions()) {
        item(key = "hero") {
            // One vivid tile holds the day: the number, how much of it is left, and how long it has to last.
            // Spent past the budget, the whole tile turns to the error container.
            val over = budget.leftTodayRub < 0
            val scheme = MaterialTheme.colorScheme
            val ink = if (over) scheme.onErrorContainer else scheme.onPrimaryFixed
            val quiet = if (over) scheme.onErrorContainer else scheme.onPrimaryFixedVariant
            HeroTile(
                Modifier.padding(horizontal = Gap.m),
                container = if (over) scheme.errorContainer else scheme.primaryFixed,
                content = ink,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Caption(tr("Можно сегодня", "Safe to spend today"), Modifier.weight(1f), color = quiet)
                    IconButton(onClick = onSettings, modifier = Modifier.overhang()) {
                        Icon(painterResource(R.drawable.ic_settings), tr("Настройки", "Settings"), tint = quiet)
                    }
                }
                VSpace(Gap.xs)
                // Whole units of the main currency only, as big as the tile allows; the rest of the
                // shown currencies under it.
                val base = data.base
                BigNumber(base.whole(budget.leftTodayRub), base.minor(budget.leftTodayRub), ink, Modifier.testTag(Tags.HOME_BIG))
                Text(data.others(budget.leftTodayRub, base.code), Modifier.testTag(Tags.HOME_OTHERS), style = MaterialTheme.typography.bodyLarge.merge(Tnum), color = quiet)
                VSpace(Gap.m)
                // What is still left of today's budget, like the number above; flat and full once it is spent.
                val left = when {
                    over -> 1f
                    budget.perDayRub > 0 -> (budget.leftTodayRub.toFloat() / budget.perDayRub).coerceIn(0f, 1f)
                    else -> 0f
                }
                // The tile's quiet ink, not black: a black line with no visible track reads as a divider.
                WavyBar(left, ink = quiet, hero = true, flat = over || left >= 1f)
                VSpace(Gap.s)
                val perDay = base.approx(budget.perDayRub)
                val days = budget.daysLeft.toLong()
                val dayWord = plural(days, "день", "дня", "дней", "day", "days")
                // What the bar is measured against, how it compares with the start of the pay period
                // (▲ ahead, ▼ behind), and how long it has to last.
                val pace = Budget.pace(budget, data.accounts, data.operations, data.settings, today, data.zone, data.allObligations, data.rates)
                val body = MaterialTheme.typography.bodyMedium.merge(Tnum)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("$perDay в день", "$perDay a day"), style = body, color = quiet, maxLines = 1)
                    if (pace != null) {
                        val arrow = if (pace >= 0) "▲" else "▼"
                        RollingNumber(" $arrow" + base.approx(kotlin.math.abs(pace)).removeSuffix(" " + base.symbol), pace, body, color = if (pace >= 0) ink else quiet)
                    }
                    Text(
                        tr(" · $days $dayWord до зарплаты", " · $days $dayWord to payday"),
                        modifier = Modifier.weight(1f, fill = false).testTag(Tags.HOME_PAYDAY),
                        style = body,
                        color = quiet,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                data.states.values.mapNotNull { graceWarning(data, it, today) }.forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = ink, modifier = Modifier.padding(top = Gap.xs))
                }
                if (budget.obligationsRub > 0) {
                    Text(
                        tr("Отложено на платежи: ", "Set aside for payments: ") + base.approx(budget.obligationsRub),
                        style = body,
                        color = quiet,
                    )
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
    onReconcile: (Long, Long) -> Unit,
    reconcileMode: Boolean = false,
    onAllReconciled: () -> Unit = {},
) {
    val total = data.states.values.sumOf { it.rubMinor }
    var reconciling by remember { mutableStateOf<AccountState?>(null) }
    // In reconcile mode, the accounts already checked this round: true when the balance matched.
    var checked by remember(reconcileMode) { mutableStateOf(mapOf<Long, Boolean>()) }
    fun done(state: AccountState, actual: Long) {
        onReconcile(state.account.id, actual)
        if (reconcileMode) {
            checked = checked + (state.account.id to (actual == state.balanceMinor))
            if (checked.keys.containsAll(data.accounts.map { it.id })) onAllReconciled()
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.aboveActions()) {
        item(key = "total") {
            // The total is the hero here, as big as Home's number.
            val scheme = MaterialTheme.colorScheme
            HeroTile(Modifier.padding(horizontal = Gap.m)) {
                Caption(tr("Всего", "Total"), color = scheme.onPrimaryFixedVariant)
                VSpace(Gap.xs)
                BigNumber(data.base.whole(total), data.base.minor(total), scheme.onPrimaryFixed, Modifier.testTag(Tags.ACCOUNTS_TOTAL))
                Text(data.others(total, data.base.code), style = MaterialTheme.typography.bodyLarge.merge(Tnum), color = scheme.onPrimaryFixedVariant)
                Debts.advice(data.accounts, data.states)?.let {
                    VSpace(Gap.m)
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onPrimaryFixedVariant)
                }
            }
        }
        if (reconcileMode) {
            item(key = "reconcile-hint") {
                GroupLabel(
                    tr("Сверка", "Reconciling"),
                    Modifier.padding(horizontal = Gap.m).padding(top = Gap.l),
                    supporting = tr("Совпало с банком — «Сходится». Нет — нажми на счёт.", "Matches the bank: “Matches”. Doesn’t: tap the account."),
                )
            }
        }
        val blocks = accountBlocks(data.accounts)
        blocks.forEachIndexed { b, (group, accounts) ->
            val last = b == blocks.lastIndex
            // "+ Счёт" closes the last plain list; under a bank's group it stands on its own.
            val withAdd = last && group == null
            item(key = "gap-$b") { VSpace(if (b == 0 && reconcileMode) 0.dp else Gap.l) }
            if (group != null) {
                item(key = "group-$group") {
                    val rub = accounts.sumOf { data.states[it.id]?.rubMinor ?: 0 }
                    GroupLabel("$group · ${data.base.approx(rub)}", Modifier.padding(horizontal = Gap.m))
                }
            }
            val count = accounts.size + if (withAdd) 1 else 0
            accounts.forEachIndexed { i, account ->
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
            if (withAdd) item(key = "add") { AddRow(count - 1, count, tr("Счёт", "Account"), onAdd) }
        }
        if (blocks.isEmpty() || blocks.last().first != null) {
            item(key = "add") {
                VSpace(if (blocks.isEmpty()) Gap.l else Gap.s)
                AddRow(0, 1, tr("Счёт", "Account"), onAdd)
            }
        }
    }
    reconciling?.let { state ->
        ReconcileSheet(state, onDismiss = { reconciling = null }) { actual ->
            reconciling = null
            done(state, actual)
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
 * Accounts in their order, a bank's accounts pulled together where the first of them stands, and
 * neighbours without a bank in one list. A group of one is not a group.
 */
private fun accountBlocks(accounts: List<Account>): List<Pair<String?, List<Account>>> {
    val groups = accounts.filter { !it.groupName.isNullOrBlank() }.groupBy { it.groupName!! }.filterValues { it.size > 1 }
    val seen = mutableSetOf<String>()
    val blocks = mutableListOf<Pair<String?, MutableList<Account>>>()
    for (account in accounts) {
        val group = account.groupName?.takeIf { it in groups }
        when {
            group == null -> if (blocks.lastOrNull()?.first == null && blocks.isNotEmpty()) blocks.last().second += account else blocks += null to mutableListOf(account)
            seen.add(group) -> blocks += group to groups.getValue(group).toMutableList()
        }
    }
    return blocks
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
    val parts = mutableListOf(forecast ?: typeLabel(account.type))
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
                RollingNumber(
                    amount, state.balanceMinor,
                    MaterialTheme.typography.headlineSmall,
                    quiet = centsOf(amount),
                )
                if (reconcileMode) {
                    Spacer(Modifier.width(Gap.s))
                    // One tap when the bank shows the same; a check once done.
                    val pop = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                    AnimatedContent(checked != null, transitionSpec = { scaleIn(pop) + fadeIn() togetherWith fadeOut() }, label = "check") { done ->
                        if (done) {
                            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondary, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_check), tr("Сверено", "Checked"), tint = MaterialTheme.colorScheme.onSecondary)
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
            // Free money is marked, not painted.
            if (state.account.includeInFree) {
                Spacer(Modifier.width(Gap.s))
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
            }
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
    onReconcile: (Long) -> Unit,
) {
    val state = data.states[accountId] ?: return
    val account = state.account
    var reconciling by remember { mutableStateOf(false) }
    val operations = data.visibleOperations.filter { full -> full.postings.any { it.accountId == accountId } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), tr("Назад", "Back")) } },
                actions = { IconButton(onClick = onEditAccount) { Icon(painterResource(R.drawable.ic_edit), tr("Изменить", "Edit")) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding() + Gap.s, bottom = padding.calculateBottomPadding() + Gap.l)) {
            item(key = "hero") {
                // Neutral unless the money is free to spend: only then the vivid tile.
                val scheme = MaterialTheme.colorScheme
                val free = account.includeInFree
                val ink = if (free) scheme.onPrimaryFixed else scheme.onSurface
                val quiet = if (free) scheme.onPrimaryFixedVariant else scheme.onSurfaceVariant
                HeroTile(
                    Modifier.padding(horizontal = Gap.m),
                    container = if (free) scheme.primaryFixed else tileColor(),
                    content = ink,
                ) {
                    Caption(typeLabel(account.type) + (account.groupName?.let { " · $it" } ?: ""), color = quiet)
                    VSpace(Gap.xs)
                    val amount = Fmt.amount(state.balanceMinor, state.currency)
                    // The kopecks are the same ink, only quieter.
                    BigNumber(amount, state.balanceMinor, ink, maxSp = 96f, quiet = centsOf(amount), quietColor = ink.copy(alpha = 0.55f))
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
                    Tile(Modifier.fillMaxWidth().padding(horizontal = Gap.m).padding(top = Gap.s)) { DebtDetails(data, state) }
                }
            }
            operationItems(data, operations, accountId, onEditOperation)
        }
    }
    if (reconciling) ReconcileSheet(state, onDismiss = { reconciling = false }) { reconciling = false; onReconcile(it) }
}

/**
 * "Сколько на самом деле?" as a small sheet: the balance stands there as the big number, and
 * one pill says "Сходится" — until a different number is typed, then it says what will be recorded.
 */
@Composable
fun ReconcileSheet(state: AccountState, onDismiss: () -> Unit, onReconcile: (Long) -> Unit) {
    var text by remember { mutableStateOf("") }
    // A debt is checked the way the bank shows it: what is owed. "15 000" on a credit card is a balance
    // of −15 000; only "+…" means money on it. Any other account takes the number as it is, "−" included.
    val debt = state.account.type == AccountType.CREDIT || state.account.type == AccountType.LOAN
    val negative = text.startsWith("-") || text.startsWith("−")
    val plus = text.startsWith("+")
    val typed = Fmt.parseMinor(text.removePrefix("-").removePrefix("−").removePrefix("+"), state.currency)
    val value = if (text.isBlank()) state.balanceMinor
    else typed?.let { if (debt) (if (plus) it else -it) else (if (negative) -it else it) }
    val diff = value?.minus(state.balanceMinor)
    FormSheet(
        onDismiss = onDismiss,
        actions = {
            val label = if (diff == null || diff == 0L) tr("Сходится", "It matches")
            else tr("Записать ", "Record ") + Fmt.amount(diff, state.currency, signed = true)
            AnimatedContent(label, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "reconcile") { text ->
                Row(Modifier.fillMaxWidth()) { PrimaryAction(text, { value?.let(onReconcile) }, enabled = value != null) }
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
            if (diff == null || diff == 0L) " " else tr("Разница ", "Difference ") + Fmt.amount(diff, state.currency, signed = true) + tr(" запишется корректировкой", " goes in as an adjustment"),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium.merge(Tnum),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** "+1 605 ₽ за октябрь": a savings account's interest this month, in the account's own currency. */
private fun interestLine(minor: Long, currency: String, today: LocalDate): String {
    val amount = Fmt.approx(Currencies.toMajor(minor, currency), currency)
    return tr("+$amount за ${monthName(today)}", "+$amount for ${monthName(today)}")
}
