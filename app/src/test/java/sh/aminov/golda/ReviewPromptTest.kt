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

    @Test
    fun dataComparesWithThePeriodBeforeAndShowsHabits() {
        fun at(day: Int, hour: Int) = LocalDate.of(2026, 10, day).atTime(hour, 30).toInstant(zone).toEpochMilli()
        val ops = listOf(
            // October 1 to 7: three coffees in the evening, one big shop on a Saturday.
            spend(1, at(2, 19), 1, 30_000, "Кофе"),
            spend(2, at(3, 20), 1, 30_000, "кофе"),
            spend(3, at(4, 21), 1, 30_000, "Кофе"),
            spend(4, at(3, 11), 1, 300_000, "Ашан"),
            // The seven days before: food for half as much, and a subscription that is gone now.
            OperationFull(Operation(5, OpType.EXPENSE, LocalDate.of(2026, 9, 28).atTime(12, 0).toInstant(zone).toEpochMilli(), 1L), listOf(Posting(operationId = 5, accountId = 1, amountMinor = -195_000, rubMinor = -195_000))),
            OperationFull(Operation(6, OpType.EXPENSE, LocalDate.of(2026, 9, 29).atTime(12, 0).toInstant(zone).toEpochMilli(), 2L), listOf(Posting(operationId = 6, accountId = 1, amountMinor = -99_900, rubMinor = -99_900))),
        )
        val text = ReviewPrompt.data(
            Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7)), ops,
            mapOf(1L to card), mapOf(1L to food, 2L to subs), Rates(emptyMap(), markup = 0.0), today, zone,
            regulars = listOf(sh.aminov.golda.data.Obligation(1, "Netflix", 99_900, "RUB", 6)),
        )
        // The seven days before, and the change against them.
        assertTrue(text, text.contains("с 24 сентября 2026 по 30 сентября 2026"))
        assertTrue(text, text.contains("- Еда (ежедневные)") && text.contains("+100 %"))
        assertTrue(text, text.contains("- Подписки: сейчас ничего"))
        // Weekdays (October 3, 2026 is a Saturday) and the time of day.
        assertTrue(text, Regex("""- сб: 3.?300 ₽, 2 шт\. за 1 дн\.""").containsMatchIn(text))
        assertTrue(text, Regex("""вечер, 18–24: 900 ₽, 3 шт\.""").containsMatchIn(text))
        // The same purchase whatever its case.
        assertTrue(text, Regex("""- кофе: 3 раза?, всего 900 ₽""").containsMatchIn(text))
        assertTrue(text, text.contains("- Netflix: 999 ₽, 6 числа"))
        // Every purchase with its weekday and time.
        assertTrue(text, text.contains("2026-10-03 | сб | 20:30 |"))
    }

    @Test
    fun dataEdgesOfTheHabits() {
        fun at(day: Int, hour: Int, minute: Int = 0) = LocalDate.of(2026, 10, day).atTime(hour, minute).toInstant(zone).toEpochMilli()
        fun tagged(id: Long, time: Long, rub: Long, note: String, tag: String?) =
            OperationFull(Operation(id, OpType.EXPENSE, time, 1L, note = note, tag = tag), listOf(Posting(operationId = id, accountId = 1, amountMinor = -rub, rubMinor = -rub)))
        val ops = listOf(
            // The edges of the parts of the day: 5:59 is night, 6:00 morning, 11:59 morning, 12:00 day, 17:59 day, 18:00 evening.
            spend(1, at(5, 5, 59), 1, 10_000, "a"),
            spend(2, at(5, 6, 0), 1, 20_000, "b"),
            spend(3, at(5, 11, 59), 1, 40_000, "c"),
            spend(4, at(5, 12, 0), 1, 80_000, "d"),
            spend(5, at(5, 17, 59), 1, 160_000, "e"),
            spend(6, at(5, 18, 0), 1, 320_000, "f"),
            // Two Mondays in the period (5 and 12 October): the average is over both.
            spend(7, at(12, 9), 1, 10_000, "g"),
            // Gemini's tag groups what the notes call differently; a blank note and a one-off are not repeats.
            tagged(8, at(6, 10), 5_000, "латте", "кофе"),
            tagged(9, at(7, 10), 5_000, "капучино", "кофе"),
            spend(10, at(8, 10), 1, 1_000, " "),
            spend(11, at(9, 10), 1, 1_000, " "),
        )
        val text = ReviewPrompt.data(
            Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14)), ops,
            mapOf(1L to card), mapOf(1L to food), Rates(emptyMap(), markup = 0.0), today, zone,
        )
        assertTrue(text, Regex("""ночь, 0–6: 100 ₽, 1 шт\.""").containsMatchIn(text))
        assertTrue(text, Regex("""утро, 6–12: 820 ₽, 7 шт\.""").containsMatchIn(text))
        assertTrue(text, Regex("""день, 12–18: 2.?400 ₽, 2 шт\.""").containsMatchIn(text))
        assertTrue(text, Regex("""вечер, 18–24: 3.?200 ₽, 1 шт\.""").containsMatchIn(text))
        // In time order: night first.
        assertTrue(text, text.indexOf("ночь, 0–6") < text.indexOf("утро, 6–12") && text.indexOf("день, 12–18") < text.indexOf("вечер, 18–24"))
        // Mondays: 6 300 + 100 over two Mondays.
        assertTrue(text, Regex("""- пн: 3.?200 ₽, 7 шт\. за 2 дн\.""").containsMatchIn(text))
        assertTrue(text, Regex("""- кофе: 2 раза?, всего 100 ₽, в среднем 50 ₽""").containsMatchIn(text))
        assertFalse(text, text.contains("- a:"))
        assertFalse(text, Regex("""
- : """).containsMatchIn(text))
        // Nothing before to compare with.
        assertTrue(text, text.contains("сравнивать не с чем"))
        assertTrue(text, text.contains("было 0 ₽, раньше не было"))
    }

    @Test
    fun dataShowsFallsAndTheLatestWhenThereAreMany() {
        fun day(d: Int) = LocalDate.of(2026, 10, d).atTime(12, 0).toInstant(zone).toEpochMilli()
        val before = listOf(spend(1, LocalDate.of(2026, 9, 25).atTime(12, 0).toInstant(zone).toEpochMilli(), 1, 400_000, "раньше"))
        val many = (1..ReviewPrompt.MAX_LINES + 5).map { i -> spend(100L + i, day(1 + i % 7) + i, 1, 1_000, "x$i") }
        val text = ReviewPrompt.data(
            Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7)), before + many,
            mapOf(1L to card), mapOf(1L to food), Rates(emptyMap(), markup = 0.0), today, zone,
        )
        // 3 050 ₽ now against 4 000 ₽ before.
        assertTrue(text, text.contains("−24 %") || text.contains("-24 %"))
        assertTrue(text, text.contains("Траты (последние ${ReviewPrompt.MAX_LINES} из ${ReviewPrompt.MAX_LINES + 5}):"))
        val equal = ReviewPrompt.data(
            Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7)),
            listOf(spend(1, day(2), 1, 1_000, "a"), spend(2, LocalDate.of(2026, 9, 26).atTime(12, 0).toInstant(zone).toEpochMilli(), 1, 1_000, "b")),
            mapOf(1L to card), mapOf(1L to food), Rates(emptyMap(), markup = 0.0), today, zone,
        )
        assertTrue(equal, equal.contains("было 10 ₽, 0 %"))
        assertTrue(ReviewPrompt.system.contains("Что заметно"))
    }

    @Test
    fun spanInEnglish() {
        I18n.russian = false
        try {
            assertEquals("October 5 to 12", ReviewPrompt.span(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12)))
            assertEquals("October 5 to November 3", ReviewPrompt.span(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 3)))
        } finally {
            I18n.russian = true
        }
    }
}
