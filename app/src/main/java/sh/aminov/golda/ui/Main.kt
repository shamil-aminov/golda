package sh.aminov.golda.ui

import android.os.Build
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerSnapDistance
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.aminov.golda.R
import sh.aminov.golda.TAB_ACCOUNTS
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.Goal
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Repo
import sh.aminov.golda.data.Undo
import sh.aminov.golda.data.Wish
import sh.aminov.golda.data.WishStatus
import sh.aminov.golda.domain.Draft
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.VoiceAction
import sh.aminov.golda.domain.label
import sh.aminov.golda.domain.plural
import sh.aminov.golda.domain.tr

private sealed interface Page {
    data object Settings : Page
    data class AccountPage(val id: Long) : Page
}

/** A page survives the activity being recreated (a language switch): Settings as 0, an account by its id. */
private val PageSaver = Saver<Page?, Long>(
    save = { page -> when (page) { Page.Settings -> 0L; is Page.AccountPage -> page.id; null -> -1L } },
    restore = { value -> when { value == 0L -> Page.Settings; value > 0 -> Page.AccountPage(value); else -> null } },
)

private sealed interface Sheet {
    data class Entry(val request: EntryRequest) : Sheet
    data class Acc(val editing: Account?) : Sheet
    data class GoalEdit(val editing: Goal?) : Sheet
}

