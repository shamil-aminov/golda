package sh.aminov.golda.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sh.aminov.golda.domain.VoiceAction
import sh.aminov.golda.domain.VoiceMapper
import sh.aminov.golda.domain.VoicePrompt
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import sh.aminov.golda.domain.AccountState
import sh.aminov.golda.domain.Envelopes
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.CategoryMerge
import sh.aminov.golda.domain.Draft
import sh.aminov.golda.domain.Period
import sh.aminov.golda.domain.ReviewPrompt
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.OpTags
import sh.aminov.golda.domain.Rates
import sh.aminov.golda.domain.tr

sealed interface VoiceOutcome {
    /** [recorded] pairs each saved operation id with what was saved. */
    data class Done(
        val transcript: String,
        val recorded: List<Pair<Long, Draft>>,
        val misunderstood: Boolean,
        val late: Boolean,
        /** What is left for today after the last expense. */
        val comment: String? = null,
        /** What "Отменить" puts back. */
        val undo: Undo = Undo(recorded.map { it.first }, 0.0, null),
    ) : VoiceOutcome

    /** Kept for later: no network, no key yet, or Gemini busy. */
    data class Waiting(val reason: String) : VoiceOutcome

    /** Not understood and set aside (Settings → Голос), or lost. */
    data class Failed(val reason: String) : VoiceOutcome
}

/** What an undo puts back besides removing the operations: the settings a save may have changed. */
data class Undo(val ids: List<Long>, val markup: Double, val lastAccountId: Long?)

/**
 * Everything the app stores. [db] and [settings] are the real ones by default; instrumented tests
 * pass an in-memory database and a separate settings file, so they never touch real data.
 */
