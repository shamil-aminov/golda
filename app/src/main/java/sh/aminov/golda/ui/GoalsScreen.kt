package sh.aminov.golda.ui

import sh.aminov.golda.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.aminov.golda.data.Goal
import sh.aminov.golda.data.Wish
import sh.aminov.golda.data.WishStatus
import sh.aminov.golda.domain.Base
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.Goals
import sh.aminov.golda.domain.VoiceAction
import sh.aminov.golda.domain.VoiceMapper
import sh.aminov.golda.domain.tr

/**
 * "Копилка", in Accounts' grammar: the main goal is the hero tile (what is saved, the wavy bar, how
 * far along, what refusals put in), then the other goals as one grouped list ending in "+ Цель",
 * the wishes waiting for a decision, and what was decided, folded away.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GoalsScreen(
    data: AppData,
    padding: PaddingValues,
    onEditGoal: (Goal?) -> Unit,
    onDecide: (Wish) -> Unit,
    onDeleteWish: (Wish) -> Unit,
    onBuyGoal: (Goal) -> Unit,
    onCelebrated: (Long) -> Unit,
) {
    val main = data.goals.firstOrNull { it.isMain }
    val now = System.currentTimeMillis()
    val waiting = data.wishes.filter { it.status == WishStatus.WAITING }.sortedBy { it.decideAt }
    val decided = data.wishes.filter { it.status != WishStatus.WAITING }.sortedByDescending { it.decidedAt }
    val skippedRub = decided.filter { it.status == WishStatus.SKIPPED }.sumOf { Goals.rubOf(it.amountMinor, it.currency, data.rates) }
    var showDecided by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.aboveActions()) {
        item(key = "main") {
            if (main == null) {
                val quiet = MaterialTheme.colorScheme.onPrimaryFixedVariant
                HeroTile(Modifier.padding(horizontal = Gap.m), onClick = { onEditGoal(null) }) {
                    Caption(tr("Копилка", "Savings"), color = quiet)
                    VSpace(Gap.s)
                    if (data.goals.isEmpty()) {
                        Text(tr("Поставь цель", "Set a goal"), style = MaterialTheme.typography.headlineMedium)
                        VSpace(Gap.xs)
                        Text(
                            tr("Велосипед, поездка, подушка. С ней сравнивается каждая покупка, а отказы копятся в неё.", "A bike, a trip, a cushion. Every purchase is held up against it, and what you skip goes into it."),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        // Goals exist, none is the main one: the star in a goal makes it so.
                        Text(tr("Выбери главную цель", "Pick the main goal"), style = MaterialTheme.typography.headlineMedium)
                        VSpace(Gap.xs)
                        Text(tr("Звезда в цели делает её главной", "The star in a goal makes it the main one"), style = MaterialTheme.typography.bodyLarge)
                    }
                    SkippedLine(skippedRub, data.base, quiet)
                }
            } else {
                MainGoalTile(data, main, skippedRub, onEdit = { onEditGoal(main) }, onBuy = onBuyGoal, onCelebrated = onCelebrated)
            }
        }
        if (data.goals.isNotEmpty()) {
            // The other goals as one labelled list, like the accounts, and "+ Цель" closing it.
            val others = data.goals.filter { !it.isMain }
            val count = others.size + 1
            item(key = "others-title") { GroupLabel(tr("Цели", "Goals"), Modifier.padding(horizontal = Gap.m).padding(top = Gap.l)) }
            others.forEachIndexed { i, goal ->
                item(key = "goal-${goal.id}") { GoalRow(data, goal, i, count) { onEditGoal(goal) } }
            }
            item(key = "add-goal") { AddRow(count - 1, count, tr("Цель", "Goal"), { onEditGoal(null) }) }
        }
        // Wishes go away with a swipe to the left (or a long press), and the snackbar can bring them back.
        if (waiting.isNotEmpty()) {
            item(key = "waiting-title") { GroupLabel(tr("Ждут решения", "Waiting for a decision"), Modifier.padding(horizontal = Gap.m).padding(top = Gap.l)) }
            waiting.forEachIndexed { i, wish ->
                item(key = "wish-${wish.id}") {
                    SwipeToRemove(i, waiting.size, Modifier.animateItem(placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec()), onRemove = { onDeleteWish(wish) }) {
                        GroupRow(
                            i, waiting.size,
                            onClick = { onDecide(wish) },
                            onLongClick = { onDeleteWish(wish) },
                            supporting = { Text(left(wish, now) + (main?.let { " · " + goalShare(wish, it, data) } ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailing = { Text(Fmt.amount(wish.amountMinor, wish.currency), style = MaterialTheme.typography.titleMedium.merge(Tnum)) },
                        ) { Text(wish.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
        if (decided.isNotEmpty()) {
            item(key = "decided-title") {
                Row(
                    Modifier.padding(horizontal = Gap.m).padding(top = Gap.l).clip(CircleShape).clickable { showDecided = !showDecided }
                        .padding(horizontal = Gap.m, vertical = Gap.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr("Решено · ${decided.size}", "Decided · ${decided.size}"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(Gap.xs))
                    Icon(painterResource(if (showDecided) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (showDecided) {
                val shown = decided.take(30)
                shown.forEachIndexed { i, wish ->
                    item(key = "done-${wish.id}") {
                        SwipeToRemove(i, shown.size, Modifier.animateItem(placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec()), onRemove = { onDeleteWish(wish) }) {
                            GroupRow(
                                i, shown.size,
                                supporting = { Text(if (wish.status == WishStatus.BOUGHT) tr("купил", "bought") else tr("не стал покупать", "skipped")) },
                                trailing = { Text(Fmt.amount(wish.amountMinor, wish.currency), style = MaterialTheme.typography.titleMedium.merge(Tnum)) },
                            ) { Text(wish.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}

/** "3 % цели": what the wish would take of the main goal, in whole percent. */
private fun goalShare(wish: Wish, goal: Goal, data: AppData): String {
    val target = Goals.rubOf(goal.targetMinor, goal.currency, data.rates)
    val share = if (target > 0) Goals.rubOf(wish.amountMinor, wish.currency, data.rates).toDouble() / target else 0.0
    return wholePercent(share) + tr(" цели", " of the goal")
}

