package sh.aminov.golda.data

import android.os.Build
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.first
import sh.aminov.golda.GoldaApplication
import sh.aminov.golda.MainActivity
import sh.aminov.golda.R
import sh.aminov.golda.domain.Base
import sh.aminov.golda.domain.Budget
import sh.aminov.golda.domain.Facts
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.Goals
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.Rates
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.Today
import sh.aminov.golda.domain.VoiceAction
import sh.aminov.golda.domain.VoiceMapper
import sh.aminov.golda.domain.tr
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import sh.aminov.golda.domain.Debts
import sh.aminov.golda.domain.plural
import sh.aminov.golda.domain.AccountState
import sh.aminov.golda.TAB_GOALS
import sh.aminov.golda.EXTRA_TAB
import sh.aminov.golda.EXTRA_WISH
import androidx.work.ExistingWorkPolicy

/** Goals and the wishlist: everything about "хочу купить". */
class Wishes(private val context: Context, private val repo: Repo, private val dao: GoldaDao) {
    val goals = dao.goals()
    val wishes = dao.wishes()

    companion object {
        const val REMINDER_TAG = "wish-reminder"
    }

    private class Snapshot(val settings: Settings, val rates: Rates, val today: Today, val mainGoal: Goal?)

    private suspend fun snapshot(): Snapshot {
        val settings = repo.settings.flow.first()
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone)
        val rates = Rates(dao.ratesNow().associate { it.code to it.rubPerUnit }, settings.markup)
        val accounts = dao.accountsNow()
        val states = Ledger.states(accounts, dao.postingsNow())
        // Today's spending, and a month back for the payments already made early.
        val recent = dao.operationsSince(day.minusMonths(1).minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        val budget = Budget.today(states, recent, settings, day, zone, dao.obligationsNow() + Debts.obligations(states.values), rates)
        return Snapshot(settings, rates, budget, dao.goalsNow().firstOrNull { it.isMain })
    }

    suspend fun saveGoal(goal: Goal) {
        val id = dao.upsertGoal(goal).takeIf { goal.id == 0L } ?: goal.id
        if (goal.isMain) dao.unsetMainExcept(id)
        val all = dao.goalsNow()
        if (all.none { it.isMain }) all.firstOrNull()?.let { dao.upsertGoal(it.copy(isMain = true)) }
    }

    suspend fun deleteGoal(id: Long) {
        dao.deleteGoal(id)
        val all = dao.goalsNow()
        if (all.none { it.isMain }) all.firstOrNull()?.let { dao.upsertGoal(it.copy(isMain = true)) }
    }

    /** What an expense just recorded costs in work and what is left for today: "≈ 2,6 ч работы · на сегодня осталось 503 ₽". */
    suspend fun impact(operationId: Long): String? {
        val full = dao.operation(operationId)?.takeIf { it.op.type == OpType.EXPENSE } ?: return null
        val snap = snapshot()
        val rub = -full.postings.sumOf { it.rubMinor }
        val hours = snap.settings.hourNet.takeIf { it > 0 }?.let { rub / 100.0 / it }
        val left = snap.today.leftTodayRub
        val base = Base.of(snap.settings, snap.rates)
        return listOfNotNull(
            hours?.let { "≈ ${Fmt.number(it, 1)} " + tr("ч работы", "h of work") },
            if (left >= 0) tr("на сегодня осталось ", "left for today ") + base.approx(left)
            else tr("перерасход ", "over budget by ") + base.approx(-left),
        ).joinToString(" · ")
    }

    /** The numbers the "хочу купить" card shows. */
    suspend fun consider(c: VoiceAction.Consider): Facts {
        val snap = snapshot()
        val rub = Goals.rubOf(c.amountMinor, c.currency, snap.rates)
        return Goals.facts(c.title, Fmt.amount(c.amountMinor, c.currency), rub, snap.settings, snap.today.perDayRub, snap.mainGoal, snap.rates)
            .copy(wait = Goals.waitLabel(Goals.waitHours(rub, snap.settings, YearMonth.now())))
    }

