package sh.aminov.golda.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

/**
 * The date it is, kept current: it turns over at local midnight while the app is open, when the app
 * comes back to the front, and when the clock, the date or the time zone is changed. Everything that
 * says "сегодня" (the budget, the day labels, the default date of an entry, the analytics periods)
 * reads it through [AppData.today] instead of asking the clock once and keeping the answer.
 */
@Composable
fun rememberToday(): LocalDate {
    val context = LocalContext.current
    var today by remember { mutableStateOf(LocalDate.now()) }
    fun refresh() {
        today = LocalDate.now()
    }

    // Midnight, while the app is open. A second late, so the new day has surely begun.
    LaunchedEffect(today) {
        val midnight = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        delay((midnight - System.currentTimeMillis()).coerceAtLeast(0) + 1_000)
        refresh()
    }

    // Opening the app in the morning, after it spent the night in the background.
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }

    // The clock, the date or the zone changed under it.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = refresh()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return today
}
