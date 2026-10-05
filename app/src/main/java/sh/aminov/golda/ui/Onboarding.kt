package sh.aminov.golda.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sh.aminov.golda.data.Account
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.tr

/** Income → currencies → accounts. Everything here can be changed later in settings. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Onboarding(
    data: AppData,
    onChange: ((Settings) -> Settings) -> Unit,
    onSaveAccount: (Account, Long?) -> Unit,
    onDeleteAccount: (Long) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Account?>(null) }
    var adding by remember { mutableStateOf(false) }
    val steps = 3
    // System back walks the steps back, like "Назад"; on the first step it leaves the app.
    BackHandler(enabled = step > 0) { step-- }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (step > 0) TextButton(onClick = { step-- }) { Text(tr("Назад", "Back")) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { if (step < steps - 1) step++ else onChange { it.copy(onboarded = true) } }) {
                        Text(if (step < steps - 1) tr("Дальше", "Next") else tr("Готово", "Done"))
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Golda", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 16.dp))
                LinearWavyProgressIndicator(progress = { (step + 1f) / steps }, modifier = Modifier.fillMaxWidth())
                when (step) {
                    0 -> {
                        Text(tr("Доход", "Income"), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            tr("Нужен, чтобы считать «можно сегодня» до следующей зарплаты и переводить покупки в часы работы.", "Used to work out what is safe to spend until the next payday and to turn prices into hours of work."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IncomeFields(data.settings, onChange)
                    }
                    1 -> {
                        Text(tr("Валюты", "Currencies"), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            tr("Любая сумма будет видна сразу во всех выбранных валютах. Рубль — основная.", "Every amount shows in all the chosen currencies at once. The ruble is the main one."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CurrencyFields(data.settings, onChange)
                    }
                    else -> {
                        Text(tr("Счета", "Accounts"), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            tr("Карты, наличные, накопительные счета и долги с текущими балансами. Неточные цифры потом легко поправить сверкой.", "Cards, cash, savings and debts with their balances now. Rough numbers are easy to fix later by reconciling."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val count = data.accounts.size + 1
                        Column {
                        data.accounts.forEachIndexed { i, account ->
                            val state = data.states.getValue(account.id)
                            GroupRow(
                                i, count,
                                onClick = { editing = account },
                                supporting = { Text(typeLabel(account.type) + (account.groupName?.let { " · $it" } ?: "")) },
                                trailing = { AmountColumn(Fmt.amount(state.balanceMinor, account.currency)) },
                            ) { Text(account.name, style = MaterialTheme.typography.titleMedium) }
                        }
                        AddRow(count - 1, count, tr("Счёт", "Account"), { adding = true }, Modifier)
                        }
                    }
                }
            }
        }

        // The account form is a page over the step; null shows nothing, a "new" marker an empty form.
        FormPageHost(editing ?: if (adding) NewAccount else null) { target ->
            AccountSheet(
                data = data,
                editing = target as? Account,
                onDismiss = { adding = false; editing = null },
                onSave = { account, opening -> onSaveAccount(account, opening); adding = false; editing = null },
                onDelete = { id -> onDeleteAccount(id); editing = null },
            )
        }
    }
}

/** Stands for "a new account" while its form page is open. */
private object NewAccount