    /** "Подумаю": on the list, with a reminder when the time is up. Returns how long to wait. */
    suspend fun think(c: VoiceAction.Consider): String {
        val snap = snapshot()
        val now = System.currentTimeMillis()
        val hours = Goals.waitHours(Goals.rubOf(c.amountMinor, c.currency, snap.rates), snap.settings, YearMonth.now())
        val wish = Wish(title = c.title, amountMinor = c.amountMinor, currency = c.currency, createdAt = now, decideAt = now + hours * 3_600_000)
        remind(wish.copy(id = dao.upsertWish(wish)))
        return Goals.waitLabel(hours)
    }

    /** One reminder per waiting wish, due when its time is up. Never doubles up. */
    private fun remind(wish: Wish) {
        val delay = (wish.decideAt - System.currentTimeMillis()).coerceAtLeast(0)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "wish-${wish.id}",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WishReminder>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("id" to wish.id))
                .addTag(REMINDER_TAG)
                .build(),
        )
    }

    /** Waiting wishes get their reminders back: after a restore, or when an old one is gone. */
    suspend fun rescheduleReminders() {
        dao.wishesAll().filter { it.status == WishStatus.WAITING }.forEach(::remind)
    }

    /**
     * Every wish reminder goes, before the wishes are replaced (a restore) or wiped: otherwise an old
     * "wish-5" would keep its time, and win over the restored wish 5's.
     */
    suspend fun cancelReminders() {
        val work = WorkManager.getInstance(context)
        work.cancelAllWorkByTag(REMINDER_TAG)
        // Reminders scheduled before they had a tag.
        dao.wishesAll().forEach { work.cancelUniqueWork("wish-${it.id}") }
    }

    /** "Не беру": the money goes towards the main goal. Returns what that did, short: "+50 $ к «Велосипед»". */
    suspend fun skip(c: VoiceAction.Consider, wishId: Long? = null): String {
        val snap = snapshot()
        val rub = Goals.rubOf(c.amountMinor, c.currency, snap.rates)
        val now = System.currentTimeMillis()
        // Every refusal is remembered, also one decided on the spot: "Не купил" on Goals sums them.
        val wish = wishId?.let { dao.wish(it) }
        if (wish != null) dao.upsertWish(wish.copy(status = WishStatus.SKIPPED, decidedAt = now))
        else dao.upsertWish(Wish(title = c.title, amountMinor = c.amountMinor, currency = c.currency, createdAt = now, decideAt = now, status = WishStatus.SKIPPED, decidedAt = now))
        val goal = snap.mainGoal ?: return tr("Сэкономлено ", "Saved ") + Fmt.amount(c.amountMinor, c.currency)
        val added = Goals.minorOfRub(rub, goal.currency, snap.rates)
        dao.upsertGoal(goal.copy(savedMinor = goal.savedMinor + added))
        val amount = Fmt.amount(added, goal.currency)
        return tr("+$amount к «${goal.name}»", "+$amount to “${goal.name}”")
    }

    /**
     * "Купить" on a reached goal: the expense, from the account the goal is saved on when that one
     * holds enough (see [VoiceMapper.buyGoal]). Returns its id and what it cost; no id when there was
     * no account to pay from.
     */
    suspend fun buyGoal(goal: Goal): Pair<Long?, String?> {
        val s = repo.settings.flow.first()
        val rates = Rates(dao.ratesNow().associate { it.code to it.rubPerUnit }, s.markup)
        val accounts = dao.accountsNow().sortedBy { it.sort }
        val states = Ledger.states(accounts, dao.postingsNow())
        val draft = VoiceMapper.buyGoal(goal, states, accounts, s, rates, System.currentTimeMillis()) ?: return null to null
        val id = repo.save(draft) ?: return null to null
        return id to impact(id)
    }

    /** A waiting wish bought after all, through the ordinary expense form. */
    suspend fun bought(wishId: Long) {
        dao.wish(wishId)?.let { dao.upsertWish(it.copy(status = WishStatus.BOUGHT, decidedAt = System.currentTimeMillis())) }
    }

    suspend fun wish(id: Long) = dao.wish(id)

    suspend fun deleteWish(id: Long) = dao.deleteWish(id)

    /** Undo for [deleteWish]: the wish comes back as it was, with its reminder if it is still waiting. */
    suspend fun restoreWish(wish: Wish) {
        dao.upsertWish(wish)
        if (wish.status == WishStatus.WAITING) remind(wish)
    }

    fun reminderLine(wish: Wish): String {
        val wait = Goals.waitLabel((wish.decideAt - wish.createdAt) / 3_600_000)
        val amount = Fmt.amount(wish.amountMinor, wish.currency)
        return tr("Прошло $wait. Ещё хочешь «${wish.title}» за $amount?", "It's been $wait. Still want “${wish.title}” for $amount?")
    }
}