/** "через 2 дня" or "пора решать". */
private fun left(wish: Wish, now: Long): String {
    val hoursLeft = (wish.decideAt - now + 3_599_999) / 3_600_000
    return if (hoursLeft > 0) tr("через ", "in ") + Goals.waitLabel(hoursLeft) else tr("пора решать", "time to decide")
}

/**
 * "+13 700 ₽ отказами": what "Не беру" has put towards the goals so far. Shown from the start
 * ("0 ₽ отказами"), so the mechanic is visible before it is first used.
 */
@Composable
private fun SkippedLine(skippedRub: Long, base: Base, color: Color) {
    val amount = (if (skippedRub > 0) "+" else "") + base.approx(skippedRub)
    Text(
        tr("$amount отказами", "$amount from what you skipped"),
        style = MaterialTheme.typography.bodyMedium.merge(Tnum),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The main goal as the hero: its name, what is saved as the big number, the wavy bar and
 * "62 % из 1 500 $". Reached, the bar is full and its wave settles (with one click of haptics the
 * first time), and the only "Купить" on the screen appears. The tile itself opens the goal.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainGoalTile(data: AppData, main: Goal, skippedRub: Long, onEdit: () -> Unit, onBuy: (Goal) -> Unit, onCelebrated: (Long) -> Unit) {
    val progress = Goals.progressMinor(main, data.states, data.rates)
    val reached = progress >= main.targetMinor
    var buying by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onPrimaryFixed
    val quiet = scheme.onPrimaryFixedVariant
    val celebrate = reached && data.settings.celebratedGoalId != main.id
    var settled by remember(main.id) { mutableStateOf(!celebrate) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(celebrate) {
        if (celebrate) {
            delay(400)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            settled = true
            onCelebrated(main.id)
        }
    }
    HeroTile(Modifier.padding(horizontal = Gap.m), onClick = onEdit) {
        Caption(main.name, color = quiet)
        VSpace(Gap.xs)
        val saved = Fmt.amount(progress, main.currency)
        BigNumber(saved, progress, ink, quiet = centsOf(saved), quietColor = ink.copy(alpha = 0.55f))
        VSpace(Gap.m)
        WavyBar(Goals.share(progress, main.targetMinor), ink = quiet, hero = true, flat = reached && settled)
        VSpace(Gap.s)
        Text(
            "${Goals.percentRounded(progress, main.targetMinor)} % ${tr("из", "of")} ${Fmt.amount(main.targetMinor, main.currency)}",
            style = MaterialTheme.typography.bodyMedium.merge(Tnum),
            color = quiet,
            maxLines = 1,
        )
        SkippedLine(skippedRub, data.base, quiet)
        if (reached) {
            VSpace(Gap.l)
            Button(
                onClick = { buying = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                colors = ButtonDefaults.buttonColors(containerColor = actionColor(), contentColor = onActionColor()),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Text(
                    tr("Купить ", "Buy ") + main.name + " · " + Fmt.amount(main.targetMinor, main.currency),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (buying) {
        // The same account the purchase will come from, named up front: the one the goal is saved on
        // when it holds enough, else the usual one.
        val from = VoiceMapper.buyGoal(main, data.states, data.accounts.sortedBy { it.sort }, data.settings, data.rates, System.currentTimeMillis())?.let { data.accountById[it.accountId] }
        val amount = Fmt.amount(main.targetMinor, main.currency)
        AlertDialog(
            onDismissRequest = { buying = false },
            title = { Text(tr("Купить «${main.name}»?", "Buy “${main.name}”?")) },
            text = {
                Text(
                    if (from == null) tr("Нет подходящего счёта.", "No suitable account.")
                    else tr("Расход $amount с «${from.name}». Цель закроется.", "$amount spent from “${from.name}”. The goal closes."),
                )
            },
            confirmButton = { TextButton(enabled = from != null, onClick = { buying = false; onBuy(main) }) { Text(tr("Купить", "Buy")) } },
            dismissButton = { TextButton(onClick = { buying = false }) { Text(tr("Отмена", "Cancel")) } },
        )
    }
}

/** Another goal as a row: the name, "62 % · из 1 500 $" over a thin wavy bar, and what is saved. Tap to edit. */
@Composable
private fun GoalRow(data: AppData, goal: Goal, index: Int, count: Int, onClick: () -> Unit) {
    val progress = Goals.progressMinor(goal, data.states, data.rates)
    val saved = Fmt.amount(progress, goal.currency)
    GroupRow(
        index, count, Modifier.padding(horizontal = Gap.m),
        onClick = onClick,
        supporting = {
            Column {
                Text(
                    "${Goals.percentRounded(progress, goal.targetMinor)} % · ${tr("из", "of")} ${Fmt.amount(goal.targetMinor, goal.currency)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                VSpace(Gap.s)
                WavyBar(Goals.share(progress, goal.targetMinor))
            }
        },
        trailing = { RollingNumber(saved, progress, MaterialTheme.typography.headlineSmall, quiet = centsOf(saved)) },
    ) { Text(goal.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** A grouped row that leaves with a swipe to the left. */
@Composable
private fun SwipeToRemove(index: Int, count: Int, modifier: Modifier, onRemove: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier = modifier.padding(horizontal = Gap.m),
        enableDismissFromStartToEnd = false,
        onDismiss = { onRemove() },
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    Modifier.fillMaxSize().padding(bottom = if (index < count - 1) GroupGap else 0.dp)
                        .clip(groupShape(index, count)).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = Gap.l),
                    contentAlignment = Alignment.CenterEnd,
                ) { Icon(painterResource(R.drawable.ic_delete), tr("Убрать", "Remove"), tint = MaterialTheme.colorScheme.onErrorContainer) }
            }
        },
    ) { content() }
}

/**
 * A goal: the name big and bare, the target as the hero number with its currency under it, and
 * where it is saved as a pill. A star in the header makes it the main one; ⋮ deletes it.
 */
@Composable
fun GoalSheet(data: AppData, editing: Goal?, onDismiss: () -> Unit, onSave: (Goal) -> Unit, onDelete: (Goal) -> Unit) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var currency by remember { mutableStateOf(editing?.currency ?: data.settings.displayCurrencies.firstOrNull { it != "RUB" } ?: "RUB") }
    var targetText by remember { mutableStateOf(editing?.let { Fmt.editable(it.targetMinor, it.currency) }.orEmpty()) }
    var savedText by remember { mutableStateOf(editing?.takeIf { it.savedMinor > 0 }?.let { Fmt.editable(it.savedMinor, it.currency) }.orEmpty()) }
    var accountId by remember { mutableStateOf(editing?.accountId) }
    var isMain by remember { mutableStateOf(editing?.isMain ?: data.goals.none { it.isMain }) }
    val target = Fmt.parseMinor(targetText, currency)?.takeIf { it > 0 }
    val saved = if (savedText.isBlank()) 0L else Fmt.parseMinor(savedText, currency)
    val valid = name.isNotBlank() && target != null && saved != null

    FormPage(
        onClose = onDismiss,
        title = if (editing == null) tr("Новая цель", "New goal") else tr("Цель", "Goal"),
        topActions = {
            IconToggleButton(checked = isMain, onCheckedChange = { isMain = it }) {
                Icon(painterResource(if (isMain) R.drawable.ic_star_fill else R.drawable.ic_star), if (isMain) tr("Главная цель", "Main goal") else tr("Сделать главной", "Make it the main goal"))
            }
            if (editing != null) DeleteAction { onDelete(editing) }
        },
        actions = {
            PrimaryAction(if (editing == null) tr("Добавить", "Add") else tr("Сохранить", "Save"), {
                onSave(
                    Goal(
                        id = editing?.id ?: 0, name = name.trim(), targetMinor = target!!, currency = currency,
                        accountId = accountId, savedMinor = saved!!, isMain = isMain,
                    ),
                )
            }, valid)
        },
    ) {
        BareField(name, { name = it }, tr("На что копишь", "What it is for"))
        VSpace(Gap.l)
        HeroAmountField(targetText, { targetText = it }, Currencies.symbol(currency))
        VSpace(Gap.m)
        CurrencyPicker(data.currencyChoices(currency), currency, { currency = it }, Modifier.align(Alignment.CenterHorizontally))
        VSpace(Gap.l)
        var open by remember { mutableStateOf(false) }
        Box {
            MetaPill(
                tr("Копится на: ", "Saved on: ") + (accountId?.let { data.accountById[it]?.name } ?: tr("без счёта", "no account")),
                onClick = { open = true },
                painter = painterResource(R.drawable.ic_wallet),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(tr("Без счёта", "No account")) }, onClick = { accountId = null; open = false })
                data.accounts.forEach { a ->
                    DropdownMenuItem(text = { Text(a.name) }, onClick = { accountId = a.id; open = false })
                }
            }
        }
        // Without an account, the progress is what is put aside by hand (and what refusals add).
        if (accountId == null) {
            VSpace(Gap.l)
            Caption(tr("Уже отложено", "Already put aside"))
            VSpace(Gap.xs)
            BareField(savedText, { savedText = it }, "0", style = MaterialTheme.typography.headlineSmall.merge(Tnum), suffix = Currencies.symbol(currency), keyboardType = KeyboardType.Decimal)
        }
    }
}
