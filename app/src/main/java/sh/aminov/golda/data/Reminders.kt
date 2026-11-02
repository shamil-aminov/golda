package sh.aminov.golda.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import sh.aminov.golda.EXTRA_TAB
import sh.aminov.golda.GoldaApplication
import sh.aminov.golda.MainActivity
import sh.aminov.golda.R
import sh.aminov.golda.domain.Budget
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.plural
import sh.aminov.golda.domain.tr
import java.time.LocalDate

/**
 * The wishlist's reminders are gone with the wishlist; an install that had some still has them
 * queued, so they are cancelled on start.
 */
fun cancelWishReminders(context: Context) {
    WorkManager.getInstance(context).cancelAllWorkByTag("wish-reminder")
}

/** Posts a notification that opens the app, when notifications are allowed. */
fun notify(context: Context, channel: String, channelName: String, id: Int, title: String, text: String, tab: Int? = null) {
    // The permission exists from Android 13; before, notifications are simply allowed.
    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(channel, channelName, NotificationManager.IMPORTANCE_DEFAULT))
    val open = PendingIntent.getActivity(
        context, id,
        Intent(context, MainActivity::class.java).putExtra(EXTRA_TAB, tab ?: -1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
