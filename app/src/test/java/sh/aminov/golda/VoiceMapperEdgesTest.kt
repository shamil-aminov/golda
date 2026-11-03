package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import sh.aminov.golda.domain.Draft
import sh.aminov.golda.domain.Rates
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.VoiceAction
import sh.aminov.golda.domain.VoiceItem
import sh.aminov.golda.domain.VoiceMapper
import sh.aminov.golda.domain.VoiceResult
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * What happens when the model's answer is incomplete or odd: nothing is booked from a guess the
 * app can't stand behind, and what can be worked out is marked as an estimate.
 */
class VoiceMapperEdgesTest {
    private val rates = Rates(mapOf("USD" to 80.0, "GEL" to 30.0), markup = 0.0)
    private val rub = Account(1, "Карта ₽", "RUB", AccountType.CARD, includeInFree = true)
    private val usd = Account(2, "Доллары", "USD", AccountType.CARD, includeInFree = true)
    private val gel = Account(3, "Наличные", "GEL", AccountType.CASH, includeInFree = true)
    private val accounts = listOf(rub, usd, gel)
    private val food = Category(7, "eating_out", "Еда вне дома", "🍔", CategoryKind.EXPENSE)
    private val settings = Settings(localCurrency = "GEL")
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 2)
    private val now = today.atTime(20, 0).toInstant(zone).toEpochMilli()

    private fun item(
        intent: String, amount: String?, currency: String? = null, note: String = "", category: String? = null,
        account: Long? = null, to: Long? = null, toAmount: String? = null, date: String? = null,
    ) = VoiceItem(intent, amount, currency, note, category, account?.toString(), to?.toString(), toAmount, date)

    private fun one(i: VoiceItem, transcript: String = "…", history: Map<String, Long> = emptyMap(), s: Settings = settings): VoiceAction =
        VoiceMapper.actions(VoiceResult(transcript, listOf(i)), accounts, listOf(food), s, rates, now, zone, history).single()

    private fun draft(i: VoiceItem, history: Map<String, Long> = emptyMap()): Draft = (one(i, history = history) as VoiceAction.Record).draft
    private fun notUnderstood(a: VoiceAction) = assertTrue("$a", a is VoiceAction.NotUnderstood)

    @Test
    fun noAmountNoBooking() {
        for (intent in listOf("expense", "income", "transfer")) notUnderstood(one(item(intent, null, "GEL", to = rub.id)))
        notUnderstood(one(item("expense", "0", "GEL")))
        notUnderstood(one(item("expense", "abc", "GEL")))
        notUnderstood(one(item("unknown", "15", "GEL")))
        // An empty answer says what was heard.
        assertEquals(VoiceAction.NotUnderstood("шум"), VoiceMapper.actions(VoiceResult("шум", emptyList()), accounts, listOf(food), settings, rates, now, zone).single())
    }

    @Test
    fun aTransferNeedsSomewhereElseToGo() {
        notUnderstood(one(item("transfer", "10", "GEL", account = gel.id)))
        notUnderstood(one(item("transfer", "10", "GEL", account = gel.id, to = gel.id)))
        notUnderstood(one(item("transfer", "10", "GEL", account = gel.id, to = 99)))
    }

    @Test
    fun aTransferBetweenCurrencies() {
        // Said both sides: nothing guessed.
        val said = draft(item("transfer", "100", "USD", account = usd.id, to = gel.id, toAmount = "260"))
        assertEquals(10_000L, said.amountMinor)
        assertEquals(26_000L, said.toAmountMinor)
        assertFalse(said.isEstimate)
        // Only what was sent: what arrived worked out at the display rate, and marked.
        val half = draft(item("transfer", "100", "USD", account = usd.id, to = gel.id))
        assertEquals(Math.round(10_000 * 80.0 / 30.0), half.toAmountMinor)
        assertTrue(half.isEstimate)
        // Same currency: what arrived is what was sent, no estimate.
        val usd2 = Account(4, "Доллары 2", "USD", AccountType.CARD, includeInFree = true)
        val same = (VoiceMapper.actions(VoiceResult("…", listOf(item("transfer", "5", "USD", account = usd.id, to = usd2.id))), accounts + usd2, listOf(food), settings, rates, now, zone).single() as VoiceAction.Record).draft
        assertEquals(500L, same.toAmountMinor)
        assertFalse(same.isEstimate)
        // Named in another currency than the account's: sent converted, and marked.
        val other = draft(item("transfer", "30", "GEL", account = usd.id, to = rub.id, toAmount = "800"))
        assertEquals(Math.round(3_000 * 30.0 / 80.0), other.amountMinor)
        assertEquals(80_000L, other.toAmountMinor)
        assertTrue(other.isEstimate)
        // A received amount of nothing is ignored, not booked as zero.
        val zero = draft(item("transfer", "100", "USD", account = usd.id, to = gel.id, toAmount = "0"))
        assertTrue(zero.toAmountMinor!! > 0)
    }

    @Test
    fun aCurrencyWithoutARateIsNotGuessed() {
        // 15 euros from the lari cash: no euro rate, so no charge to book.
        notUnderstood(one(item("expense", "15", "EUR", account = gel.id)))
        notUnderstood(one(item("income", "15", "EUR", account = gel.id)))
        notUnderstood(one(item("transfer", "15", "EUR", account = gel.id, to = rub.id)))
        notUnderstood(one(item("transfer", "15", "GEL", account = gel.id, to = Account(9, "€", "EUR", AccountType.CARD, includeInFree = true).id)))
    }

    @Test
    fun incomeInAnotherCurrencyIsConvertedAndKept() {
        val d = draft(item("income", "100", "USD", account = rub.id))
        assertEquals(OpType.INCOME, d.type)
        assertEquals(800_000L, d.amountMinor)
        assertEquals(10_000L, d.purchaseAmountMinor)
        assertEquals("USD", d.purchaseCurrency)
        assertTrue(d.isEstimate)
        val plain = draft(item("income", "100", "GEL"))
        assertEquals(gel.id, plain.accountId)
        assertFalse(plain.isEstimate)
        assertNull(plain.purchaseCurrency)
    }

    @Test
    fun theCurrencyCodeMustLookLikeOne() {
        // Lower case is fine; anything not three letters falls back to the local currency.
        assertEquals(usd.id, draft(item("expense", "5", "usd")).accountId)
        assertEquals(gel.id, draft(item("expense", "5", "LARI")).accountId)
        assertEquals(gel.id, draft(item("expense", "5", "")).accountId)
    }

    @Test
    fun aDaySaidMovesItBackNeverForward() {
        // Yesterday: noon of that day.
        val yesterday = draft(item("expense", "5", "GEL", date = today.minusDays(1).toString()))
        assertEquals(today.minusDays(1).atTime(LocalTime.NOON).toInstant(zone).toEpochMilli(), yesterday.timestamp)
        // Today or a day ahead (a mishearing): when it was recorded.
        assertEquals(now, draft(item("expense", "5", "GEL", date = today.toString())).timestamp)
        assertEquals(now, draft(item("expense", "5", "GEL", date = today.plusDays(3).toString())).timestamp)
        assertEquals(now, draft(item("expense", "5", "GEL", date = "вчера")).timestamp)
    }

    @Test
    fun aCategoryFromTheListOrFromHistory() {
        assertEquals(food.id, draft(item("expense", "5", "GEL", "кофе", category = "eating_out")).categoryId)
        // Not a key from the list, nothing remembered: none.
        assertNull(draft(item("expense", "5", "GEL", "кофе", category = "made_up")).categoryId)
        // Remembered for this note (case and spaces aside) and still there: that one.
        assertEquals(food.id, draft(item("expense", "5", "GEL", "Кофе"), history = mapOf("кофе" to food.id)).categoryId)
        // Remembered, but the category is gone: none.
        assertNull(draft(item("expense", "5", "GEL", "кофе"), history = mapOf("кофе" to 404L)).categoryId)
    }

    @Test
    fun historyKeepsTheNewestCategoryPerNote() {
        fun op(id: Long, ts: Long, note: String, cat: Long?) = OperationFull(Operation(id, OpType.EXPENSE, ts, categoryId = cat, note = note), emptyList())
        val h = VoiceMapper.history(
            listOf(
                op(1, 30, " Кофе ", 2),
                op(2, 10, "кофе", 1),
                op(3, 20, "чай", null),
                op(4, 40, "  ", 5),
            ),
        )
        assertEquals(mapOf("кофе" to 2L), h)
    }

    @Test
    fun theAccountAPurchaseComesFrom() {
        val credit = Account(4, "Кредитка", "GEL", AccountType.CREDIT, includeInFree = false)
        val savings = Account(5, "Вклад", "GEL", AccountType.SAVINGS, includeInFree = false)
        // The last used, when in this currency.
        assertEquals(gel, VoiceMapper.pick(listOf(credit, gel), "GEL", Settings(lastAccountId = gel.id)))
        // Counted for spending before the rest.
        assertEquals(gel, VoiceMapper.pick(listOf(savings, credit, gel), "GEL", Settings()))
        // Then not a credit card.
        assertEquals(savings, VoiceMapper.pick(listOf(credit, savings), "GEL", Settings()))
        // Then a credit card.
        assertEquals(credit, VoiceMapper.pick(listOf(credit, usd), "GEL", Settings()))
        // Nothing in this currency: the last used anyway, converted.
        assertEquals(usd, VoiceMapper.pick(listOf(rub, usd), "GEL", Settings(lastAccountId = usd.id)))
        // Else the first spendable one, else the first.
        assertEquals(usd, VoiceMapper.pick(listOf(savings.copy(currency = "RUB"), usd), "GEL", Settings()))
        val loan = Account(6, "Кредит", "RUB", AccountType.LOAN, includeInFree = false)
        assertEquals(loan, VoiceMapper.pick(listOf(loan), "GEL", Settings()))
        assertNull(VoiceMapper.pick(emptyList(), "GEL", Settings()))
        // Spent from: counted per account, expenses only.
        val ops = listOf(
            OperationFull(Operation(1, OpType.EXPENSE, 0), listOf(Posting(1, 1, gel.id, -100, 0))),
            OperationFull(Operation(2, OpType.EXPENSE, 0), listOf(Posting(2, 2, gel.id, -100, 0))),
            OperationFull(Operation(3, OpType.INCOME, 0), listOf(Posting(3, 3, rub.id, 100, 100))),
        )
        assertEquals(mapOf(gel.id to 2), VoiceMapper.spentFrom(ops))
    }

    @Test
    fun aPurchaseFromAnAccountInAnotherCurrencyIsAnEstimatedCharge() {
        val d = draft(item("expense", "30", "GEL", account = usd.id))
        assertEquals(usd.id, d.accountId)
        // The card's charge: the official cross rate plus its 2 %.
        assertEquals(rates.cardCharge(3_000, "GEL", "USD"), d.amountMinor)
        assertEquals(3_000L, d.purchaseAmountMinor)
        assertTrue(d.isEstimate)
        // With no account in that currency and nothing to go by: still booked, converted.
        val noAccount = VoiceMapper.actions(VoiceResult("…", listOf(item("expense", "30", "GEL"))), listOf(usd), listOf(food), settings, rates, now, zone).single()
        assertTrue(noAccount is VoiceAction.Record)
        // No accounts at all: nothing to book.
        notUnderstood(VoiceMapper.actions(VoiceResult("…", listOf(item("expense", "30", "GEL"))), emptyList(), listOf(food), settings, rates, now, zone).single())
        notUnderstood(VoiceMapper.actions(VoiceResult("…", listOf(item("income", "30", "GEL"))), emptyList(), listOf(food), settings, rates, now, zone).single())
        notUnderstood(VoiceMapper.actions(VoiceResult("…", listOf(item("transfer", "30", "GEL", to = 1))), emptyList(), listOf(food), settings, rates, now, zone).single())
    }
}
