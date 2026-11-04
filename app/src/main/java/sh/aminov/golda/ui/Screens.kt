package sh.aminov.golda.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.time.format.DateTimeFormatter
import sh.aminov.golda.domain.I18n
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.graphics.takeOrElse
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
import kotlin.math.roundToInt
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.FirstBaseline
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
    /** A regular payment due: the entry form filled in for it. */
    onPayment: (Obligation) -> Unit = {},
    onEdit: (OperationFull) -> Unit,
) {
    val today = data.today
    val envelope = data.envelope
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding.aboveActions()) {
        item(key = "hero") {
            // One vivid tile holds the day: what may go today, and the period as one bar, time against
            // money, with whether the money goes faster than the days.
            val scheme = MaterialTheme.colorScheme
            val ink = scheme.onPrimaryFixed
            // The figures first, each closed by its caption (14, the base, the plate's second ink). The big
            // figure is cut at its baseline; every other text keeps its box, as on Accounts.
            val words = TextRole.caption
            val status = TextRole.headerFigures
            HeroTile(Modifier.padding(horizontal = Gap.s), content = ink) {
                val code = envelope?.currency
                val figure = if (envelope != null && code != null) whole(envelope.leftTodayMinor, code) else null
                BoxWithConstraints {
                    // The gear stands on the digits' top line, its glyph's right edge on the content's (16 from
                    // the plate), its 48 to tap spilling into the padding; the figure fits what is left.
                    val gear = 48.dp
                    val size = figure?.let { heroFigureSize(it, maxWidth - gear) } ?: BIG_FIGURE_SP.sp
                    val cap = with(LocalDensity.current) { (size.toPx() * 0.727f).roundToInt() + 12.dp.roundToPx() }
                    Row {
                        Box(Modifier.weight(1f).alignBy(FirstBaseline)) {
                            // No account to spend from (or the data is still loading): the tile stays quiet.
                            if (figure != null) HeroAmount(figure, ink, Modifier.letters(BIG_FIGURE_SP.sp, top = false).testTag(Tags.HOME_BIG), signColor = ink)
                        }
                        IconButton(onClick = onSettings, modifier = Modifier.offset(x = 12.dp).alignBy { cap }) {
                            Icon(painterResource(R.drawable.ic_settings), tr("Настройки", "Settings"), tint = scheme.onPrimaryFixedVariant)
                        }
                    }
                }
                if (envelope != null) VSpace(Space.text)
                Text(tr("Можно сегодня", "Safe to spend today"), style = words, maxLines = 1)
                if (envelope == null || code == null) return@HeroTile
                VSpace(Space.block)
                // The period as one bar: the days gone pale, the money gone over them, and what went
                // past the even pace in red. The money short of the pale end is the margin.
                val red = paceColor(Pace.OVER)
                PeriodBar(envelope.timeShare, envelope.spentShare, paceColor(Pace.LASTS), red)
                VSpace(Space.text)
                // Where the bar starts and ends; no "today" here, the caption under the figure says it.
                Row(Modifier.fillMaxWidth()) {
                    Text(dayMonth(envelope.start), Modifier.weight(1f), style = words, maxLines = 1)
                    Text(dayMonth(envelope.end), style = words, maxLines = 1)
                }
                VSpace(Space.block)
                // Against the even pace: within it, or how much past it. A tap changes the period.
                Column(Modifier.fillMaxWidth().clickable(onClick = onPeriod).testTag(Tags.HOME_PERIOD)) {
                    val ahead = envelope.daysLeft
                    fun days(n: Long) = plural(n, "день", "дня", "дней", "day", "days")
                    val beyond = envelope.overNormMinor
                    Text(
                        when {
                            envelope.balanceMinor <= 0 -> AnnotatedString(tr("Денег нет, впереди $ahead ${days(ahead)}", "No money left, $ahead ${days(ahead)} to go"))
                            // Less than half a unit past it still reads "0 ₾": that is within it.
                            beyond > 0 && Fmt.whole(beyond, code) != "0" -> buildAnnotatedString {
                                append(tr("Сверх нормы ", "Over the norm by "))
                                // The bar's red is too pale for words; the error ink reads in both themes, and on
                                // a bold theme's plate of colour where it does not, the plate's own ink does.
                                val overInk = if (contrast(scheme.onErrorContainer, scheme.primaryFixed) >= 4.5) scheme.onErrorContainer else ink
                                withStyle(SpanStyle(color = overInk)) { append(whole(beyond, code)) }
                            }
                            else -> AnnotatedString(tr("В норме", "Within the norm"))
                        },
                        Modifier.testTag(Tags.HOME_RUNWAY),
                        // The tile's main statement: the header's figures, in full ink.
                        style = status,
                        color = ink,
                        maxLines = 1,
                    )
                    VSpace(Space.text)
                    // What a day was meant to be and what it actually is: its caption.
                    val plan = tr("План ${whole(envelope.perDayMinor, code)} в день", "Plan ${whole(envelope.perDayMinor, code)} a day")
                    val fact = envelope.burnMinor?.let { tr("факт ${whole(it, code)}", "actual ${whole(it, code)}") }
                    Text(listOfNotNull(plan, fact).joinToString(" · "), style = words, maxLines = 1)
                }
                data.states.values.mapNotNull { graceWarning(data, it, today) }.forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = ink, modifier = Modifier.padding(top = Gap.xs))
                }
            }
        }
        // A regular payment due in the next two days, so it is no surprise on the card; a tap books it,
        // and once booked it leaves the list.
        val due = Regulars.upcoming(data.obligations, today, data.operations, data.zone)
        if (due.isNotEmpty()) {
            item(key = "upcoming") {
                Column(Modifier.padding(start = Gap.m, end = Gap.m, top = Gap.m)) {
                    due.forEachIndexed { i, (payment, days) ->
                        GroupRow(
                            i, due.size,
                            onClick = { onPayment(payment) },
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
            // The total is the hero here, as big as Home's number. Three sizes on the golden-ratio steps:
            // 14 for the captions (in the plate's second ink), 22 for the figures under the total, the total.
            val ink = MaterialTheme.colorScheme.onPrimaryFixed
            val words = TextRole.caption
            val figures = TextRole.headerFigures
            HeroTile(Modifier.padding(horizontal = Gap.s)) {
                // The figures first, each block closed by its label, the sizes only going down: the total
                // and the same money in other currencies, «Всего на счетах» under them. Each block is one
                // node for TalkBack, read as it stands: the figures, then what they are.
                // The figure cut at its baseline (the empty band under its digits would lift the line under it
                // to a block's distance); every other text by its box. The captions in the plate's second ink.
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    HeroAmount(data.base.whole(total), ink, Modifier.letters(BIG_FIGURE_SP.sp, top = false).testTag(Tags.ACCOUNTS_TOTAL), signColor = ink)
                    VSpace(Space.text)
                    Text(data.others(total, data.base.code), style = figures, color = ink)
                    VSpace(Space.text)
                    Text(tr("Всего на счетах", "Total in accounts"), style = words, maxLines = 1)
                }
                // What the debt costs, a block under: the money it eats, then its label.
                Debts.advice(data.accounts, data.states)?.let { advice ->
                    VSpace(Space.block)
                    val (head, tail) = advice.split("\n", limit = 2).let { it.first() to it.getOrNull(1) }
                    Column(Modifier.semantics(mergeDescendants = true) {}) {
                        tail?.let {
                            Text(it, style = figures, color = ink)
                            VSpace(Space.text)
                        }
                        Text(head, style = words)
                    }
                }
            }
        }
        if (reconcileMode) {
            item(key = "reconcile-hint") {
                GroupLabel(
                    tr("Сверка", "Reconciling"),
                    Modifier.padding(horizontal = Gap.m).padding(top = Gap.l),
                    supporting = tr("Нажми «Сходится», если совпало с банком, или на счёт, если нет", "Tap “Matches” if it agrees with the bank, or the account if it doesn’t"),
                )
            }
        }
        // One list: one bank, one account, so there is nothing to group.
        // Adding one is the group's action, in the header that opens its card; reconciling has none.
        item(key = "gap") {
            if (!reconcileMode) {
                GroupHeader(
                    tr("Счета", "Accounts"), tr("Добавить счёт", "Add an account"), onAdd,
                    Modifier.padding(horizontal = Gap.m).padding(top = Space.cards), alone = data.accounts.isEmpty(),
                )
            }
        }
        data.accounts.forEachIndexed { i, account ->
            item(key = account.id) {
                val state = data.states.getValue(account.id)
                // Reconciling has no header: the rows are the whole card.
                val head = if (reconcileMode) 0 else 1
                AccountRow(
                    data, state, i + head, data.accounts.size + head,
                    reconcileMode = reconcileMode,
                    checked = checked[account.id],
                    onClick = { if (reconcileMode) reconciling = state else onOpen(account.id) },
                    onMatch = { done(state, state.balanceMinor) },
                )
            }
        }
        if (!reconcileMode) regularPayments(data) { payment = it }
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

/**
 * "Карта · ≈ 16 001 ₽" (the equivalent in the main currency, unless the account is in it), for
 * savings with a rate just "+1 605 ₽ за октябрь" in the account's own currency: that says what it is;
 * a debt with a rate carries it ("29,9 %"). Never empty, so every row has two lines: at the least the type.
 */
internal fun accountSubline(data: AppData, state: AccountState): String {
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
    // The type only when the name does not already say it: no "Кредитка / Кредитка".
    val kind = forecast ?: if (spendCard) tr("Карта трат", "Spending card") else typeLabel(account.type).takeUnless { account.name.contains(it, ignoreCase = true) }
    val parts = listOfNotNull(kind).toMutableList()
    val debt = account.type == AccountType.CREDIT || account.type == AccountType.LOAN
    if (debt) account.interestRate?.let { parts += Fmt.percent(it / 100) }
    if (state.currency != data.base.code) parts += "≈ " + data.base.approx(state.rubMinor)
    return parts.joinToString(" · ").ifEmpty { typeLabel(account.type) }
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
        // Always two lines, so the rows run evenly.
        supporting = { Text(accountSubline(data, state), style = TextRole.caption, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // What is on the account reads first: a step over the name, 22 against 16.
                Text(amount, style = TextRole.rowAmount, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
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
            Text(state.account.name, Modifier.weight(1f, fill = false), style = TextRole.rowName, maxLines = 1, overflow = TextOverflow.Ellipsis)

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
                // The page's one lead; Сверить and Погасить досрочно are the small tonal buttons in the tiles.
                PrimaryAction(tr("Изменить счёт", "Edit account"), onEditAccount)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
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
                    // The type over the figure, unless the title above already says it.
                    Caption(typeLabel(account.type).takeUnless { account.name.contains(it, ignoreCase = true) } ?: tr("Баланс", "Balance"), color = quiet)
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
                    // Its own group, set apart like every block on the page, padded like the hero.
                    GroupLabel(typeLabel(account.type), Modifier.padding(horizontal = Gap.m).padding(top = Gap.l))
                    HeroTile(Modifier.padding(horizontal = Gap.s)) { DebtDetails(data, state) }
                }
            }
            operationItems(data, operations, accountId, onEditOperation)
        }
        // Rows scroll under the clock: a band of the page's colour keeps them from showing through, as on the tabs.
        val density = LocalDensity.current
        val clock = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
        Spacer(Modifier.fillMaxWidth().height(clock).background(MaterialTheme.colorScheme.background))
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
    // Less money on a card or in cash is usually spending nobody wrote down: by default it is booked
    // as everyday spending, so the pace stays honest (SPEC §5). On savings it is rarely that (a debt
    // paid from them, a transfer left out), so there the default is a plain adjustment. A debt's
    // difference is never spending.
    var asSpending by remember { mutableStateOf(state.account.type == AccountType.CARD || state.account.type == AccountType.CASH) }
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
            if (debt) tr("Сколько ты должен по «${state.account.name}»?", "How much do you owe on “${state.account.name}”?")
            else tr("Сколько на «${state.account.name}» на самом деле?", "How much is really on “${state.account.name}”?"),
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
            signed = true,
        )
        // No sentence under the figure: the button says what is recorded ("Записать −43,45 $"), the switch
        // whether as spending. A line there stole the figure's place and looked like the point.
        if (shortfall) {
            VSpace(Gap.m)
            // The whole line flips the switch; its words start where the question above starts.
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(asSpending, role = Role.Switch) { asSpending = it }
                    .padding(horizontal = LocalTextInset.current),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Это ежедневные траты", "Count as everyday spending"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                CheckSwitch(asSpending) { asSpending = it }
            }
        }
    }
}

