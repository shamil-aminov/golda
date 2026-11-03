package sh.aminov.golda.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import sh.aminov.golda.R
import sh.aminov.golda.data.Obligation
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.Regulars
import sh.aminov.golda.domain.plural
import sh.aminov.golda.domain.tr

/**
 * Regular payments: the list with each one's day and amount, under it what looks regular in the
 * spending but is not in the list yet (one tap to add), and at the bottom back and "Добавить".
 */
@Composable
fun RegularsScreen(
    data: AppData,
    payments: List<Obligation>,
    onBack: () -> Unit,
    onSave: (Obligation) -> Unit,
    onDelete: (Long) -> Unit,
) {
    BackHandler(onBack = onBack)
    var editing by remember { mutableStateOf<Obligation?>(null) }
    val suggestions = remember(data, payments) {
        Regulars.suggest(data.operations, data.accountById, data.categoryById, payments, data.today, data.zone)
    }
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun save(payment: Obligation) {
        onSave(payment)
        editing = null
        // A reminder needs notifications: asked once there is something to remind of.
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BottomPage(
        actions = {
            RoundAction(R.drawable.ic_back, tr("Назад", "Back"), onBack)
            PrimaryAction(tr("Добавить", "Add"), { editing = Obligation(name = "", amountMinor = 0, currency = data.settings.localCurrency, dayOfMonth = data.today.dayOfMonth) })
        },
    ) {
        Text(tr("Регулярные платежи", "Regular payments"), style = MaterialTheme.typography.headlineMedium)
        VSpace(Gap.s)
        Text(
            tr("За ${Regulars.AHEAD} дня до списания напомню и спрошу, нужна ли ещё подписка.", "${Regulars.AHEAD} days before a charge you get a reminder asking if it is still needed."),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(Gap.l)
        if (payments.isNotEmpty()) {
            val total = data.base.approx(Regulars.monthlyRub(payments, data.rates))
            GroupLabel(tr("В месяц $total", "$total a month"))
            payments.forEachIndexed { i, payment ->
                PaymentRow(payment, i, payments.size) { editing = payment }
            }
            VSpace(Gap.l)
        }
        if (suggestions.isNotEmpty()) {
            GroupLabel(tr("Похоже на регулярные", "Looks regular"))
            suggestions.forEachIndexed { i, payment ->
                PaymentRow(payment, i, suggestions.size, add = true) { editing = payment }
            }
        }
    }

    editing?.let { payment ->
        PaymentSheet(data, payment, onDismiss = { editing = null }, onSave = ::save, onDelete = { onDelete(payment.id); editing = null })
    }
}

/** A payment: its name, the day it is charged, the amount (or "пробный"); a suggestion has a "+". */
@Composable
private fun PaymentRow(payment: Obligation, index: Int, count: Int, add: Boolean = false, onClick: () -> Unit) {
    GroupRow(
        index, count,
        onClick = onClick,
        supporting = { Text(dayOfMonth(payment.dayOfMonth)) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                Text(
                    if (payment.amountMinor == 0L) tr("пробный", "trial") else Fmt.amount(payment.amountMinor, payment.currency),
                    style = MaterialTheme.typography.titleMedium.merge(Tnum),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (add) Icon(painterResource(R.drawable.ic_add), tr("Добавить", "Add"), Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) { Text(payment.name, style = MaterialTheme.typography.titleMedium) }
}

/** "5 числа" — the day of every month a payment is charged. */
private fun dayOfMonth(day: Int): String = tr("$day числа", "on the ${ordinal(day)}")

private fun ordinal(day: Int): String = day.toString() + when {
    day % 100 in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}

/** One payment: name, amount and currency, the day; 0 is a free trial. */
@Composable
private fun PaymentSheet(data: AppData, payment: Obligation, onDismiss: () -> Unit, onSave: (Obligation) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(payment.name) }
    var currency by remember { mutableStateOf(payment.currency) }
    var amountText by remember { mutableStateOf(if (payment.amountMinor > 0) Fmt.editable(payment.amountMinor, payment.currency) else "") }
    var dayText by remember { mutableStateOf(payment.dayOfMonth.toString()) }
    val amount = if (amountText.isBlank()) 0L else Fmt.parseMinor(amountText, currency)
    val day = dayText.toIntOrNull()?.takeIf { it in 1..31 }
    val valid = name.isNotBlank() && amount != null && day != null

    FormSheet(
        onDismiss = onDismiss,
        actions = {
            if (payment.id != 0L) RoundAction(R.drawable.ic_delete, tr("Удалить", "Delete"), onDelete)
            PrimaryAction(tr("Сохранить", "Save"), {
                onSave(payment.copy(name = name.trim(), amountMinor = amount ?: 0, currency = currency, dayOfMonth = day ?: 1))
            }, enabled = valid)
        },
    ) {
        TonalField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = tr("Название, например Netflix", "Name, like Netflix"))
        VSpace(Gap.l)
        HeroAmountField(amountText, { amountText = it }, Currencies.symbol(currency), maxSp = 64f)
        VSpace(Gap.m)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CurrencyPicker(data.currencyChoices(currency), currency, { currency = it })
        }
        VSpace(Gap.l)
        TonalField(
            dayText, { dayText = it.filter(Char::isDigit).take(2) }, Modifier.fillMaxWidth(),
            label = tr("Какого числа списывается", "Day of the month it is charged"),
            keyboardType = KeyboardType.Number,
        )
        VSpace(Gap.s)
        UnderLine(tr("Без суммы это пробный период", "No amount is a free trial"))
    }
}

/** "3 платежа · 2 400 ₽ в месяц", for the settings row. */
fun regularsSummary(data: AppData, payments: List<Obligation>): String {
    if (payments.isEmpty()) return tr("Пока нет", "None yet")
    val n = payments.size.toLong()
    return "$n " + plural(n, "платёж", "платежа", "платежей", "payment", "payments") + " · " +
        tr("${data.base.approx(Regulars.monthlyRub(payments, data.rates))} в месяц", "${data.base.approx(Regulars.monthlyRub(payments, data.rates))} a month")
}
