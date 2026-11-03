package sh.aminov.golda.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import sh.aminov.golda.R
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.domain.Envelopes
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Which date of the period a picker is open for. */
private enum class Picking { START, END }

/**
 * The spending period: the card spent from, from when, and for how long the money on it is meant to
 * last (a week, two, a month, or a date of your own). It starts at the card's last top-up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSheet(data: AppData, onDismiss: () -> Unit, onChange: ((Settings) -> Settings) -> Unit) {
    val envelope = data.envelope ?: return
    val cards = data.accounts.filter { it.type != AccountType.CREDIT && it.type != AccountType.LOAN }
    var accountId by remember { mutableStateOf(envelope.account.id) }
    var start by remember { mutableStateOf(envelope.start) }
    var end by remember { mutableStateOf(envelope.end) }
    var picking by remember { mutableStateOf<Picking?>(null) }
    val length = Envelopes.Length.entries.firstOrNull { Envelopes.end(start, it) == end }

    fun pickAccount(id: Long) {
        accountId = id
        // Another card: its own last top-up starts the period, the length stays.
        val top = Envelopes.lastTopUp(id, data.operations, data.today, data.zone) ?: data.today
        end = length?.let { Envelopes.end(top, it) } ?: end
        start = top
    }

    FormSheet(
        onDismiss = onDismiss,
        actions = {
            PrimaryAction(tr("Готово", "Done"), {
                onChange { it.copy(spendAccountId = accountId, periodStart = start.toEpochDay(), periodEnd = end.toEpochDay()) }
                onDismiss()
            }, enabled = !end.isBefore(data.today) && end.isAfter(start))
        },
    ) {
        Caption(tr("Карта трат", "The card you spend from"))
        VSpace(Gap.s)
        ChoiceGroup(cards.map { it.id }, accountId, ::pickAccount, Modifier.fillMaxWidth()) { id ->
            ChoiceText(data.accountById[id]?.name.orEmpty())
        }
        VSpace(Gap.l)
        Caption(tr("Деньги на ней — на", "The money on it is for"))
        VSpace(Gap.s)
        ChoiceGroup(Envelopes.Length.entries, length, { end = Envelopes.end(start, it) }, Modifier.fillMaxWidth()) {
            ChoiceText(
                when (it) {
                    Envelopes.Length.WEEK -> tr("Неделю", "A week")
                    Envelopes.Length.TWO_WEEKS -> tr("2 недели", "2 weeks")
                    Envelopes.Length.MONTH -> tr("Месяц", "A month")
                },
            )
        }
        VSpace(Gap.m)
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
            MetaPill(tr("с ", "from ") + dayLabel(start, data.today), { picking = Picking.START }, painter = painterResource(R.drawable.ic_calendar))
            MetaPill(tr("до ", "until ") + dayLabel(end, data.today), { picking = Picking.END }, painter = painterResource(R.drawable.ic_calendar))
        }
        VSpace(Gap.s)
        UnderLine(tr("Начало — последнее пополнение карты", "It starts at the card's last top-up"))
    }

    picking?.let { which ->
        val initial = if (which == Picking.START) start else end
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == Picking.START) {
                            // Moving the start keeps a week, two or a month a week, two or a month.
                            end = length?.let { Envelopes.end(day, it) } ?: end
                            start = day
                        } else {
                            end = day
                        }
                    }
                    picking = null
                }) { Text(tr("Готово", "Done")) }
            },
        ) { DatePicker(state) }
    }
}

/** "С 26 октября по 26 ноября": the period, for its settings row. */
fun periodLabel(start: LocalDate, end: LocalDate, today: LocalDate): String =
    sh.aminov.golda.domain.ReviewPrompt.span(start, end).replaceFirstChar { it.titlecase(sh.aminov.golda.domain.I18n.locale) }