/** "+1 605 ₽ за октябрь": a savings account's interest this month, in the account's own currency. */
private fun interestLine(minor: Long, currency: String, today: LocalDate): String {
    val amount = Fmt.approx(Currencies.toMajor(minor, currency), currency)
    // Expected, not earned yet: "≈ +3 106 ₽ в ноябре".
    return tr("≈ +$amount в ${monthIn(today)}", "≈ +$amount in ${monthName(today)}")
}

/** "20 ₾": whole units only, for the numbers around the big one, which should be read at a glance. */
private fun whole(minor: Long, code: String): String = Fmt.whole(minor, code) + " " + Currencies.symbol(code)

/** "26 окт", "Oct 26": the ends of the period under its bar. Java's own Russian is "нояб.", hence the list. */
internal fun dayMonth(date: LocalDate): String =
    if (I18n.russian) "${date.dayOfMonth} " + listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")[date.monthValue - 1]
    else date.format(DateTimeFormatter.ofPattern("MMM d", I18n.locale))

/**
 * The period as one wide bar with small corners: the days gone in a pale [color], the money gone over
 * them in [color] as far as the days and in [over] past them. Gliding to new values with a little give.
 */
@Composable
internal fun PeriodBar(time: Float, spent: Float, color: Color, over: Color) {
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val t by animateFloatAsState(time.coerceIn(0f, 1f), spec, label = "time")
    val s by animateFloatAsState(spent.coerceIn(0f, 1f), spec, label = "spent")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(6.dp))) {
        fun band(from: Float, to: Float, paint: Color) {
            if (to <= from) return
            val x = size.width * from
            val w = size.width * (to - from)
            drawRect(paint, Offset(if (rtl) size.width - x - w else x, 0f), Size(w, size.height))
        }
        drawRect(track)
        band(0f, t, color.copy(alpha = 0.32f))
        band(0f, minOf(s, t), color)
        band(t, s, over)
    }
}


