package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.CategoryKind
import sh.aminov.golda.data.Obligation
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.Operation
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.Posting
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.domain.Regulars
import java.time.LocalDate
import java.time.ZoneOffset

class RegularsTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 3)
    private val card = Account(1, "TBC", "GEL", AccountType.CARD, includeInFree = true)
    private val categories = listOf(
        Category(1, "food", "Еда", "", CategoryKind.EXPENSE),
        Category(2, "subscriptions", "Подписки", "", CategoryKind.EXPENSE),
        Category(3, "telecom", "Связь", "", CategoryKind.EXPENSE),
        Category(4, "loans", "Кредиты", "", CategoryKind.EXPENSE),
    ).associateBy { it.id }

    private fun spend(id: Long, date: LocalDate, category: Long, minor: Long, note: String, said: Pair<Long, String>? = null) = OperationFull(
        Operation(id, OpType.EXPENSE, date.atTime(12, 0).toInstant(zone).toEpochMilli(), category, note = note, purchaseAmountMinor = said?.first, purchaseCurrency = said?.second),
        listOf(Posting(operationId = id, accountId = 1, amountMinor = -minor, rubMinor = -minor * 30)),
    )

    @Test
    fun remindsTwoDaysAheadAndOnTheDay() {
        I18n.russian = true
        val netflix = Obligation(1, "Netflix", 1_200, "USD", dayOfMonth = 5)
        assertEquals("5 ноября спишется 12 \$. Ещё пользуешься?", Regulars.reminder(netflix, today))
        assertEquals("Сегодня спишется 12 \$", Regulars.reminder(netflix, LocalDate.of(2026, 11, 5)))
        assertNull(Regulars.reminder(netflix, LocalDate.of(2026, 11, 4)))
        // A free trial says when it ends instead of an amount.
        assertEquals("5 ноября кончается пробный период. Ещё пользуешься?", Regulars.reminder(netflix.copy(amountMinor = 0), today))
        // The 31st in a 30-day month is charged on the 30th.
        assertEquals("30 ноября спишется 12 \$. Ещё пользуешься?", Regulars.reminder(netflix.copy(dayOfMonth = 31), LocalDate.of(2026, 11, 28)))
    }

    @Test
    fun upcomingIsTheNextTwoDaysSoonestFirst() {
        I18n.russian = true
        val payments = listOf(
            Obligation(1, "Сервер", 1_000, "RUB", 5),
            Obligation(2, "Связь", 1_000, "GEL", 3),
            Obligation(3, "Интернет", 4_300, "GEL", 20),
        )
        assertEquals(listOf("Связь" to 0L, "Сервер" to 2L), Regulars.upcoming(payments, today).map { it.first.name to it.second })
        assertEquals("Спишется послезавтра", Regulars.whenLabel(2))
    }

    @Test
    fun suggestsRecentRegularSpendingNotInTheList() {
        I18n.russian = true
        val ops = listOf(
            spend(1, LocalDate.of(2026, 10, 14), 2, 3_000, "сервер"),
            spend(2, LocalDate.of(2026, 9, 14), 2, 2_900, "сервер"),
            spend(3, LocalDate.of(2026, 10, 9), 2, 1_000, "подписка вольт", said = 1_000L to "USD"),
            spend(4, LocalDate.of(2026, 10, 20), 3, 4_000, "Интернет"),
            spend(5, LocalDate.of(2026, 10, 10), 4, 750_000, "платёж"),
            spend(6, LocalDate.of(2026, 10, 11), 1, 5_000, "продукты"),
            spend(7, LocalDate.of(2026, 7, 1), 2, 999, "старое"),
        )
        val known = listOf(Obligation(9, "интернет", 4_000, "GEL", 20))
        val found = Regulars.suggest(ops, mapOf(1L to card), categories, known, today, zone)
        // The latest of each, its own day and amount, the said currency first; no loans, food,
        // anything older than two months or already listed.
        assertEquals(
            listOf(Obligation(name = "Сервер", amountMinor = 3_000, currency = "GEL", dayOfMonth = 14), Obligation(name = "Подписка вольт", amountMinor = 1_000, currency = "USD", dayOfMonth = 9)),
            found,
        )
    }
}
