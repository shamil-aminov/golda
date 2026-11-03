package sh.aminov.golda.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import sh.aminov.golda.R
import sh.aminov.golda.data.Account
import sh.aminov.golda.domain.Currencies
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
    var currencies by remember { mutableStateOf<CurrencyPick?>(null) }
    val steps = 2
    // System back walks the steps back, like "Назад"; on the first step it leaves the app.
    BackHandler(enabled = step > 0) { step-- }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            // Like every page: back as the circle, the step's action as the wide button.
            bottomBar = {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(Gap.m),
                    horizontalArrangement = Arrangement.spacedBy(Gap.s),
                ) {
                    if (step > 0) RoundAction(R.drawable.ic_back, tr("Назад", "Back")) { step-- }
                    PrimaryAction(if (step < steps - 1) tr("Дальше", "Next") else tr("Готово", "Done"), {
                        if (step < steps - 1) step++ else onChange { it.copy(onboarded = true) }
                    })
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = Gap.l)) {
                // The page title the account page has, and the steps as a plain count under it.
                Text(
                    if (step == 0) tr("Валюты", "Currencies") else tr("Счета", "Accounts"),
                    Modifier.padding(start = Gap.l, end = Gap.l, top = Gap.l),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Caption(tr("Шаг ${step + 1} из $steps", "Step ${step + 1} of $steps"), Modifier.padding(horizontal = Gap.l))
                VSpace(Gap.m)
                when (step) {
                    0 -> {
                        Text(
                            tr("Любая сумма будет видна сразу во всех выбранных валютах. Основная валюта рубль.", "Every amount shows in all the chosen currencies at once. The ruble is the main one."),
                            Modifier.padding(horizontal = Gap.l),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        VSpace(Gap.l)
                        // The same rows as in Settings, opening the same list page.
                        val s = data.settings
                        GroupRow(
                            0, 2, Modifier.padding(horizontal = Gap.m),
                            onClick = { currencies = CurrencyPick.SHOWN },
                            supporting = { Text(s.displayCurrencies.joinToString(" ") { Currencies.symbol(it) }) },
                        ) { Text(tr("Показывать суммы в", "Show amounts in"), style = MaterialTheme.typography.titleMedium) }
                        GroupRow(
                            1, 2, Modifier.padding(horizontal = Gap.m),
                            onClick = { currencies = CurrencyPick.LOCAL },
                            supporting = { Text(currencyLabel(s.localCurrency) + tr(", для новых трат и голоса", ", for new purchases and voice")) },
                        ) { Text(tr("Местная валюта", "Local currency"), style = MaterialTheme.typography.titleMedium) }
                    }
                    else -> {
                        Text(
                            tr("Карты, наличные, накопительные счета и долги с текущими балансами. Неточные цифры потом легко поправить сверкой.", "Cards, cash, savings and debts with their balances now. Rough numbers are easy to fix later by reconciling."),
                            Modifier.padding(horizontal = Gap.l),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Adding is the group's action, in its header, as on Accounts.
                        GroupLabel(tr("Счета", "Accounts"), Modifier.padding(horizontal = Gap.m).padding(top = Gap.l)) {
                            HeaderAction(tr("Добавить", "Add")) { adding = true }
                        }
                        val count = data.accounts.size
                        data.accounts.forEachIndexed { i, account ->
                            val state = data.states.getValue(account.id)
                            GroupRow(
                                i, count, Modifier.padding(horizontal = Gap.m),
                                onClick = { editing = account },
                                supporting = typeLabel(account.type).takeUnless { account.name.contains(it, ignoreCase = true) }?.let { { Text(it) } },
                                trailing = { AmountColumn(Fmt.amount(state.balanceMinor, account.currency)) },
                            ) { Text(account.name, style = MaterialTheme.typography.titleMedium) }
                        }
                    }
                }
            }
        }

        FormPageHost(currencies) { pick -> CurrencyPage(data, pick, onChange) { currencies = null } }

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