/**
 * A tile's big amount: written the way every amount in the lists is (the same face, "2 000 ₽"), only
 * bigger, as big as the width allows up to 72 sp. No smaller sign, no trimmed line box.
 */
@Composable
fun HeroAmount(text: String, color: Color, modifier: Modifier = Modifier, signColor: Color = Color.Unspecified) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val size = heroFigureSize(text, maxWidth)
        // The one big thing on a tab, still: the sign as in the entry field, one space away, in the
        // muted ink, so the digits are read first.
        val sign = text.substringAfterLast(' ', "").takeIf { it.isNotEmpty() && it.none(Char::isDigit) }
        val style = bigFigure(size)
        val muted = signColor.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }
        val digits = if (sign == null) text else text.dropLast(sign.length).trimEnd()
        // Optically flush left: big digits carry their own side bearing, which at 94 sp reads as an
        // indent against the caption above; pulled back by the first glyph's measured ink.
        val nudge = with(LocalDensity.current) { firstInk(digits, style, size.toPx()).toDp() }
        Text(
            buildAnnotatedString {
                append(digits)
                if (sign != null) withStyle(SpanStyle(color = muted)) { append(" $sign") }
            },
            Modifier.offset(x = -nudge),
            style = style,
            color = color,
            maxLines = 1,
        )
    }
}

