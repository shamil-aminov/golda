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
            val ink = if (over) scheme.onErrorContainer else scheme.onPrimaryFixed
            val quiet = if (over) scheme.onErrorContainer else scheme.onPrimaryFixedVariant
            // Two kinds of type on the tile and no more: the big number, and one style in one colour for
            // every word around it.
            val text = MaterialTheme.typography.bodyLarge.merge(Tnum)
            HeroTile(
                Modifier.padding(horizontal = Gap.m),
                container = if (over) scheme.errorContainer else scheme.primaryFixed,
                content = ink,
            ) {
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
                VSpace(Gap.m)
                // What is still left of today's share; flat and full once it is spent.
                val share = when {
                    over -> 1f
                    envelope.perDayMinor > 0 -> (left.toFloat() / envelope.perDayMinor).coerceIn(0f, 1f)
                    else -> 0f
                }
                // The tile's quiet ink, not black: a black line with no visible track reads as a divider.
                WavyBar(share, ink = quiet, hero = true, flat = over || share >= 1f)
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
                            ahead <= 1L -> tr("Последний день", "The last day")
                            lasts == null -> tr("Впереди $ahead ${days(ahead)}", "$ahead ${days(ahead)} to go")
                            envelope.lasts(today) -> (lasts - ahead).let { spare ->
                                // A week or more to spare is worth saying; a day or two is noise.
                                tr("Хватит на все $ahead ${days(ahead)}", "Lasts all $ahead ${days(ahead)}") + if (spare >= 7) ", +$spare" else ""
                            }
                            else -> tr("Хватит на $lasts из $ahead $ofDays", "Lasts $lasts of $ahead $ofDays")
                        },
                        Modifier.testTag(Tags.HOME_RUNWAY),
                        // The tile's main statement: the one semibold line, in full ink.
                        style = text.copy(fontWeight = FontWeight.SemiBold),
                        color = ink,
                        maxLines = 1,
                    )
                    VSpace(Gap.xs)
                    // What a day was meant to be and what it actually is, in the same words and ink.
                    val plan = tr("План ${whole(envelope.perDayMinor, code)} в день", "Plan ${whole(envelope.perDayMinor, code)} a day")
                    val fact = envelope.burnMinor?.let { tr("факт ${whole(it, code)}", "actual ${whole(it, code)}") }
                    Text(listOfNotNull(plan, fact).joinToString(" · "), style = text, color = quiet, maxLines = 1)
                }
                data.states.values.mapNotNull { graceWarning(data, it, today) }.forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = ink, modifier = Modifier.padding(top = Gap.xs))
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
) {
    val total = data.states.values.sumOf { it.rubMinor }
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
            val text = MaterialTheme.typography.bodyLarge.merge(Tnum)
            val quiet = scheme.onPrimaryFixedVariant
            HeroTile(Modifier.padding(horizontal = Gap.m)) {
                Text(tr("Всего", "Total"), style = text, color = quiet, maxLines = 1)
                VSpace(Gap.s)
                HeroAmount(data.base.whole(total), scheme.onPrimaryFixed, Modifier.testTag(Tags.ACCOUNTS_TOTAL))
                VSpace(Gap.s)
                Text(data.others(total, data.base.code), style = text, color = quiet)
                // The one line that says something here: semibold, like Home's; its figure under it.
                Debts.advice(data.accounts, data.states)?.let { advice ->
                    VSpace(Gap.m)
                    val (head, tail) = advice.split("\n", limit = 2).let { it.first() to it.getOrNull(1) }
                    Text(head, style = text.copy(fontWeight = FontWeight.SemiBold), color = scheme.onPrimaryFixed)
                    tail?.let { Text(it, style = text, color = quiet) }
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
                    // The bank's total on the right, under the accounts' amounts: what its app shows.
                    GroupLabel(group, Modifier.padding(horizontal = Gap.m)) {
                        Text(
                            data.base.approx(rub),
                            Modifier.padding(end = Gap.m),
                            style = MaterialTheme.typography.labelLarge.merge(Tnum),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                // Like every amount in a list: one size, one ink, cents and all.
                Text(amount, style = MaterialTheme.typography.titleMedium.merge(Tnum), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
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
    onReconcile: (Long, Boolean) -> Unit,
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
private fun whole(minor: Long, code: String): String = Fmt.split(minor, code).first + " " + Currencies.symbol(code)

/**
 * A tile's big amount: written the way every amount in the lists is (the same face, "2 000 ₽"), only
 * bigger, as big as the width allows up to 72 sp. No smaller sign, no trimmed line box.
 */
@Composable
fun HeroAmount(text: String, color: Color, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Tabular figures run about 0.6 em wide; keep a little air at the edge.
        val fit = with(LocalDensity.current) { (maxWidth * 0.94f / (text.length * 0.6f)).toSp().value }
        val size = minOf(fit, 72f).sp
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.merge(Tnum).copy(fontSize = size, lineHeight = size * 1.15f),
            color = color,
            maxLines = 1,
        )
    }
}
