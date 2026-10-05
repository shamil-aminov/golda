package sh.aminov.golda.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import sh.aminov.golda.R
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.Goal
import sh.aminov.golda.data.Obligation
import sh.aminov.golda.data.Wish
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Rate
import sh.aminov.golda.domain.AccountState
import sh.aminov.golda.domain.Base
import sh.aminov.golda.domain.Currencies
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.tr
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.Rates
import sh.aminov.golda.domain.Settings
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import sh.aminov.golda.domain.Debts

/** One consistent snapshot of everything the screens show. */
@Immutable
class AppData(
    val settings: Settings,
    val accounts: List<Account>,
    val operations: List<OperationFull>,
    val categories: List<Category>,
    rateList: List<Rate>,
    val obligations: List<Obligation> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val wishes: List<Wish> = emptyList(),
    /** The day it is: kept current by [rememberToday], so "Сегодня" and the budget turn over at midnight. */
    val today: LocalDate = LocalDate.now(),
) {
    val rates = Rates(rateList.associate { it.code to it.rubPerUnit }, settings.markup)
    val ratesDate: String? = rateList.maxOfOrNull { it.date }

    /** The main currency the big numbers and totals are shown in. */
    val base = Base.of(settings, rates)
    val states: Map<Long, AccountState> = Ledger.states(accounts, operations.flatMap { it.postings })
    val accountById = accounts.associateBy { it.id }
    val categoryById = categories.associateBy { it.id }
    val zone: ZoneId = ZoneId.systemDefault()

    /** Monthly payments typed in settings plus the ones debts carry. */
    val allObligations: List<Obligation> = obligations + Debts.obligations(states.values)

    /** Operations worth listing: opening balances are bookkeeping, not events. */
    val visibleOperations = operations.filter { it.op.type != OpType.OPENING }

    /** The account things are usually paid from; lists name an account only when it is a different one. */
    val usualAccountId: Long? = settings.lastAccountId ?: accounts.firstOrNull { it.includeInFree }?.id

    /** "≈ 45,8 $ · 124 ₾ · 1 498 ฿": [rubMinor] in every display currency but [exclude]. */
    fun others(rubMinor: Long, exclude: String): String =
        // The currency of where you are comes first: that is the one you pay in today.
        (listOf(settings.localCurrency) + settings.displayCurrencies).distinct().filter { it != exclude }.mapNotNull { code ->
            val value = if (code == "RUB") rubMinor / 100.0 else rates.fromRub(rubMinor, code)
            value?.let { Fmt.approx(it, code) }
        }.joinToString(" · ")

    /**
     * Currencies to pick from, in one order everywhere: the local one, then the
     * display currencies as set, then [extra] (say, an account's own currency).
     * Picking a chip never moves it.
     */
    fun currencyChoices(vararg extra: String): List<String> =
        (listOf(settings.localCurrency) + settings.displayCurrencies + extra).distinct()
}

fun currencyLabel(code: String): String =
    Currencies.symbol(code).let { if (it == code) code else "$it $code" }

fun typeLabel(type: AccountType) = when (type) {
    AccountType.CARD -> tr("Карта", "Card")
    AccountType.CASH -> tr("Наличные", "Cash")
    AccountType.SAVINGS -> tr("Накопительный", "Savings")
    AccountType.CREDIT -> tr("Кредитка", "Credit card")
    AccountType.LOAN -> tr("Кредит", "Loan")
}

fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> tr("Сегодня", "Today")
    today.minusDays(1) -> tr("Вчера", "Yesterday")
    today.plusDays(1) -> tr("Завтра", "Tomorrow")
    else -> date.format(DateTimeFormatter.ofPattern(if (I18n.russian) "d MMMM" else "MMMM d", I18n.locale))
}

fun monthName(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("LLLL", I18n.locale))

/** Each account type's icon: in the type tiles and on the account pill. */
@DrawableRes
fun typeIcon(type: AccountType): Int = when (type) {
    AccountType.CARD -> R.drawable.ic_card
    AccountType.CASH -> R.drawable.ic_cash
    AccountType.SAVINGS -> R.drawable.ic_growth
    AccountType.CREDIT -> R.drawable.ic_credit_card
    AccountType.LOAN -> R.drawable.ic_loan
}

/** A category's icon by its stable key; one of your own gets the box. */
@DrawableRes
fun categoryIcon(key: String?): Int = when (key) {
    "eating_out" -> R.drawable.ic_cat_dining
    "groceries" -> R.drawable.ic_cat_groceries
    "transport" -> R.drawable.ic_cat_transport
    "housing" -> R.drawable.ic_cat_housing
    "telecom" -> R.drawable.ic_cat_telecom
    "fun" -> R.drawable.ic_cat_fun
    "health" -> R.drawable.ic_cat_health
    "clothes" -> R.drawable.ic_cat_clothes
    "subscriptions" -> R.drawable.ic_cat_subscriptions
    "travel" -> R.drawable.ic_cat_travel
    "fees" -> R.drawable.ic_cat_fees
    "salary" -> R.drawable.ic_cat_salary
    "interest" -> R.drawable.ic_cat_interest
    "gift" -> R.drawable.ic_cat_gift
    else -> R.drawable.ic_cat_other
}

/** What an operation's row shows at its start: its category, or what kind of move it is. */
@DrawableRes
fun operationIcon(category: Category?, type: OpType): Int = when {
    category != null -> categoryIcon(category.key)
    type == OpType.TRANSFER -> R.drawable.ic_swap
    type == OpType.ADJUSTMENT -> R.drawable.ic_check
    else -> R.drawable.ic_cat_other
}