/** The big figure's size for [text] in [width]: one size on every tab, so it does not jump when swiping; smaller only to fit. */
@Composable
internal fun heroFigureSize(text: String, width: Dp): TextUnit {
    // Tabular figures run about 0.6 em wide; keep a little air at the edge.
    val fit = with(LocalDensity.current) { (width * 0.94f / (text.length * 0.6f)).toSp().value }
    return minOf(fit, BIG_FIGURE_SP).sp
}

/** How far right of its origin the first character's ink starts, in px; about 6.5 % of the size if the face cannot be measured. */
@Composable
private fun firstInk(text: String, style: androidx.compose.ui.text.TextStyle, px: Float): Float {
    val resolver = androidx.compose.ui.platform.LocalFontFamilyResolver.current
    val face = resolver.resolve(style.fontFamily, style.fontWeight ?: FontWeight.Normal).value as? android.graphics.Typeface
    return remember(text.firstOrNull(), face, px) {
        val first = text.firstOrNull() ?: return@remember 0f
        if (face == null) return@remember px * 0.065f
        val paint = android.graphics.Paint().apply {
            typeface = face
            textSize = px
            fontFeatureSettings = "tnum"
        }
        val box = android.graphics.Rect()
        paint.getTextBounds(first.toString(), 0, 1, box)
        box.left.toFloat().coerceIn(0f, px * 0.15f)
    }
}

/** "в ноябре": the month in the prepositional case. */
private fun monthIn(date: LocalDate): String = listOf("январе", "феврале", "марте", "апреле", "мае", "июне", "июле", "августе", "сентябре", "октябре", "ноябре", "декабре")[date.monthValue - 1]