@Composable
fun GoldaRoot(repo: Repo, voiceRequests: ReceiveChannel<Unit>, tabRequests: ReceiveChannel<Int>, wishRequests: ReceiveChannel<Long>) {
    val settings by repo.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val accounts by repo.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val operations by repo.operations.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by repo.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val rates by repo.rates.collectAsStateWithLifecycle(initialValue = emptyList())
    val obligations by repo.obligations.collectAsStateWithLifecycle(initialValue = emptyList())
    val goals by repo.wishes.goals.collectAsStateWithLifecycle(initialValue = emptyList())
    val wishes by repo.wishes.wishes.collectAsStateWithLifecycle(initialValue = emptyList())
    // Read before the early return, so the ticker keeps running from the first frame.
    val today = rememberToday()
    val s = settings ?: return
    val data = remember(s, accounts, operations, categories, rates, obligations, goals, wishes, today) {
        AppData(s, accounts, operations, categories, rates, obligations, goals, wishes, today)
    }
    val scope = rememberCoroutineScope()
    val change: ((Settings) -> Settings) -> Unit = { f -> scope.launch { repo.settings.update(f) } }

    if (!s.onboarded) {
        Onboarding(
            data = data,
            onChange = change,
            onSaveAccount = { account, opening -> scope.launch { repo.saveAccount(account, opening) } },
            onDeleteAccount = { id -> scope.launch { repo.deleteAccount(id) } },
        )
    } else {
        MainScreen(repo, data, change, voiceRequests, tabRequests, wishRequests)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainScreen(
    repo: Repo,
    data: AppData,
    change: ((Settings) -> Settings) -> Unit,
    voiceRequests: ReceiveChannel<Unit>,
    tabRequests: ReceiveChannel<Int>,
    wishRequests: ReceiveChannel<Long>,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable(stateSaver = PageSaver) { mutableStateOf<Page?>(null) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    BackHandler(enabled = page != null) { page = null }
    // "Хочу купить X" said aloud opens the expense sheet already in "Сомневаюсь"; several of them in
    // one note open one after another, each when the one before is closed.
    val considerQueue = remember { mutableStateListOf<VoiceAction.Consider>() }
    LaunchedEffect(sheet, considerQueue.size) {
        if (sheet == null && considerQueue.isNotEmpty()) sheet = Sheet.Entry(EntryRequest(consider = considerQueue.removeAt(0)))
    }
    val voice = rememberVoice(repo, data, snackbar, voiceRequests) { considerQueue.addAll(it) }
    val voiceBacklog by repo.voiceBacklog.collectAsStateWithLifecycle()
    // Only the Sunday reminder opens Accounts from outside; then each row offers "Сходится" outright.
    var reconciling by remember { mutableStateOf(false) }
    val homeList = rememberLazyListState()
    // The tabs sit side by side and follow the finger; the toolbar and [tab] stay in step with them.
    // The pager is the one source of truth: [tab] only remembers where it settled, for a restart. A
    // tap or a request scrolls the pager directly, never through [tab]: an effect keyed on [tab]
    // would fire mid-swipe and pull the page back against the finger.
    val pager = rememberPagerState(initialPage = tab) { 4 }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { settled ->
            tab = settled
            if (settled != TAB_ACCOUNTS) reconciling = false
        }
    }
    LaunchedEffect(tabRequests) {
        for (request in tabRequests) {
            page = null
            reconciling = request == TAB_ACCOUNTS
            pager.animateScrollToPage(request)
        }
    }
    // A wish's reminder opens that wish in "Сомневаюсь", if it is still waiting.
    LaunchedEffect(wishRequests) {
        for (id in wishRequests) {
            val wish = repo.wishes.wish(id)?.takeIf { it.status == WishStatus.WAITING } ?: continue
            page = null
            sheet = Sheet.Entry(EntryRequest(consider = VoiceAction.Consider(wish.title, wish.amountMinor, wish.currency), wishId = wish.id))
        }
    }
    // Back on another tab goes Home first; only Home's back leaves the app.
    BackHandler(enabled = page == null && sheet == null && pager.currentPage != 0) {
        scope.launch { pager.animateScrollToPage(0) }
    }

    /**
     * "Шаурма · 15 ₾" plus what it cost in work and what is left today, with undo. The undo puts back
     * all a save did: the markup it learned, the usual account, and a wish it marked bought.
     */
    suspend fun announce(id: Long, draft: Draft, comment: String?, undo: Undo, wish: Wish?) {
        val account = data.accountById[draft.accountId]
        val code = draft.purchaseCurrency ?: account?.currency ?: "RUB"
        val shown = draft.purchaseAmountMinor ?: draft.amountMinor
        val title = draft.note.ifBlank { draft.categoryId?.let { data.categoryById[it]?.label() } ?: tr("Записано", "Saved") }
        val result = snackbar.showSnackbar(
            message = "$title · ${Fmt.amount(shown, code)}" + (comment?.let { "\n$it" } ?: ""),
            actionLabel = tr("Отменить", "Undo"),
            duration = if (comment != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            repo.undo(undo)
            wish?.let { repo.wishes.restoreWish(it) }
        }
    }

    fun save(draft: Draft, wishId: Long?) {
        sheet = null
        scope.launch {
            val undo = repo.undoPoint(emptyList())
            val wish = wishId?.let { repo.wishes.wish(it) }
            val id = repo.save(draft)
            if (id == null) {
                snackbar.showSnackbar(tr("Счёт удалён — записать некуда", "The account is gone; nothing was saved"))
                return@launch
            }
            wishId?.let { repo.wishes.bought(it) }
            if (draft.id == 0L) {
                if (draft.type == OpType.EXPENSE) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                announce(id, draft, repo.wishes.impact(id), undo.copy(ids = listOf(id)), wish)
            }
        }
    }

    /** Gone at once, back with "Вернуть". */
    fun deleteOperation(full: OperationFull) {
        sheet = null
        scope.launch {
            repo.deleteOperation(full.op.id)
            val title = full.op.note.ifBlank { full.op.categoryId?.let { data.categoryById[it]?.label() } ?: tr("Запись", "Entry") }
            val result = snackbar.showSnackbar(tr("«$title» удалено", "“$title” deleted"), actionLabel = tr("Вернуть", "Undo"), duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed && !repo.restoreOperation(full)) {
                snackbar.showSnackbar(tr("Не вернуть: счёта больше нет", "Can't bring it back: the account is gone"))
            }
        }
    }

    val titles = listOf("Golda", tr("Счета", "Accounts"), tr("Цели", "Goals"), tr("Аналитика", "Insights"))

    // One window: the tabs or a page, and over them the form pages, so the keyboard and the insets
    // behave the same everywhere.
    Box(Modifier.fillMaxSize()) {
        when (val current = page) {
            Page.Settings -> SettingsScreen(
                data = data,
                onBack = { page = null },
                onChange = change,
                snackbar = snackbar,
                onRefreshRates = {
                    scope.launch {
                        val ok = repo.refreshRates()
                        snackbar.showSnackbar(if (ok) tr("Курсы обновлены", "Rates updated") else tr("Не получилось, нет сети?", "Didn't work. No network?"))
                    }
                },
                onWipe = { page = null; scope.launch { repo.resetAll() } },
                onSaveObligation = { scope.launch { repo.saveObligation(it) } },
                onDeleteObligation = { obligation ->
                    scope.launch {
                        repo.deleteObligation(obligation.id)
                        val result = snackbar.showSnackbar(tr("«${obligation.name}» удалён", "“${obligation.name}” deleted"), actionLabel = tr("Вернуть", "Undo"), duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) repo.saveObligation(obligation)
                    }
                },
                onExport = { uri ->
                    scope.launch {
                        val ok = runCatching {
                            val text = repo.backups.export()
                            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
                        }.isSuccess
                        snackbar.showSnackbar(if (ok) tr("Копия сохранена", "Backup saved") else tr("Не получилось сохранить файл", "Could not save the file"))
                    }
                },
                onImport = { uri ->
                    scope.launch {
                        val result = runCatching {
                            val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
                            repo.importBackup(requireNotNull(text))
                        }
                        snackbar.showSnackbar(
                            result.fold(
                                {
                                    val a = it.accounts.size.toLong()
                                    val o = it.operations.size.toLong()
                                    tr("Восстановлено: $a ${plural(a, "счёт", "счёта", "счетов", "account", "accounts")}, $o ${plural(o, "операция", "операции", "операций", "operation", "operations")}",
                                        "Restored: $a ${plural(a, "счёт", "счёта", "счетов", "account", "accounts")}, $o ${plural(o, "операция", "операции", "операций", "operation", "operations")}")
                                },
                                { tr("Файл не подошёл — ничего не изменилось", "That file did not fit; nothing changed") },
                            ),
                        )
                    }
                },
                onSaveKey = { key ->
                    scope.launch {
                        repo.settings.setGeminiKey(key)
                        snackbar.showSnackbar(if (key.isBlank()) tr("Ключ удалён", "Key removed") else tr("Ключ сохранён", "Key saved"))
                        // A new key is worth another try for the notes set aside, too.
                        if (key.isNotBlank()) repo.retryVoiceNotes()
                    }
                },
                voiceBacklog = voiceBacklog,
                onRetryVoice = { scope.launch { repo.retryVoiceNotes() } },
                onDiscardVoice = {
                    scope.launch {
                        repo.discardVoiceNotes()
                        snackbar.showSnackbar(tr("Записи удалены", "Notes deleted"))
                    }
                },
            )
            is Page.AccountPage -> AccountScreen(
                data = data,
                accountId = current.id,
                snackbar = snackbar,
                onBack = { page = null },
                onEditAccount = { sheet = Sheet.Acc(data.accountById[current.id]) },
                onEditOperation = { sheet = Sheet.Entry(EntryRequest(editing = it)) },
                onReconcile = { actual -> scope.launch { repo.reconcile(current.id, actual) } },
            )
            // No top bar on the tabs: the content starts under the status bar, and Settings sits in the Home tile.
            null -> Scaffold(
                // No bar across the screen: one floating toolbar, centred 16 dp above the gesture bar, with
                // the tabs, "+" and the mic. The same on every tab, so nothing moves when switching.
                floatingActionButton = {
                    val tabs = listOf(
                        ToolbarTab(R.drawable.ic_home, R.drawable.ic_home_fill, tr("Главная", "Home")),
                        ToolbarTab(R.drawable.ic_wallet, R.drawable.ic_wallet_fill, titles[1]),
                        ToolbarTab(R.drawable.ic_flag, R.drawable.ic_flag_fill, titles[2]),
                        ToolbarTab(R.drawable.ic_chart, R.drawable.ic_chart_fill, titles[3]),
                    )
                    GoldaToolbar(
                        tabs = tabs,
                        // Follows the swipe, not only where it settles.
                        selected = pager.targetPage,
                        position = { pager.currentPage + pager.currentPageOffsetFraction },
                        onSelect = { index -> reconciling = false; scope.launch { pager.animateScrollToPage(index) } },
                        voice = voice,
                        onAdd = { sheet = Sheet.Entry(EntryRequest()) },
                    )
                },
                floatingActionButtonPosition = FabPosition.Center,
                // The Scaffold puts snackbars above the pair.
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                HorizontalPager(
                state = pager,
                key = { it },
                // Like the launcher's home screens: the pages either side are already laid out, so a
                // swipe never waits for a page to be built.
                beyondViewportPageCount = 1,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pager,
                    // One page per swipe, however hard the flick.
                    pagerSnapDistance = PagerSnapDistance.atMost(1),
                    // A slow drag flips past a fifth of the width; a flick flips from any distance.
                    snapPositionalThreshold = 0.2f,
                    // The finger's speed carries into a firm settle with no overshoot.
                    snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f),
                ),
            ) { index ->
                    when (index) {
                        0 -> HomeScreen(data, padding, homeList, onSettings = { page = Page.Settings }) { sheet = Sheet.Entry(EntryRequest(editing = it)) }
                        1 -> AccountsScreen(
                            data, padding,
                            onOpen = { page = Page.AccountPage(it) },
                            onAdd = { sheet = Sheet.Acc(null) },
                            onReconcile = { id, actual -> scope.launch { repo.reconcile(id, actual) } },
                            reconcileMode = reconciling,
                            onAllReconciled = {
                                reconciling = false
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                scope.launch { snackbar.showSnackbar(tr("Все счета сверены", "All accounts reconciled")) }
                            },
                        )
                        2 -> GoalsScreen(
                            data = data,
                            padding = padding,
                            onEditGoal = { sheet = Sheet.GoalEdit(it) },
                            onCelebrated = { id -> change { it.copy(celebratedGoalId = id) } },
                            onBuyGoal = { goal ->
                                scope.launch {
                                    val (id, line) = repo.wishes.buyGoal(goal)
                                    if (id == null) {
                                        snackbar.showSnackbar(tr("Нет подходящего счёта", "No suitable account"))
                                    } else {
                                        repo.wishes.deleteGoal(goal.id)
                                        snackbar.showSnackbar(
                                            "${goal.name} · ${Fmt.amount(goal.targetMinor, goal.currency)}" + (line?.let { "\n$it" } ?: ""),
                                            duration = SnackbarDuration.Long,
                                        )
                                    }
                                }
                            },
                            // A waiting wish opens "Сомневаюсь" without "Подумаю": it has been thought about.
                            onDecide = { wish -> sheet = Sheet.Entry(EntryRequest(consider = VoiceAction.Consider(wish.title, wish.amountMinor, wish.currency), wishId = wish.id)) },
                            onDeleteWish = { wish ->
                                scope.launch {
                                    repo.wishes.deleteWish(wish.id)
                                    val result = snackbar.showSnackbar(
                                        tr("«${wish.title}» убрано", "“${wish.title}” removed"),
                                        actionLabel = tr("Вернуть", "Undo"),
                                        duration = SnackbarDuration.Long,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) repo.wishes.restoreWish(wish)
                                }
                            },
                        )
                        else -> AnalyticsScreen(data, padding)
                    }
                }
            }
        }

        FormPageHost(sheet) { current ->
            when (current) {
                is Sheet.Entry -> EntrySheet(
                    data = data,
                    request = current.request,
                    facts = { repo.wishes.consider(it) },
                    onDismiss = { sheet = null },
                    onSave = ::save,
                    onDelete = ::deleteOperation,
                    onSkip = { consider, wishId ->
                        sheet = null
                        scope.launch { snackbar.showSnackbar(repo.wishes.skip(consider, wishId), duration = SnackbarDuration.Long) }
                    },
                    onThink = { consider ->
                        sheet = null
                        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        scope.launch {
                            val wait = repo.wishes.think(consider)
                            snackbar.showSnackbar(tr("«${consider.title}» — решить через $wait", "“${consider.title}”: decide in $wait"))
                        }
                    },
                )
                is Sheet.Acc -> AccountSheet(
                    data = data,
                    editing = current.editing,
                    onDismiss = { sheet = null },
                    onSave = { account, opening -> sheet = null; scope.launch { repo.saveAccount(account, opening) } },
                    onDelete = { id ->
                        sheet = null
                        if ((page as? Page.AccountPage)?.id == id) page = null
                        scope.launch { repo.deleteAccount(id) }
                    },
                )
                is Sheet.GoalEdit -> GoalSheet(
                    data = data,
                    editing = current.editing,
                    onDismiss = { sheet = null },
                    onSave = { goal -> sheet = null; scope.launch { repo.wishes.saveGoal(goal) } },
                    onDelete = { goal ->
                        sheet = null
                        scope.launch {
                            repo.wishes.deleteGoal(goal.id)
                            val result = snackbar.showSnackbar(tr("Цель «${goal.name}» удалена", "Goal “${goal.name}” deleted"), actionLabel = tr("Вернуть", "Undo"), duration = SnackbarDuration.Long)
                            if (result == SnackbarResult.ActionPerformed) repo.wishes.saveGoal(goal)
                        }
                    },
                )
            }
        }
    }
}
