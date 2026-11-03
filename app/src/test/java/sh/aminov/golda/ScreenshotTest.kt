package sh.aminov.golda

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.DefaultCategories
import sh.aminov.golda.data.Obligation
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Operation
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Rate
import sh.aminov.golda.domain.Draft
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.Rates
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.ui.AccountsScreen
import sh.aminov.golda.ui.AnalyticsScreen
import sh.aminov.golda.ui.AppData
import sh.aminov.golda.ui.EntryRequest
import sh.aminov.golda.ui.EntrySheet
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HomeScreen
import sh.aminov.golda.ui.SettingsScreen
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * The main screens drawn on the JVM with a fixed, made-up life abroad and compared with the stored
 * images in `app/src/test/screenshots`. A change that moves a pixel shows up as a diff.
 *
 * `./gradlew recordRoborazziDebug` stores new images after an intended change;
 * `./gradlew verifyRoborazziDebug` fails on any difference and writes the diffs to `build/outputs/roborazzi`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 24)

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    private fun shot(name: String, palette: String = "golda", content: @Composable () -> Unit) {
        compose.setContent { GoldaTheme(palette) { content() } }
        compose.onRoot().captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f)),
        )
    }

    @Test fun home() = shot("home") { HomeScreen(demo(), PaddingValues(), rememberLazyListState(), {}, {}, onEdit = {}) }

    @Test fun homeDark() = shot("home_dark", palette = "midnight") { HomeScreen(demo(), PaddingValues(), rememberLazyListState(), {}, {}, onEdit = {}) }

    @Test fun accounts() = shot("accounts") { AccountsScreen(demo(), PaddingValues(), {}, {}, { _, _, _ -> }) }

    @Test fun insights() = shot("insights") { AnalyticsScreen(demo(), PaddingValues()) {} }

    @Test fun clearTheme() = shot("insights_clear", palette = "clear") { AnalyticsScreen(demo(), PaddingValues()) {} }

    @Test fun entry() = shot("entry") { EntrySheet(demo(), EntryRequest(), {}, {}, {}) }

    @Test fun entryDark() = shot("entry_dark", palette = "midnight") { EntrySheet(demo(), EntryRequest(), {}, {}, {}) }

    @Test fun accountsDark() = shot("accounts_dark", palette = "midnight") { AccountsScreen(demo(), PaddingValues(), {}, {}, { _, _, _ -> }) }

    @Test fun settings() = shot("settings") {
        SettingsScreen(demo(), {}, {}, remember { SnackbarHostState() }, {}, {}, {}, {}, {})
    }

    /** The demo of `Demo.samples`, built in memory: a ruble salary, money moved abroad, two weeks of lari. */
    private fun demo(): AppData {
        val zone = ZoneId.of("UTC")
        val now = today.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val hour = 3_600_000L
        val day = 24 * hour
        val categories = DefaultCategories.all.mapIndexed { i, c -> c.copy(id = i + 1L) }
        val cat = categories.associate { it.key to it.id }
        val accounts = listOf(
            Account(1, "Карта ₽", "RUB", AccountType.CARD, includeInFree = true, sort = 0),
            Account(2, "Накопительный", "RUB", AccountType.SAVINGS, includeInFree = false, interestRate = 12.0, sort = 1),
            Account(3, "Мультивалютная USD", "USD", AccountType.CARD, includeInFree = true, sort = 2),
            Account(4, "Мультивалютная GEL", "GEL", AccountType.CARD, includeInFree = true, sort = 3),
            Account(5, "Наличные ₾", "GEL", AccountType.CASH, includeInFree = true, sort = 4),
            Account(6, "Кредитка", "RUB", AccountType.CREDIT, includeInFree = false, sort = 5, interestRate = 29.9, paymentDay = 25, paymentMinor = 300_000),
            Account(7, "Кредит", "RUB", AccountType.LOAN, includeInFree = false, sort = 6, interestRate = 19.9, paymentDay = 5, paymentMinor = 1_000_000),
        )
        val rateList = listOf(Rate("USD", 92.0, today.toString()), Rate("GEL", 34.0, today.toString()))
        val settings = Settings(
            onboarded = true, displayCurrencies = listOf("RUB", "USD", "GEL"), localCurrency = "GEL", lastAccountId = 4,
            spendAccountId = 4, periodStart = today.minusDays(9).toEpochDay(), periodEnd = today.plusDays(21).toEpochDay(),
        )
        val rates = Rates(rateList.associate { it.code to it.rubPerUnit }, settings.markup)
        val ops = mutableListOf<OperationFull>()
        fun book(d: Draft) {
            val states = Ledger.states(accounts, ops.flatMap { it.postings })
            val id = ops.size + 1L
            val op = Operation(id, d.type, d.timestamp, categoryId = d.categoryId, note = d.note,
                purchaseAmountMinor = d.purchaseAmountMinor, purchaseCurrency = d.purchaseCurrency, isEstimate = d.isEstimate)
            ops += OperationFull(op, Ledger.postings(d, states, rates).map { it.copy(operationId = id) })
        }
        val start = now - 30 * day
        book(Draft(OpType.OPENING, start, 1, 500_000))
        book(Draft(OpType.OPENING, start, 2, 25_000_000))
        book(Draft(OpType.OPENING, start, 6, -1_500_000))
        book(Draft(OpType.OPENING, start, 7, -20_000_000))
        book(Draft(OpType.INCOME, now - 12 * day, 1, 15_840_000, categoryId = cat["salary"]))
        book(Draft(OpType.TRANSFER, now - 12 * day + hour, 1, 4_000_000, toAccountId = 2, toAmountMinor = 4_000_000, note = "В подушку"))
        book(Draft(OpType.TRANSFER, now - 12 * day + 2 * hour, 1, 11_040_000, toAccountId = 3, toAmountMinor = 120_000, note = "Перевод за границу"))
        book(Draft(OpType.TRANSFER, now - 9 * day, 3, 90_000, toAccountId = 4, toAmountMinor = 241_200, note = "Обмен в приложении банка"))
        book(Draft(OpType.TRANSFER, now - 9 * day, 3, 10_000, toAccountId = 5, toAmountMinor = 26_500, note = "Банкомат"))
        fun spend(ago: Long, from: Long, minor: Long, key: String, note: String) =
            book(Draft(OpType.EXPENSE, now - ago, from, minor, categoryId = cat[key], note = note))
        spend(9 * day, 4, 90_000, "housing", "Аренда")
        spend(9 * day, 1, 69_900, "subscriptions", "Музыка и облако")
        spend(8 * day, 4, 6_420, "food", "Продукты")
        spend(8 * day, 5, 1_500, "food", "Хачапури")
        spend(7 * day, 4, 3_500, "telecom", "Мобильный интернет")
        spend(7 * day, 5, 800, "transport", "Метро")
        spend(6 * day, 4, 4_800, "food", "Ужин")
        spend(5 * day, 4, 8_960, "food", "Продукты")
        spend(5 * day, 5, 2_000, "treats", "Музей")
        spend(4 * day, 4, 1_200, "transport", "Такси")
        spend(3 * day, 4, 12_500, "shopping", "Куртка")
        spend(3 * day, 5, 900, "food", "Кофе")
        spend(2 * day, 4, 5_230, "food", "Продукты")
        spend(1 * day, 4, 4_500, "health", "Аптека")
        spend(1 * day, 5, 1_800, "food", "Обед")
        spend(3 * hour, 4, 1_500, "food", "Шаурма")
        spend(1 * hour, 5, 800, "food", "Кофе")
        val payments = listOf(
            Obligation(1, "Музыка и облако", 69_900, "RUB", 15),
            Obligation(2, "Мобильный интернет", 3_500, "GEL", 17),
        )
        return AppData(settings, accounts, ops, categories, rateList, payments, today)
    }
}
