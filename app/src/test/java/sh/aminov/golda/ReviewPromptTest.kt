package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.CategoryKind
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Operation
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Posting
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Period
import sh.aminov.golda.domain.Rates
import sh.aminov.golda.domain.ReviewPrompt
import java.time.LocalDate
import java.time.ZoneOffset

class ReviewPromptTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 3)
    private val card = Account(1, "TBC", "RUB", AccountType.CARD, includeInFree = true)
    private val food = Category(1, "food", "Еда", "", CategoryKind.EXPENSE)
    private val subs = Category(2, "subscriptions", "Подписки", "", CategoryKind.EXPENSE)

    private fun at(month: Int, day: Int) = LocalDate.of(2026, month, day).atTime(12, 0).toInstant(zone).toEpochMilli()
    private fun spend(id: Long, time: Long, category: Long, rub: Long, note: String = "") =
        OperationFull(Operation(id, OpType.EXPENSE, time, category, note = note), listOf(Posting(operationId = id, accountId = 1, amountMinor = -rub, rubMinor = -rub)))

    @Test
    fun titleNamesAWholeMonthOrTheDays() {
        I18n.russian = true
        val october = Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertEquals("октябрь", ReviewPrompt.title(october, today))
        // November so far is still "ноябрь": a month up to today.
        assertEquals("ноябрь", ReviewPrompt.title(Period(LocalDate.of(2026, 11, 1), today), today))
        assertEquals("с 27 октября по 2 ноября", ReviewPrompt.title(Period(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 2)), today))
        assertEquals("с 5 по 12 октября", ReviewPrompt.title(Period(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12)), today))
        // Days up to today are counted, not dated; a review from the list passes a far today to get dates.
        val month = Period(today.minusDays(29), today)
        assertEquals("последние 30 дней", ReviewPrompt.title(month, today))
        assertEquals("с 5 октября по 3 ноября", ReviewPrompt.title(month, LocalDate.MAX))
    }

    @Test
    fun dataHasTheTotalsCategoriesAndOnlyThePeriodsOperations() {
        val ops = listOf(
            spend(1, at(10, 5), 1, 120_000, "Ашан"),
            spend(2, at(10, 6), 2, 99_900, "Netflix"),
            spend(3, at(9, 30), 1, 500_000, "сентябрь"),
        )
        val text = ReviewPrompt.data(
            Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), ops,
            mapOf(1L to card), mapOf(1L to food, 2L to subs), Rates(emptyMap(), markup = 0.0), today, zone,
        )
        assertTrue(text, text.contains(", 31 дн."))
        assertTrue(text, text.contains("- Еда (ежедневные)") && text.contains(", 1 шт."))
        assertTrue(text, text.contains("- Подписки (регулярные)"))
        assertTrue(text, text.contains("| Ашан |") && text.contains("| Netflix |"))
        assertFalse(text, text.contains("сентябрь"))
    }
}
