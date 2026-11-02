package sh.aminov.golda.data

import kotlinx.coroutines.flow.first
import sh.aminov.golda.domain.Draft
import sh.aminov.golda.domain.VoiceAction
import java.time.LocalDate

/**
 * Debug-only shortcuts so the app can be tested without tapping through
 * onboarding (adb: `--ez golda.demo true`, `--ez golda.samples true`).
 * Both wipe everything first. All names and numbers here are made up.
 */
object Demo {
    /** A made-up person: a ruble card, savings, a multi-currency card abroad, cash and two debts. */
    suspend fun fill(repo: Repo) {
        repo.resetAll()
        repo.settings.update {
            it.copy(
                onboarded = true,
                displayCurrencies = listOf("RUB", "USD", "GEL"),
                localCurrency = "GEL",
            )
        }
        val today = LocalDate.now()
        repo.saveAccount(Account(name = "Карта ₽", currency = "RUB", type = AccountType.CARD, includeInFree = true, sort = 0), 500_000)
        repo.saveAccount(
            Account(name = "Накопительный", currency = "RUB", type = AccountType.SAVINGS, includeInFree = false, interestRate = 12.0, sort = 1),
            25_000_000,
        )
        repo.saveAccount(
            Account(name = "Мультивалютная USD", currency = "USD", type = AccountType.CARD, groupName = "Мультивалютная", includeInFree = true, sort = 2),
            null,
        )
        repo.saveAccount(
            Account(name = "Мультивалютная GEL", currency = "GEL", type = AccountType.CARD, groupName = "Мультивалютная", includeInFree = true, sort = 3),
            null,
        )
        repo.saveAccount(Account(name = "Наличные ₾", currency = "GEL", type = AccountType.CASH, includeInFree = true, sort = 4), null)
        repo.saveAccount(
            Account(
                name = "Кредитка", currency = "RUB", type = AccountType.CREDIT, includeInFree = false, sort = 5,
                interestRate = 29.9, paymentDay = 25, paymentMinor = 300_000, graceUntil = today.plusDays(40).toEpochDay(),
            ),
            -1_500_000,
        )
        repo.saveAccount(
            Account(
                name = "Кредит", currency = "RUB", type = AccountType.LOAN, includeInFree = false, sort = 6,
                interestRate = 19.9, paymentDay = 5, paymentMinor = 1_000_000,
            ),
            -20_000_000,
        )
    }

    /** [fill] plus a couple of weeks of made-up life abroad, so the screens look lived in. */
    suspend fun samples(repo: Repo, now: Long = System.currentTimeMillis()) {
        fill(repo)
        val hour = 3_600_000L
        val day = 24 * hour
        val account = repo.accounts.first().associate { it.name to it.id }
        val category = repo.categories.first().associate { it.key to it.id }
        val rub = account.getValue("Карта ₽")
        val usd = account.getValue("Мультивалютная USD")
        val gel = account.getValue("Мультивалютная GEL")
        val cash = account.getValue("Наличные ₾")

        fun expense(at: Long, from: Long, minor: Long, key: String, note: String = "") =
            Draft(OpType.EXPENSE, at, from, minor, categoryId = category[key], note = note)

        // Pay arrives in rubles: half goes to savings, 800 $ go abroad for 73 600 ₽, and part of that into lari and cash.
        repo.save(Draft(OpType.INCOME, now - 12 * day, rub, 15_840_000, categoryId = category["salary"]))
        repo.save(Draft(OpType.TRANSFER, now - 12 * day + hour, rub, 8_000_000, toAccountId = account.getValue("Накопительный"), toAmountMinor = 8_000_000, note = "В подушку"))
        repo.save(Draft(OpType.TRANSFER, now - 12 * day + 2 * hour, rub, 7_360_000, toAccountId = usd, toAmountMinor = 80_000, note = "Перевод за границу"))
        repo.save(Draft(OpType.TRANSFER, now - 11 * day, usd, 60_000, toAccountId = gel, toAmountMinor = 160_800, note = "Обмен в приложении банка"))
        repo.save(Draft(OpType.TRANSFER, now - 10 * day, usd, 10_000, toAccountId = cash, toAmountMinor = 26_500, note = "Банкомат"))
        repo.save(Draft(OpType.EXPENSE, now - 10 * day, usd, 300, categoryId = category["fees"], note = "Комиссия банкомата"))

        val days = listOf(
            expense(now - 9 * day, gel, 90_000, "housing", "Аренда"),
            expense(now - 9 * day, rub, 69_900, "subscriptions", "Музыка и облако"),
            expense(now - 8 * day, gel, 6_420, "groceries", "Продукты"),
            expense(now - 8 * day, cash, 1_500, "eating_out", "Хачапури"),
            expense(now - 7 * day, gel, 3_500, "telecom", "Мобильный интернет"),
            expense(now - 7 * day, cash, 800, "transport", "Метро"),
            expense(now - 6 * day, gel, 4_800, "eating_out", "Ужин"),
            expense(now - 5 * day, gel, 8_960, "groceries", "Продукты"),
            expense(now - 5 * day, cash, 2_000, "fun", "Музей"),
            expense(now - 4 * day, gel, 1_200, "transport", "Такси"),
            expense(now - 3 * day, gel, 12_500, "clothes", "Куртка"),
            expense(now - 3 * day, cash, 900, "eating_out", "Кофе"),
            expense(now - 2 * day, gel, 5_230, "groceries", "Продукты"),
            expense(now - 1 * day, gel, 4_500, "health", "Аптека"),
            expense(now - 1 * day, cash, 1_800, "eating_out", "Обед"),
            expense(now - 3 * hour, cash, 1_500, "eating_out", "Шаурма"),
            expense(now - 1 * hour, cash, 800, "eating_out", "Кофе"),
        )
        days.forEach { repo.save(it) }
        // A purchase in lari charged to the dollar card: the charge is the app's estimate.
        repo.save(
            Draft(
                OpType.EXPENSE, now - day - 4 * hour, usd, 1_899, categoryId = category["groceries"], note = "Рынок",
                purchaseAmountMinor = 4_850, purchaseCurrency = "GEL", isEstimate = true,
            ),
        )
    }
}