/** Fires when a wish's thinking time is up. */
class WishReminder(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GoldaApplication
        I18n.russian = AppLanguage.russian(app)
        val wish = app.repo.wishes.wish(inputData.getLong("id", 0)) ?: return Result.success()
        if (wish.status != WishStatus.WAITING) return Result.success()
        notify(app, CHANNEL, tr("Вишлист", "Wishlist"), wish.id.toInt(), wish.title, app.repo.wishes.reminderLine(wish), tab = TAB_GOALS, wish = wish.id)
        return Result.success()
    }

    companion object {
        const val CHANNEL = "wishes"
    }
}

/** Posts a notification that opens the app, when notifications are allowed. */
fun notify(context: Context, channel: String, channelName: String, id: Int, title: String, text: String, tab: Int? = null, wish: Long? = null) {
    // The permission exists from Android 13; before, notifications are simply allowed.
    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(channel, channelName, NotificationManager.IMPORTANCE_DEFAULT))
    val open = PendingIntent.getActivity(
        context, id,
        Intent(context, MainActivity::class.java).putExtra(EXTRA_TAB, tab ?: -1).putExtra(EXTRA_WISH, wish ?: -1L).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(id, notification)
}

/** Once a day: payments due in three days or today, and interest-free periods running out. */
class DebtReminder(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GoldaApplication
        I18n.russian = AppLanguage.russian(app)
        val today = LocalDate.now()
        val states = app.repo.debtStates()
        for (state in states) {
            val account = state.account
            val name = tr("Долги", "Debts")
            if (account.paymentDay != null && account.paymentMinor != null && state.balanceMinor < 0) {
                val due = Budget.nextDue(account.paymentDay, today)
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
                val amount = Fmt.amount(account.paymentMinor, account.currency)
                val text = when (days) {
                    0L -> tr("Сегодня платёж: $amount", "Payment due today: $amount")
                    3L -> tr("Через 3 дня платёж: $amount", "Payment due in 3 days: $amount")
                    else -> null
                }
                if (text != null) notify(app, "debts", name, 100_000 + account.id.toInt(), account.name, text)
            }
            val until = account.graceUntil?.let(LocalDate::ofEpochDay)
            if (until != null && state.balanceMinor < 0) {
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, until)
                if (days == 7L || days == 1L || days == 0L) {
                    val owed = Fmt.amount(-state.balanceMinor, account.currency)
                    val text = if (days == 0L) tr("Льготный период кончается сегодня. Погаси $owed.", "The interest-free period ends today. Pay $owed.")
                    else tr("Льготный период кончается через $days ${plural(days, "день", "дня", "дней", "day", "days")}. Погаси $owed.",
                        "The interest-free period ends in $days ${plural(days, "день", "дня", "дней", "day", "days")}. Pay $owed.")
                    notify(app, "debts", name, 200_000 + account.id.toInt(), account.name, text)
                }
            }
        }
        return Result.success()
    }
}
