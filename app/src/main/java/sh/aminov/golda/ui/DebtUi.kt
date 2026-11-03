package sh.aminov.golda.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.domain.AccountState
import sh.aminov.golda.domain.Budget
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.Debts
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.plural
import sh.aminov.golda.domain.tr
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

private fun months(n: Long) = "$n ${plural(n, "месяц", "месяца", "месяцев", "month", "months")}"

/** Terms, what is left and the early repayment calculator, on a debt's page. */
@Composable
fun DebtDetails(data: AppData, state: AccountState) {
    val account = state.account
    val owed = -state.balanceMinor
    val code = account.currency
    val today = data.today
    var prepaying by remember { mutableStateOf(false) }
    val body = MaterialTheme.typography.bodyMedium.merge(Tnum)

    // Label and value per line, like a bank's terms: read down the left, the numbers on the right.
    @Composable
    fun Term(label: String, value: String) {
        Row(Modifier.fillMaxWidth().padding(vertical = Gap.xs)) {
            Text(label, Modifier.weight(1f), style = body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Gap.m))
            Text(value, style = MaterialTheme.typography.titleMedium.merge(Tnum), maxLines = 1)
        }
    }
    Column {
        account.interestRate?.let { Term(tr("Ставка", "Rate"), tr("${Fmt.number(it, 1)} % годовых", "${Fmt.number(it, 1)} % a year")) }
        if (account.paymentDay != null && account.paymentMinor != null) {
            val due = Budget.nextDue(account.paymentDay, today)
            Term(tr("Платёж", "Payment"), Fmt.amount(account.paymentMinor, code))
            Term(tr("Следующий", "Next due"), dayLabel(due, today))
        }
        val rate = account.interestRate
        val payment = account.paymentMinor
        if (account.type == AccountType.LOAN && rate != null && payment != null && owed > 0) {
            val n = Debts.monthsLeft(owed, rate, payment)
            val interest = Debts.interestLeft(owed, rate, payment)
            if (n == null || interest == null) {
                Text(tr("Платёж не покрывает даже проценты, долг растёт", "The payment does not even cover the interest; the debt grows"), color = MaterialTheme.colorScheme.error)
            } else {
                Term(tr("Осталось", "To go"), "≈ " + months(ceil(n).toLong()))
                Term(tr("Переплата", "Interest"), "≈ " + Fmt.approx(interest / Currencies.factor(code), code))
                VSpace(Gap.m)
                Row { TonalAction(tr("Погасить досрочно", "Pay off early"), { prepaying = true }) }
            }
        }
        graceWarning(data, state, today)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = body) }
    }

    if (prepaying && account.interestRate != null && account.paymentMinor != null) {
        PrepayDialog(data, owed, account.interestRate, account.paymentMinor, code) { prepaying = false }
    }
}

/** "Льготный период кончается через 3 дня — погаси 48 000 ₽", or null while there is time or nothing owed. */
fun graceWarning(data: AppData, state: AccountState, today: LocalDate): String? {
    val until = state.account.graceUntil?.let(LocalDate::ofEpochDay) ?: return null
    val owed = -state.balanceMinor
    if (owed <= 0) return null
    val days = ChronoUnit.DAYS.between(today, until)
    if (days !in 0..7) return null
    val amount = Fmt.amount(owed, state.account.currency)
    return when (days) {
        0L -> tr("«${state.account.name}»: льготный период кончается сегодня, погаси $amount", "“${state.account.name}”: the interest-free period ends today; pay $amount")
        else -> tr(
            "«${state.account.name}»: льготный период кончается через $days ${plural(days, "день", "дня", "дней", "day", "days")}, погаси $amount",
            "“${state.account.name}”: the interest-free period ends in $days ${plural(days, "день", "дня", "дней", "day", "days")}; pay $amount",
        )
    }
}

@Composable
private fun PrepayDialog(data: AppData, owedMinor: Long, rate: Double, paymentMinor: Long, code: String, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val extra = Fmt.parseMinor(text, code)?.takeIf { it > 0 }
    val savings = data.accounts.filter { it.type == AccountType.SAVINGS && it.interestRate != null }.maxByOrNull { it.interestRate!! }
    val result = extra?.let { Debts.prepay(owedMinor, rate, paymentMinor, it, savings?.interestRate) }
    fun money(minor: Long) = Fmt.approx(minor / Currencies.factor(code), code)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Досрочное погашение", "Early repayment")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TonalField(
                    text, { text = it },
                    label = tr("Сколько внести", "How much to pay"),
                    suffix = Currencies.symbol(code),
                    keyboardType = KeyboardType.Decimal,
                )
                if (result != null) {
                    val sooner = (result.monthsNow - result.monthsAfter).toLong()
                    Text(
                        tr("Сократить срок: на ${months(sooner)} раньше, проценты −${money(result.savedByTermMinor)}",
                            "Shorter term: ${months(sooner)} sooner, interest −${money(result.savedByTermMinor)}"),
                        style = MaterialTheme.typography.bodyLarge.merge(Tnum),
                    )
                    Text(
                        tr("Уменьшить платёж: ${money(result.paymentAfterMinor)} в месяц, проценты −${money(result.savedByPaymentMinor)}",
                            "Lower payment: ${money(result.paymentAfterMinor)} a month, interest −${money(result.savedByPaymentMinor)}"),
                        style = MaterialTheme.typography.bodyLarge.merge(Tnum),
                    )
                    if (savings != null && result.savingsWouldEarnMinor != null) {
                        val earn = result.savingsWouldEarnMinor
                        Text(
                            tr("На «${savings.name}» эти деньги за то же время принесли бы ${money(earn)}.",
                                "On “${savings.name}” the same money would earn ${money(earn)} over that time."),
                            style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (result.savedByTermMinor > earn) {
                                tr("Гасить выгоднее на ${money(result.savedByTermMinor - earn)}.", "Paying off is better by ${money(result.savedByTermMinor - earn)}.")
                            } else {
                                tr("Выгоднее оставить на счёте: +${money(earn - result.savedByTermMinor)}.", "Keeping it saved is better by ${money(earn - result.savedByTermMinor)}.")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        tr("Расчёт по аннуитетной схеме, точные цифры в графике банка.", "Annuity estimate; the bank's schedule has the exact figures."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Готово", "Done")) } },
    )
}
