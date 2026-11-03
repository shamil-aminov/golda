package sh.aminov.golda.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
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
import sh.aminov.golda.domain.tr

/**
 * Regular payments as a block under the accounts: the list with the month's total, "+ Платёж", and
 * what looks regular in the spending but is not listed yet, one tap to add.
 */
fun LazyListScope.regularPayments(data: AppData, suggestions: List<Obligation>, onEdit: (Obligation) -> Unit) {
    val payments = data.obligations
    item(key = "regulars-label") {
        VSpace(Gap.l)
        // The month's total under the name; adding is the group's action, in its header.
        val total = payments.takeIf { it.isNotEmpty() }?.let { data.base.approx(Regulars.monthlyRub(it, data.rates)) }
        GroupLabel(
            tr("Регулярные платежи", "Regular payments"),
            Modifier.padding(horizontal = Gap.m),
            supporting = total?.let { tr("$it в месяц", "$it a month") },
        ) {
            HeaderAction(tr("Добавить", "Add")) {
                onEdit(Obligation(name = "", amountMinor = 0, currency = data.settings.localCurrency, dayOfMonth = data.today.dayOfMonth))
            }
        }
    }
    val count = payments.size
    payments.forEachIndexed { i, payment ->
        item(key = "regular-${payment.id}") { PaymentRow(payment, i, count, Modifier.padding(horizontal = Gap.m)) { onEdit(payment) } }
    }
    if (suggestions.isNotEmpty()) {
        item(key = "regulars-suggested") {
            VSpace(Gap.l)
            GroupLabel(tr("Похоже на регулярные", "Looks regular"), Modifier.padding(horizontal = Gap.m))
        }
        suggestions.forEachIndexed { i, payment ->
            item(key = "suggested-${payment.name}") { PaymentRow(payment, i, suggestions.size, Modifier.padding(horizontal = Gap.m), add = true) { onEdit(payment) } }
        }
    }
}

/** A payment: its name, the day it is charged, the amount (or "пробный"); a suggestion has a "+". */
@Composable
private fun PaymentRow(payment: Obligation, index: Int, count: Int, modifier: Modifier, add: Boolean = false, onClick: () -> Unit) {
    GroupRow(
        index, count, modifier,
        onClick = onClick,
        // A suggestion says how to take it; its amount stays in the column with the others.
        supporting = { Text(dayOfMonth(payment.dayOfMonth) + if (add) tr(" · нажми, чтобы добавить", " · tap to add") else "") },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                Text(
                    if (payment.amountMinor == 0L) tr("пробный", "trial") else Fmt.amount(payment.amountMinor, payment.currency),
                    style = MaterialTheme.typography.titleMedium.merge(Tnum),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )

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
fun PaymentSheet(data: AppData, payment: Obligation, onDismiss: () -> Unit, onSave: (Obligation) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun save(saved: Obligation) {
        onSave(saved)
        // A reminder needs notifications: asked once there is something to remind of.
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
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
                save(payment.copy(name = name.trim(), amountMinor = amount ?: 0, currency = currency, dayOfMonth = day ?: 1))
            }, enabled = valid)
        },
    ) {
        TonalField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = tr("Название, например Netflix", "Name, like Netflix"))
        VSpace(Gap.l)
        HeroAmountField(amountText, { amountText = it }, Currencies.symbol(currency))
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