class Repo(
    private val context: Context,
    private val db: GoldaDb = GoldaDb.open(context),
    val settings: SettingsStore = SettingsStore(context),
    /** Gemini; the instrumented tests put a fake here. */
    var voiceParser: VoiceParser = GeminiParser(settings),
) {
    private val dao = db.dao()

    val accounts = dao.accounts()
    val operations = dao.operations()
    val categories = dao.categories()
    val rates = dao.rates()
    val obligations = dao.obligations()
    val backups = Backups(db, settings)

    suspend fun saveObligation(obligation: Obligation) = write { dao.upsertObligation(obligation) }

    suspend fun deleteObligation(id: Long) = write { dao.deleteObligation(id) }

    /** The regular payments as they are now, for the daily reminder. */
    suspend fun obligationsNow(): List<Obligation> = dao.obligationsNow()

    /**
     * The category for a purchase typed by hand: the one the same note had last time, else what
     * Gemini says, else null (no key, no network, or no answer that names one). Never throws.
     */
    suspend fun suggestCategory(note: String, kind: CategoryKind): Long? {
        val text = note.trim()
        if (text.isEmpty()) return null
        val categories = dao.categoriesNow().filter { it.kind == kind }
        VoiceMapper.history(dao.operationsSince(0))[text.lowercase()]?.takeIf { id -> categories.any { it.id == id } }?.let { return it }
        val key = settings.geminiKey() ?: return null
        val model = settings.flow.first().geminiModel
        val found = runCatching { Gemini.categorize(text, categories, key, model) }.getOrNull() ?: return null
        return categories.firstOrNull { it.key == found }?.id
    }

    /**
     * A purchase saved before its category was worked out gets it now, if it still has none: the
     * form closes at once and the category follows, the way a voice note's does.
     */
    suspend fun categorizeLater(operationId: Long) {
        val full = dao.operation(operationId) ?: return
        if (full.op.categoryId != null || full.op.note.isBlank()) return
        val kind = if (full.op.type == OpType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        val id = suggestCategory(full.op.note, kind) ?: return
        write { dao.operation(operationId)?.takeIf { it.op.categoryId == null }?.let { dao.updateOperation(it.op.copy(categoryId = id)) } }
    }

    /** Notes Gemini was already asked about in this run, so a failing one is not asked again and again. */
    private val tagAsked = mutableSetOf<String>()

    /**
     * Puts Gemini's precise tag on purchases and income that have none: the tag of an earlier one
     * with the same note first, then the rest in one request. Quietly does nothing without a key or
     * network.
     */
    suspend fun tagMissing() {
        val all = dao.operationsSince(0)
        val untagged = all.filter { OpTags.wanted(it.op) }
        if (untagged.isEmpty()) return
        val known = OpTags.known(all)
        val fromHistory = untagged.mapNotNull { full -> known[OpTags.key(full.op.note)]?.let { full.op.id to it } }
        if (fromHistory.isNotEmpty()) write { fromHistory.forEach { (id, tag) -> setTag(id, tag) } }
        val key = settings.geminiKey() ?: return
        val notes = untagged.map { OpTags.key(it.op.note) }.filter { it !in known && it !in tagAsked }.distinct().take(OpTags.BATCH)
        if (notes.isEmpty()) return
        tagAsked += notes
        val tags = runCatching { Gemini.tag(notes, key, settings.flow.first().geminiModel) }.getOrNull() ?: return
        write { untagged.forEach { full -> tags[OpTags.key(full.op.note)]?.let { setTag(full.op.id, it) } } }
    }

    /** Sets [tag] on an operation that still has none and still has the same note. */
    private suspend fun setTag(id: Long, tag: String) {
        dao.operation(id)?.op?.takeIf { it.tag == null }?.let { dao.updateOperation(it.copy(tag = tag)) }
    }

    val reviews = dao.reviews()

    suspend fun deleteReview(id: Long) = write { dao.deleteReview(id) }

    /**
     * Asks Gemini for a review of [period] and keeps it. The figures are worked out here
     * ([ReviewPrompt]); the reply is stored as it comes, Markdown. Returns the review's id, or what
     * went wrong in words: no key, no network, Gemini refused.
     */
    suspend fun review(period: Period): Result<Long> {
        val key = settings.geminiKey() ?: return Result.failure(IllegalStateException(tr("Нужен ключ Gemini в настройках", "Add a Gemini key in settings")))
        val s = settings.flow.first()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val data = ReviewPrompt.data(
            period, dao.operationsSince(0), dao.accountsNow().associateBy { it.id }, dao.categoriesNow().associateBy { it.id },
            ratesNow(), today, zone,
        )
        val text = try {
            Gemini.write(ReviewPrompt.system, data, key, s.geminiModel)
        } catch (e: GeminiException) {
            return Result.failure(IllegalStateException(e.message ?: tr("Gemini не ответил", "Gemini did not answer")))
        }
        val review = Review(fromDay = period.from.toEpochDay(), toDay = period.to.toEpochDay(), createdAt = System.currentTimeMillis(), text = text)
        return Result.success(write { dao.insertReview(review) })
    }

    /** A category with its bucket changed in settings. */
    suspend fun saveCategory(category: Category) = write { dao.updateCategory(category) }

    /**
     * Writes finish even when the screen that started them goes away (wiping
     * the data swaps the whole UI for onboarding mid-way, for instance).
     */
    private suspend fun <T> write(block: suspend () -> T): T = withContext(NonCancellable) { block() }

    suspend fun ensureSeed() = write {
        if (dao.categoryCount() == 0) dao.insertCategories(DefaultCategories.all)
        mergeCategories()
        if (dao.ratesNow().isEmpty()) dao.upsertRates(Cbr.fallback)
        repairValuations()
    }

    /**
     * Brings the expense categories to the twelve of [DefaultCategories]: one of an earlier version
     * ([CategoryMerge]) takes over the row of the new one it goes into, or moves its operations there
     * and is removed; a default missing is added; a default keeps its own name and order. Categories
     * of your own stay as they are.
     */
    private suspend fun mergeCategories() {
        val defaults = DefaultCategories.all.filter { it.kind == CategoryKind.EXPENSE }
        var all = dao.categoriesNow()
        for (wanted in defaults) {
            if (all.any { it.key == wanted.key }) continue
            val old = all.firstOrNull { CategoryMerge.into[it.key] == wanted.key }
            if (old != null) dao.updateCategory(old.copy(key = wanted.key, name = wanted.name, emoji = wanted.emoji, sort = wanted.sort, bucket = null))
            else dao.insertCategory(wanted)
            all = dao.categoriesNow()
        }
        val byKey = all.associateBy { it.key }
        for (old in all) {
            val into = CategoryMerge.into[old.key] ?: continue
            val to = byKey.getValue(into).id
            dao.moveOperations(old.id, to)
            dao.deleteCategory(old.id)
        }
        // The defaults in their order and under their current name: a migrated row keeps its id but
        // not its old place, and a name the default once had gives way to the new one.
        for (wanted in defaults) {
            val row = byKey[wanted.key] ?: continue
            val name = if (row.name in CategoryMerge.formerNames[wanted.key].orEmpty()) wanted.name else row.name
            if (row.sort != wanted.sort || row.name != name) dao.updateCategory(row.copy(sort = wanted.sort, name = name))
        }
    }

    /** True when fresh rates arrived. */
    suspend fun refreshRates(): Boolean {
        val fresh = runCatching { Cbr.fetch() }.getOrNull() ?: return false
        write {
            dao.upsertRates(fresh)
            repairValuations()
        }
        return true
    }

    /**
     * A posting written while its currency had no rate got valued at 0 ₽;
     * once a rate exists it is valued at today's rate instead.
     */
    private suspend fun repairValuations() {
        val rates = ratesNow()
        val accounts = dao.accountsNow().associateBy { it.id }
        for (posting in dao.postingsNow()) {
            val currency = accounts[posting.accountId]?.currency ?: continue
            if (currency == "RUB" || posting.rubMinor != 0L || posting.amountMinor == 0L) continue
            rates.rubMinor(posting.amountMinor, currency)?.let { dao.updatePosting(posting.copy(rubMinor = it)) }
        }
    }

    private suspend fun ratesNow(): Rates =
        Rates(dao.ratesNow().associate { it.code to it.rubPerUnit }, settings.flow.first().markup)

    suspend fun saveAccount(account: Account, openingMinor: Long?): Long = write {
        if (account.id != 0L) {
            dao.updateAccount(account)
            return@write account.id
        }
        val id = dao.insertAccount(account)
        if (openingMinor != null && openingMinor != 0L) {
            save(Draft(OpType.OPENING, System.currentTimeMillis(), id, openingMinor))
        }
        id
    }

    suspend fun deleteAccount(id: Long) = write {
        dao.deleteOperationsOf(id)
        dao.deleteAccount(id)
    }

    /**
     * Records [draft] (or replaces the operation with its id) and returns the operation id; null when
     * an account it names is gone (deleted while a form or an undo was still open).
     *
     * Editing keeps what was fixed when the operation was recorded (SPEC §3): if the same money moves
     * between the same accounts, the postings keep their ruble values; if the currencies are the same,
     * the CBR rates of the day stay. Only a new, recent transfer teaches the markup.
     */
    suspend fun save(draft: Draft): Long? = write {
        val states = Ledger.states(dao.accountsNow(), dao.postingsNow(except = draft.id))
        if (draft.accountId !in states) return@write null
        if (draft.type == OpType.TRANSFER && draft.toAccountId !in states) return@write null
        val rates = ratesNow()
        val old = if (draft.id != 0L) dao.operation(draft.id) else null
        val fresh = Ledger.postings(draft, states, rates)
        fun moves(list: List<Posting>) = list.map { it.accountId to it.amountMinor }.sortedWith(compareBy({ it.first }, { it.second }))
        val postings = if (old != null && moves(old.postings) == moves(fresh)) old.postings.map { it.copy(id = 0) } else fresh
        val cbr = if (old?.op?.cbrFrom != null && old.op.cbrTo != null && currencies(old.postings) == currencies(fresh)) {
            old.op.cbrFrom to old.op.cbrTo
        } else {
            exchangeRates(draft, states, rates)
        }
        val op = Operation(
            id = draft.id,
            type = draft.type,
            timestamp = draft.timestamp,
            categoryId = draft.categoryId,
            note = draft.note.trim(),
            voiceText = draft.voiceText,
            purchaseAmountMinor = draft.purchaseAmountMinor,
            purchaseCurrency = draft.purchaseCurrency,
            isEstimate = draft.isEstimate,
            bucket = draft.bucket.takeIf { draft.type == OpType.EXPENSE },
            // The tag stays while the note does; a new note is tagged again.
            tag = old?.op?.tag?.takeIf { OpTags.key(old.op.note) == OpTags.key(draft.note) },
            cbrFrom = cbr?.first,
            cbrTo = cbr?.second,
        )
        val id = dao.saveOperation(op, postings)
        val recent = System.currentTimeMillis() - draft.timestamp < 7 * 24 * 3_600_000L
        val learned = if (draft.id == 0L && recent) Ledger.learnedMarkup(draft, states, rates) else null
        settings.update { s ->
            val withMarkup = if (learned != null) s.copy(markup = learned) else s
            if (draft.type == OpType.EXPENSE) withMarkup.copy(lastAccountId = draft.accountId) else withMarkup
        }
        id
    }

    /** The currencies money leaves and arrives in, by the accounts of [postings]. */
    private suspend fun currencies(postings: List<Posting>): Pair<String?, String?> {
        val byId = dao.accountsNow().associateBy { it.id }
        return byId[postings.firstOrNull { it.amountMinor < 0 }?.accountId]?.currency to
            byId[postings.firstOrNull { it.amountMinor > 0 }?.accountId]?.currency
    }

    /** Official rates of both currencies of a transfer that changes currency. */
    private fun exchangeRates(draft: Draft, states: Map<Long, AccountState>, rates: Rates): Pair<Double, Double>? {
        if (draft.type != OpType.TRANSFER) return null
        val from = states[draft.accountId]?.currency ?: return null
        val to = states[draft.toAccountId ?: return null]?.currency ?: return null
        if (from == to) return null
        return (rates.official(from) ?: return null) to (rates.official(to) ?: return null)
    }

    /** Debts with their balances, for reminders. */
    suspend fun debtStates(): List<AccountState> =
        Ledger.states(dao.accountsNow(), dao.postingsNow()).values.filter { it.account.type == AccountType.CREDIT || it.account.type == AccountType.LOAN }

    suspend fun deleteOperation(id: Long) = write { dao.deleteOperation(id) }

    /** Undo of a delete. False when it cannot come back: its account was deleted meanwhile. */
    suspend fun restoreOperation(full: OperationFull): Boolean = write {
        val accounts = dao.accountsNow().map { it.id }.toSet()
        if (full.postings.any { it.accountId !in accounts }) return@write false
        runCatching { dao.restoreOperation(full.op, full.postings) }.isSuccess
    }

    /** The settings a save may change, to put back on undo. */
    suspend fun undoPoint(ids: List<Long>): Undo = settings.flow.first().let { Undo(ids, it.markup, it.lastAccountId) }

    /** Undo of a save: the operations go, and the markup and the usual account are what they were. */
    suspend fun undo(undo: Undo) = write {
        undo.ids.forEach { dao.deleteOperation(it) }
        settings.update { it.copy(markup = undo.markup, lastAccountId = undo.lastAccountId) }
    }

    private val voiceLock = Mutex()
    private val _voice = MutableSharedFlow<VoiceOutcome>(extraBufferCapacity = 16)

    /** Every understood (or postponed) voice note, for the UI to report. */
    val voice: SharedFlow<VoiceOutcome> = _voice

    private val _voiceBacklog = MutableStateFlow(0)

    /** Notes not booked yet: waiting in the queue or set aside after a failure (Settings → Голос). */
    val voiceBacklog: StateFlow<Int> = _voiceBacklog

    fun refreshVoiceBacklog() {
        _voiceBacklog.value = VoiceQueue.count(context)
    }

    /** Understands one recorded note and books what it says. The file is removed once understood. */
    suspend fun understand(file: File, late: Boolean = false): VoiceOutcome = voiceLock.withLock { understandLocked(file, late) }

    /**
     * Notes recorded offline, before the key was set, or while Gemini was busy. Stops at the first one
     * that has to wait again. Never throws: a note that cannot be handled is set aside, so no note can
     * break every start of the app.
     */
    suspend fun processVoiceQueue() {
        runCatching {
            voiceLock.withLock {
                for (file in VoiceQueue.pending(context)) {
                    if (VoiceQueue.fresh(file)) continue // still being recorded
                    if (understandLocked(file, late = true) is VoiceOutcome.Waiting) break
                }
            }
        }
        refreshVoiceBacklog()
    }

    /** "Повторить" in Settings: the notes set aside get another try, now. */
    suspend fun retryVoiceNotes() {
        voiceLock.withLock { VoiceQueue.requeue(context) }
        processVoiceQueue()
    }

    /** "Удалить" in Settings: the notes not booked yet are dropped. */
    suspend fun discardVoiceNotes() {
        voiceLock.withLock { VoiceQueue.clear(context) }
        refreshVoiceBacklog()
    }

    /**
     * The voice policy. No network or no key: the note waits in the queue and is tried at the next
     * start (or when a key is saved), for as long as it takes. Gemini busy or over quota (429, 5xx):
     * the same, but at most [VoiceQueue.MAX_ATTEMPTS] times. A refused request, a reply that cannot
     * be read, or anything unexpected: the note is set aside at once and said so; it is tried again
     * only from Settings → Голос, where it can also be deleted. So a failed note is never booked
     * behind the user's back after they entered it by hand, and never retried forever.
     */
    private suspend fun understandLocked(file: File, late: Boolean): VoiceOutcome {
        if (!file.exists()) return VoiceOutcome.Failed(tr("запись потерялась", "the note got lost"))
        val aside = tr(" Запись ждёт в настройках голоса", " The note waits in the voice settings")
        val outcome = try {
            write<VoiceOutcome> {
                val s = settings.flow.first()
                val accounts = dao.accountsNow().sortedBy { it.sort }
                val categories = dao.categoriesNow()
                val zone = ZoneId.systemDefault()
                val recordedAt = VoiceQueue.recordedAt(file)
                // "Вчера" means the day before the note was recorded, not before it is understood.
                val system = VoicePrompt.system(accounts, categories, s, Ledger.localDate(recordedAt, zone))
                val result = try {
                    voiceParser.parse(file, system, s.geminiModel)
                } catch (e: GeminiException) {
                    return@write when (e.kind) {
                        GeminiException.Kind.NO_KEY -> VoiceOutcome.Waiting(tr("Добавь ключ Gemini в настройках, запись сохранена", "Add a Gemini key in settings; the note is kept"))
                        GeminiException.Kind.OFFLINE -> VoiceOutcome.Waiting(tr("Нет связи, запись разберётся позже", "No connection; the note will be worked out later"))
                        GeminiException.Kind.BUSY -> if (VoiceQueue.attempts(file) + 1 < VoiceQueue.MAX_ATTEMPTS) {
                            VoiceQueue.countAttempt(file)
                            VoiceOutcome.Waiting(tr("Gemini не ответил, попробую при следующем запуске", "Gemini didn't answer; I'll try again at the next start"))
                        } else {
                            VoiceQueue.putAside(context, file)
                            VoiceOutcome.Failed("Gemini: ${e.message}." + aside)
                        }
                        GeminiException.Kind.REJECTED -> {
                            VoiceQueue.putAside(context, file)
                            VoiceOutcome.Failed("Gemini: ${e.message}." + aside)
                        }
                    }
                }
                val actions = dao.operationsSince(0).let { all -> VoiceMapper.actions(result, accounts, categories, s, ratesNow(), recordedAt, zone, VoiceMapper.history(all), VoiceMapper.spentFrom(all)) }
                val recorded = actions.filterIsInstance<VoiceAction.Record>().mapNotNull { action -> save(action.draft)?.let { it to action.draft } }
                file.delete()
                val comment = recorded.lastOrNull { it.second.type == OpType.EXPENSE }?.let { impact(it.first) }
                VoiceOutcome.Done(
                    comment = comment,
                    transcript = result.transcript,
                    recorded = recorded,
                    misunderstood = actions.any { it is VoiceAction.NotUnderstood },
                    late = late,
                    undo = Undo(recorded.map { it.first }, s.markup, s.lastAccountId),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (file.exists()) VoiceQueue.putAside(context, file)
            VoiceOutcome.Failed(tr("Запись не получилось разобрать.", "The note could not be processed.") + aside)
        }
        refreshVoiceBacklog()
        _voice.emit(outcome)
        return outcome
    }

    /**
     * Brings [accountId] to what the bank shows; a match records nothing but the time it was checked.
     * Less money than the records say is, when [asSpending], everyday spending that was never written
     * down (SPEC §5): an expense "Не записано" in the everyday bucket, so the pace is not flattered.
     * Otherwise, and for more money than recorded, the difference is a plain adjustment.
     */
    suspend fun reconcile(accountId: Long, actualMinor: Long, asSpending: Boolean = false) = write {
        val state = Ledger.states(dao.accountsNow(), dao.postingsNow()).getValue(accountId)
        val delta = actualMinor - state.balanceMinor
        val now = System.currentTimeMillis()
        when {
            delta == 0L -> Unit
            delta < 0 && asSpending -> {
                val other = dao.categoriesNow().firstOrNull { it.key == "other" && it.kind == CategoryKind.EXPENSE }
                save(Draft(OpType.EXPENSE, now, accountId, -delta, categoryId = other?.id, note = tr("Не записано", "Not recorded"), bucket = Bucket.DAILY))
            }
            else -> save(Draft(OpType.ADJUSTMENT, now, accountId, delta))
        }
        settings.update { it.copy(reconciledAt = it.reconciledAt + (accountId to now)) }
    }

    /** What is left for today on the spending card after an expense just recorded from it: "на сегодня осталось 56 ₾". */
    suspend fun impact(operationId: Long): String? {
        val full = dao.operation(operationId)?.takeIf { it.op.type == OpType.EXPENSE } ?: return null
        val zone = ZoneId.systemDefault()
        val envelope = Envelopes.current(dao.accountsNow(), dao.operationsSince(0), settings.flow.first(), LocalDate.now(zone), zone) ?: return null
        if (full.postings.none { it.accountId == envelope.account.id }) return null
        val left = envelope.leftTodayMinor
        return if (left >= 0) tr("на сегодня осталось ", "left for today ") + Fmt.amount(left, envelope.currency)
        else tr("перерасход ", "over by ") + Fmt.amount(-left, envelope.currency)
    }

    /**
     * A restore: the backup's data, then its own reconcile-reminder setting.
     * Throws when the file does not fit, and nothing changes then.
     */
    suspend fun importBackup(text: String): Backup = write {
        try {
            backups.import(text).also { mergeCategories() }
        } finally {
            ReconcileSchedule.apply(context, settings.flow.first().reconcileReminder)
        }
    }

    suspend fun resetAll() = write {
        // Nothing of the old data may come back: no notes booked into the new data.
        VoiceQueue.wipe(context)
        refreshVoiceBacklog()
        withContext(Dispatchers.IO) { db.clearAllTables() }
        ensureSeed()
        // Last: clearing settings is what sends the UI back to onboarding.
        settings.clear()
    }
}

object DefaultCategories {
    val all = listOf(
        Category(key = "food", name = "Еда", emoji = "🍔", kind = CategoryKind.EXPENSE, sort = 0),
        Category(key = "transport", name = "Транспорт", emoji = "🚕", kind = CategoryKind.EXPENSE, sort = 1),
        Category(key = "household", name = "Бытовое", emoji = "🧻", kind = CategoryKind.EXPENSE, sort = 2),
        Category(key = "treats", name = "Радости", emoji = "🍺", kind = CategoryKind.EXPENSE, sort = 3),
        Category(key = "housing", name = "Жильё", emoji = "🏠", kind = CategoryKind.EXPENSE, sort = 4),
        Category(key = "telecom", name = "Связь", emoji = "📱", kind = CategoryKind.EXPENSE, sort = 5),
        Category(key = "subscriptions", name = "Подписки", emoji = "🔁", kind = CategoryKind.EXPENSE, sort = 6),
        Category(key = "loans", name = "Кредиты", emoji = "🏦", kind = CategoryKind.EXPENSE, sort = 7),
        Category(key = "shopping", name = "Покупки", emoji = "🛍️", kind = CategoryKind.EXPENSE, sort = 8),
        Category(key = "travel", name = "Поездки", emoji = "✈️", kind = CategoryKind.EXPENSE, sort = 9),
        Category(key = "health", name = "Здоровье", emoji = "💊", kind = CategoryKind.EXPENSE, sort = 10),
        Category(key = "other", name = "Прочее", emoji = "📦", kind = CategoryKind.EXPENSE, sort = 11),
        Category(key = "salary", name = "Зарплата", emoji = "💼", kind = CategoryKind.INCOME, sort = 0),
        Category(key = "interest", name = "Проценты", emoji = "🏦", kind = CategoryKind.INCOME, sort = 1),
        Category(key = "gift", name = "Подарок", emoji = "🎁", kind = CategoryKind.INCOME, sort = 2),
        Category(key = "other_income", name = "Прочее", emoji = "📦", kind = CategoryKind.INCOME, sort = 3),
    )
}
