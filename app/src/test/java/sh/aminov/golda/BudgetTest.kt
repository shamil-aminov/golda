package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Bucket
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.CategoryKind
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Operation
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Posting
import sh.aminov.golda.domain.Budget
import sh.aminov.golda.domain.Buckets
import sh.aminov.golda.domain.Ledger
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.Today
import java.time.LocalDate
import java.time.ZoneOffset

/** "Можно сегодня" as a daily limit, the pace it falls back to, the buckets, and the reserve's runway (SPEC §5). */
class BudgetTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 2)
    private val card = Account(1, "Карта ₽", "RUB", AccountType.CARD, includeInFree = true)
    private val savings = Account(2, "Накопительный", "RUB", AccountType.SAVINGS, includeInFree = false)
    private val loan = Account(3, "Кредит", "RUB", AccountType.LOAN, includeInFree = true, paymentDay = 30, paymentMinor = 1_096_000)
    private val accounts = listOf(card, savings, loan)

    private val groceries = Category(1, "groceries", "Продукты", "", CategoryKind.EXPENSE)
    private val housing = Category(2, "housing", "Жильё", "", CategoryKind.EXPENSE)
    private val clothes = Category(3, "clothes", "Одежда", "", CategoryKind.EXPENSE)
    private val categories = listOf(groceries, housing, clothes).associateBy { it.id }

    private val ops = mutableListOf<OperationFull>()

    private fun at(day: LocalDate) = day.atTime(12, 0).toInstant(zone).toEpochMilli()

    private fun opening(account: Account, rub: Long) {
        val id = ops.size + 1L
        ops += OperationFull(Operation(id, OpType.OPENING, 0), listOf(Posting(id, id, account.id, rub, rub)))
    }

    /** [rub] kopecks spent from [account] on [day]. */
    private fun spend(day: LocalDate, rub: Long, category: Category? = groceries, bucket: Bucket? = null, account: Account = card) {
        val id = ops.size + 1L
        ops += OperationFull(
            Operation(id, OpType.EXPENSE, at(day), categoryId = category?.id, bucket = bucket),
            listOf(Posting(id, id, account.id, -rub, -rub)),
        )
    }

    private fun budget(settings: Settings = Settings()): Today =
        Budget.today(Ledger.states(accounts, ops.flatMap { it.postings }), ops, categories, settings, today, zone)

    @Test
    fun aLimitSetIsWhatTheDayIsMeasuredAgainst() {
        opening(card, 10_000_000)
        spend(today.minusDays(3), 900_000)
        spend(today, 40_000)
        val t = budget(Settings(dailyLimitRub = 150_000))
        assertEquals(150_000, t.limitRub)
        assertFalse(t.autoLimit)
        assertEquals(40_000, t.spentTodayRub)
        assertEquals(110_000, t.leftTodayRub)
    }

    @Test
    fun withoutALimitTheDayFollowsThePace() {
        opening(card, 10_000_000)
        // 1 000 + 2 000 ₽ over the three days since the first purchase: 1 000 ₽ a day.
        spend(today.minusDays(3), 100_000)
        spend(today.minusDays(1), 200_000)
        spend(today, 30_000)
        val t = budget()
        assertTrue(t.autoLimit)
        assertEquals(100_000L, t.paceRub)
        assertEquals(100_000, t.limitRub)
        assertEquals(70_000, t.leftTodayRub)
    }

    @Test
    fun thePaceLeavesTodayOutAndLooksBackThirtyDays() {
        opening(card, 10_000_000)
        spend(today.minusDays(45), 9_000_000) // long before the window
        spend(today.minusDays(30), 300_000)
        spend(today, 5_000_000) // today never moves the pace
        assertEquals(10_000L, budget().paceRub) // 3 000 ₽ over 30 days
    }

    @Test
    fun nothingToMeasureOnTheFirstDay() {
        opening(card, 10_000_000)
        spend(today, 50_000)
        val t = budget()
        assertNull(t.paceRub)
        assertEquals(0, t.limitRub)
        assertEquals(-50_000, t.leftTodayRub)
    }

    @Test
    fun rentDoesNotSinkTheDay() {
        opening(card, 20_000_000)
        spend(today.minusDays(1), 100_000)
        spend(today, 6_144_280, category = housing) // two months of rent
        val t = budget(Settings(dailyLimitRub = 150_000))
        assertEquals(0, t.spentTodayRub)
        assertEquals(150_000, t.leftTodayRub)
        // Spread over at least 30 days, not over the two days of history.
        assertEquals(6_144_280L / 30, t.regularPerDayRub)
    }

    @Test
    fun regularPaymentsAreSpreadOverTheHistoryUpToNinetyDays() {
        opening(card, 20_000_000)
        // Two months of history: the rent of 59 days ago is spread over those 60 days.
        spend(today.minusDays(59), 6_000_000, category = housing)
        assertEquals(6_000_000L / 60, budget().regularPerDayRub)
        // Rent from before the 90-day window no longer counts.
        spend(today.minusDays(90), 6_000_000, category = housing)
        assertEquals(6_000_000L / 90, budget().regularPerDayRub)
    }

    @Test
    fun anExpenseCanLeaveItsCategorysBucket() {
        opening(card, 10_000_000)
        spend(today, 500_000, category = groceries, bucket = Bucket.BIG) // a party's worth, not everyday
        spend(today, 20_000, category = clothes, bucket = Bucket.DAILY) // socks
        assertEquals(20_000, budget().spentTodayRub)
    }

    @Test
    fun bigPurchasesStayOutOfThePace() {
        opening(card, 10_000_000)
        spend(today.minusDays(2), 100_000)
        spend(today.minusDays(1), 3_000_000, category = clothes)
        val t = budget()
        assertEquals(50_000L, t.paceRub)
        assertEquals(0, t.regularPerDayRub)
    }

    @Test
    fun spendingFromAnyAccountCountsToday() {
        opening(savings, 10_000_000)
        spend(today, 30_000, account = savings)
        assertEquals(30_000, budget().spentTodayRub)
    }

    @Test
    fun theReserveLastsAtThePacePlusRegularPayments() {
        opening(card, 9_300_000) // 93 000 ₽
        opening(savings, 50_000_000) // not in the reserve
        spend(today.minusDays(10), 1_000_000) // 1 000 ₽ a day
        val t = budget()
        assertEquals(9_300_000 - 1_000_000, t.reserveRub)
        // 83 000 ₽ / 1 000 ₽ a day.
        assertEquals(83L, t.runwayDays)
        assertEquals(today.plusDays(83), t.runwayUntil(today))
    }

    @Test
    fun aDebtsMonthlyPaymentIsARegularCostAndNotReserve() {
        opening(card, 3_650_000)
        opening(loan, -20_000_000)
        val t = budget(Settings(dailyLimitRub = 100_000))
        // 10 960 ₽ a month is 360 ₽ a day.
        assertEquals(1_096_000L * 12 / 365, t.regularPerDayRub)
        assertEquals(3_650_000, t.reserveRub)
        // No pace yet: the limit stands in. 36 500 ₽ / (1 000 + 360) ₽ a day.
        assertEquals(3_650_000L / (100_000 + 36_000), t.runwayDays)
    }

    @Test
    fun noRunwayWhileNothingIsSpent() {
        opening(card, 1_000_000)
        assertNull(budget().runwayDays)
    }

    @Test
    fun bucketsDefaultByCategoryAndCanBeChanged() {
        assertEquals(Bucket.DAILY, Buckets.default("groceries"))
        assertEquals(Bucket.DAILY, Buckets.default("eating_out"))
        assertEquals(Bucket.DAILY, Buckets.default(null))
        assertEquals(Bucket.REGULAR, Buckets.default("housing"))
        assertEquals(Bucket.REGULAR, Buckets.default("telecom"))
        assertEquals(Bucket.REGULAR, Buckets.default("subscriptions"))
        assertEquals(Bucket.BIG, Buckets.default("clothes"))
        assertEquals(Bucket.BIG, Buckets.default("travel"))
        assertEquals(Bucket.REGULAR, Buckets.of(groceries.copy(bucket = Bucket.REGULAR)))
        assertEquals(Bucket.DAILY, Buckets.of(null))
    }
}
