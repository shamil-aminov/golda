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
import sh.aminov.golda.domain.tr

/**
 * Regular payments as a block under the accounts: the list, and adding one from the header.
 */
fun LazyListScope.regularPayments(data: AppData, onEdit: (Obligation) -> Unit) {
    val payments = data.obligations
    item(key = "regulars-label") {
        // Adding is the group's action, in the header that opens its card.
        GroupHeader(
            tr("Регулярные платежи", "Regular payments"), tr("Добавить платёж", "Add a payment"),
            onAdd = { onEdit(Obligation(name = "", amountMinor = 0, currency = data.settings.localCurrency, dayOfMonth = data.today.dayOfMonth)) },
            modifier = Modifier.padding(horizontal = Gap.m).padding(top = Space.cards),
            alone = payments.isEmpty(),
        )
    }
    payments.forEachIndexed { i, payment ->
        item(key = "regular-${payment.id}") { PaymentRow(payment, i + 1, payments.size + 1, Modifier.padding(horizontal = Gap.m)) { onEdit(payment) } }
    }
}

/** A payment: its name, the day it is charged, the amount (or "пробный"). */
@Composable
private fun PaymentRow(payment: Obligation, index: Int, count: Int, modifier: Modifier, onClick: () -> Unit) {
    GroupRow(
        index, count, modifier,
        onClick = onClick,
        // Like an account's row: the same icon, the same three roles.
        leading = { RowIcon(R.drawable.ic_repeat) },
        supporting = { Text(dayOfMonth(payment.dayOfMonth), style = TextRole.caption) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                Text(
                    if (payment.amountMinor == 0L) tr("пробный", "trial") else Fmt.amount(payment.amountMinor, payment.currency),
                    style = TextRole.rowAmount,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        },
    ) { Text(payment.name, style = TextRole.rowName) }
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
            label = tr("Спишется", "Charged on"),
            suffix = tr("числа", "of the month"),
            keyboardType = KeyboardType.Number,
        )
        VSpace(Gap.s)
        // Said only while there is no amount: that is when it applies.
        if (amountText.isBlank()) UnderLine(tr("Без суммы это пробный период", "No amount is a free trial"))
    }
}
