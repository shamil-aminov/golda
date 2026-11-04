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
import sh.aminov.golda.domain.Buckets
import sh.aminov.golda.domain.Envelopes
import sh.aminov.golda.domain.Settings
import java.time.LocalDate
import java.time.ZoneOffset

/** "Можно сегодня" as the spending card's money over the period, and the burn measured by its balance (SPEC §5). */
class BudgetTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 2)
    private val tbc = Account(1, "TBC", "GEL", AccountType.CARD, includeInFree = true)
    private val alfa = Account(2, "Альфа", "RUB", AccountType.SAVINGS, includeInFree = false)
    private val credit = Account(3, "Кредитка", "RUB", AccountType.CREDIT, includeInFree = false)
    private val accounts = listOf(tbc, alfa, credit)
    private val groceries = Category(1, "groceries", "Продукты", "", CategoryKind.EXPENSE)

    private val ops = mutableListOf<OperationFull>()

    private fun at(day: LocalDate) = day.atTime(12, 0).toInstant(zone).toEpochMilli()

    private fun op(type: OpType, day: LocalDate, vararg postings: Pair<Account, Long>) {
        val id = ops.size + 1L
        ops += OperationFull(Operation(id, type, at(day)), postings.map { (account, minor) -> Posting(0, id, account.id, minor, minor) })
    }

    /** 60 000 ₽ from the savings to 1 800 ₾ on the card. */
    private fun topUp(day: LocalDate, lari: Long = 180_000) = op(OpType.TRANSFER, day, alfa to -6_000_000, tbc to lari)
    private fun spend(day: LocalDate, lari: Long, account: Account = tbc) = op(OpType.EXPENSE, day, account to -lari)

    private fun envelope(settings: Settings = Settings()) = Envelopes.current(accounts, ops, settings, today, zone)!!

    @Test
    fun thePeriodStartsAtTheLastTopUpAndLastsAMonth() {
        op(OpType.OPENING, today.minusDays(60), alfa to 30_000_000)
        topUp(today.minusDays(40))
        topUp(today.minusDays(8))
        spend(today.minusDays(5), 100)
        val e = envelope()
        assertEquals(tbc, e.account)
        assertEquals(today.minusDays(8), e.start)
        assertEquals(today.minusDays(8).plusMonths(1), e.end)
    }

    @Test
    fun todaysShareIsTheMoneyOverTheDaysLeft() {
        topUp(today.minusDays(8))
        spend(today.minusDays(3), 56_300) // 563 ₾ gone, never itemised
        spend(today, 2_000)
        val e = envelope(Settings(periodStart = today.minusDays(8).toEpochDay(), periodEnd = today.plusDays(21).toEpochDay()))
        assertEquals(121_700, e.balanceMinor) // 1 217 ₾ left
        assertEquals(22, e.daysLeft) // today to the end, both counted
        // This morning's 1 237 ₾ over 22 days: 56,22 ₾, of which 20 ₾ is gone.
        assertEquals(123_700L / 22, e.perDayMinor)
        assertEquals(123_700L / 22 - 2_000, e.leftTodayMinor)
    }

    @Test
    fun theBurnIsWhatLeftTheCardReconciliationsIncluded() {
        topUp(today.minusDays(8))
        spend(today.minusDays(6), 10_000)
        op(OpType.ADJUSTMENT, today.minusDays(1), tbc to -53_000) // reconciled: 530 ₾ not written down
        op(OpType.TRANSFER, today.minusDays(2), tbc to -20_000, alfa to 700_000) // moved away, not spent
        op(OpType.ADJUSTMENT, today.minusDays(2), tbc to 500) // found 5 ₾: not negative spending
        val e = envelope()
        assertEquals(63_000, e.spentMinor)
        // 630 ₾ over the 9 days since the top-up, today included.
        assertEquals(7_000L, e.burnMinor)
        assertEquals((180_000L - 63_000 - 20_000 + 500) / 7_000, e.runwayDays)
    }

    @Test
    fun aFastBurnRunsOutBeforeTheEnd() {
        topUp(today.minusDays(9))
        spend(today.minusDays(5), 100_000) // 1 000 ₾ in ten days: 100 ₾ a day
        val e = envelope()
        assertEquals(10_000L, e.burnMinor)
        assertEquals(8L, e.runwayDays) // 800 ₾ at 100 ₾ a day
        assertEquals(today.plusDays(8), e.runsOut(today))
        assertFalse(e.lasts(today))
        // Looking ahead: 8 days of money for the days still to come.
        assertEquals(today.minusDays(9).plusMonths(1).toEpochDay() - today.toEpochDay() + 1, e.daysLeft)
        // Meant for a month, it lasts the 9 days gone plus 8 more.
        assertEquals(Envelopes.Length.MONTH, e.length)
        assertEquals(today.minusDays(9).let { it.until(it.plusMonths(1), java.time.temporal.ChronoUnit.DAYS) }, e.plannedDays)
        assertEquals(17L, e.lastsDays(today))
        // A slow one lasts.
        val slow = Envelopes.of(tbc, today.minusDays(9), today.plusDays(5), ops, today, zone)
        assertTrue(slow.lasts(today)) // 8 days of money for the 6 to come
        assertFalse(Envelopes.of(tbc, today.minusDays(9), today.plusDays(8), ops, today, zone).lasts(today)) // 8 for 9
    }

    @Test
    fun aBigPurchaseLowersTheBalanceButNotThePace() {
        val clothes = Category(2, "clothes", "Одежда", "", CategoryKind.EXPENSE)
        val categories = listOf(groceries, clothes).associateBy { it.id }
        topUp(today.minusDays(6))
        // 7 days at 50 ₾, and one 300 ₾ purchase on the first.
        (0..6L).forEach { spend(today.minusDays(it), 5_000) }
        ops += OperationFull(
            Operation(ops.size + 1L, OpType.EXPENSE, at(today.minusDays(6)), categoryId = clothes.id),
            listOf(Posting(0, ops.size + 1L, tbc.id, -30_000, -30_000)),
        )
        val e = Envelopes.current(accounts, ops, Settings(), today, zone, categories)!!
        assertEquals(180_000L - 35_000 - 30_000, e.balanceMinor)
        assertEquals(65_000L, e.spentMinor)
        assertEquals(5_000L, e.burnMinor)
        assertEquals(23L, e.runwayDays) // 1 150 ₾ at 50 a day
        // Marked big on the operation itself, a grocery run is left out too.
        ops += OperationFull(
            Operation(ops.size + 1L, OpType.EXPENSE, at(today), categoryId = groceries.id, bucket = Bucket.BIG),
            listOf(Posting(0, ops.size + 1L, tbc.id, -7_000, -7_000)),
        )
        assertEquals(5_000L, Envelopes.current(accounts, ops, Settings(), today, zone, categories)!!.burnMinor)
    }

    @Test
    fun noBurnBeforeAnythingIsSpent() {
        topUp(today)
        val e = envelope()
        assertNull(e.burnMinor)
        assertNull(e.runwayDays)
        assertNull(e.lastsDays(today))
        assertTrue(e.lasts(today))
        assertEquals(today.plusMonths(1), e.end)
    }

    @Test
    fun aPeriodSetIsKeptOnlyWhileItLasts() {
        topUp(today.minusDays(3))
        val set = Settings(periodStart = today.minusDays(3).toEpochDay(), periodEnd = today.plusDays(4).toEpochDay())
        assertEquals(today.plusDays(4), envelope(set).end)
        // Over: the next one of the same 19 days, from where it ended. A top-up does not move it.
        val over = Settings(periodStart = today.minusDays(20).toEpochDay(), periodEnd = today.minusDays(1).toEpochDay())
        assertEquals(today.minusDays(1), envelope(over).start)
        assertEquals(today.plusDays(18), envelope(over).end)
    }

    @Test
    fun aMonthSetRollsOnAsMonthsAndMoneyComingInDoesNotMoveIt() {
        val set = Settings(periodStart = LocalDate.of(2026, 8, 28).toEpochDay(), periodEnd = LocalDate.of(2026, 9, 28).toEpochDay())
        // A friend's money after the month was set: income, and the month stays.
        op(OpType.INCOME, today.minusDays(1), tbc to 10_000)
        val e = envelope(set)
        assertEquals(LocalDate.of(2026, 10, 28), e.start)
        assertEquals(LocalDate.of(2026, 11, 28), e.end)
    }

    @Test
    fun aTopUpStartingANewMonthKeepsTheLengthSetBefore() {
        val day = LocalDate.of(2026, 11, 4)
        assertEquals(day.plusMonths(1).toEpochDay(), Envelopes.startingOn(day, Settings()).periodEnd)
        val twoWeeks = Settings(periodStart = day.minusWeeks(3).toEpochDay(), periodEnd = day.minusWeeks(1).toEpochDay())
        assertEquals(day.plusWeeks(2).toEpochDay(), Envelopes.startingOn(day, twoWeeks).periodEnd)
        val tenDays = Settings(periodStart = day.minusDays(20).toEpochDay(), periodEnd = day.minusDays(10).toEpochDay())
        assertEquals(day.toEpochDay(), Envelopes.startingOn(day, tenDays).periodStart)
        assertEquals(day.plusDays(10).toEpochDay(), Envelopes.startingOn(day, tenDays).periodEnd)
        // The card picked stays the card.
        assertEquals(4L, Envelopes.startingOn(day, tenDays.copy(spendAccountId = 4)).spendAccountId)
    }

    @Test
    fun theSpendingCardIsTheOneSetOrTheOneMostSpentFrom() {
        spend(today, 100, alfa)
        spend(today, 100)
        spend(today, 100)
        spend(today, 100, credit)
        spend(today, 100, credit)
        spend(today, 100, credit)
        assertEquals(tbc, envelope().account) // a credit card is never it
        assertEquals(alfa, envelope(Settings(spendAccountId = alfa.id)).account)
    }

    @Test
    fun noTopUpStartsTheMonthToday() {
        op(OpType.OPENING, today.minusDays(10), tbc to 50_000)
        assertEquals(today, envelope().start)
        assertEquals(50_000L / (today.plusMonths(1).toEpochDay() - today.toEpochDay() + 1), envelope().perDayMinor)
    }

    @Test
    fun theEdgesOfThePeriodCount() {
        topUp(today.minusDays(4))
        spend(today.minusDays(4), 1_000) // on the top-up day itself
        topUp(today.plusDays(1)) // dated tomorrow: not a top-up yet
        // The last day of a period set is still in it, and is the one day left.
        val lastDay = Settings(periodStart = today.minusDays(4).toEpochDay(), periodEnd = today.toEpochDay())
        val e = envelope(lastDay)
        assertEquals(today, e.end)
        assertEquals(1L, e.daysLeft)
        assertEquals(e.balanceMinor, e.perDayMinor)
        assertEquals(1_000L, e.spentMinor)
        assertEquals(1_000L / 5, e.burnMinor)
        // A period that starts today is in force too.
        val fresh = Settings(periodStart = today.toEpochDay(), periodEnd = today.plusDays(6).toEpochDay())
        assertEquals(today, envelope(fresh).start)
        // Without one, the month runs from the top-up four days ago, not from tomorrow's.
        assertEquals(today.minusDays(4), envelope().start)
    }

    @Test
    fun periodLengths() {
        val start = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 7), Envelopes.end(start, Envelopes.Length.WEEK))
        assertEquals(LocalDate.of(2026, 2, 14), Envelopes.end(start, Envelopes.Length.TWO_WEEKS))
        assertEquals(LocalDate.of(2026, 2, 28), Envelopes.end(start, Envelopes.Length.MONTH))
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
        assertEquals(Bucket.BIG, Buckets.default("health"))
        // The twelve since 0.17: four in each bucket.
        assertEquals(Bucket.DAILY, Buckets.default("food"))
        assertEquals(Bucket.DAILY, Buckets.default("treats"))
        assertEquals(Bucket.REGULAR, Buckets.default("loans"))
        assertEquals(Bucket.BIG, Buckets.default("shopping"))
        assertEquals(Bucket.BIG, Buckets.default("other"))
        assertEquals(Bucket.REGULAR, Buckets.of(groceries.copy(bucket = Bucket.REGULAR)))
        assertEquals(Bucket.DAILY, Buckets.of(null))
        val own = Operation(1, OpType.EXPENSE, 0, categoryId = groceries.id, bucket = Bucket.BIG)
        assertEquals(Bucket.BIG, Buckets.of(own, mapOf(groceries.id to groceries)))
    }
}
