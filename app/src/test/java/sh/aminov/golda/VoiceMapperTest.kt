package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.CategoryKind
import sh.aminov.golda.data.OpType
import sh.aminov.golda.domain.Rates
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.VoiceAction
import sh.aminov.golda.domain.VoiceItem
import sh.aminov.golda.domain.VoiceMapper
import sh.aminov.golda.domain.VoicePrompt
import sh.aminov.golda.domain.VoiceResult
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class VoiceMapperTest {
    private val rates = Rates(mapOf("USD" to 83.2454, "GEL" to 31.9597, "THB" to 2.47438), markup = 0.10)
    private val rubCard = Account(1, "Карта ₽", "RUB", AccountType.CARD, includeInFree = true, sort = 0)
    private val multiUsd = Account(2, "Мультивалютная USD", "USD", AccountType.CARD, includeInFree = true, sort = 1)
    private val cash = Account(3, "Наличные", "GEL", AccountType.CASH, includeInFree = true, sort = 2)
    private val accounts = listOf(rubCard, multiUsd, cash)
    private val food = Category(7, "eating_out", "Еда вне дома", "🍔", CategoryKind.EXPENSE)
    private val settings = Settings(localCurrency = "GEL", lastAccountId = multiUsd.id, displayCurrencies = listOf("RUB", "USD", "GEL", "THB"))
    private val zone = ZoneOffset.UTC
    private val now = LocalDate.of(2026, 10, 2).atTime(20, 0).toInstant(zone).toEpochMilli()

    private fun item(intent: String, amount: String?, currency: String?, note: String = "", vararg extra: Pair<String, String>) = VoiceItem(
        intent = intent, amount = amount, currency = currency, note = note,
        category = extra.toMap()["category"], accountId = extra.toMap()["account"], toAccountId = extra.toMap()["to"],
        toAmount = extra.toMap()["toAmount"], date = extra.toMap()["date"],
    )

    private fun map(vararg items: VoiceItem, s: Settings = settings, list: List<Account> = accounts) =
        VoiceMapper.actions(VoiceResult("…", items.toList()), list, listOf(food), s, rates, now, zone)

    @Test
    fun lariGoToTheLariAccountEvenIfTheLastOneWasDollars() {
        val draft = (map(item("expense", "15", "GEL", "шаурма", "category" to "eating_out")).single() as VoiceAction.Record).draft
        assertEquals(cash.id, draft.accountId)
        assertEquals(1_500L, draft.amountMinor)
        assertEquals(food.id, draft.categoryId)
        assertEquals("Шаурма", draft.note)
        assertEquals(now, draft.timestamp)
    }

    @Test
    fun noCurrencyMeansLocalCurrency() {
        val draft = (map(item("expense", "8", null, "кофе")).single() as VoiceAction.Record).draft
        assertEquals(cash.id, draft.accountId)
        assertEquals(800L, draft.amountMinor)
    }

    @Test
    fun bahtFromADollarCardIsAnEstimatedCharge() {
        val draft = (map(item("expense", "80", "THB", "пад тай")).single() as VoiceAction.Record).draft
        assertEquals(multiUsd.id, draft.accountId)
        assertEquals(243L, draft.amountMinor)
        assertEquals(8_000L, draft.purchaseAmountMinor)
        assertEquals("THB", draft.purchaseCurrency)
        assertTrue(draft.isEstimate)
    }

    @Test
    fun twoThingsInOnePhrase() {
        val actions = map(item("expense", "8", "GEL", "кофе"), item("expense", "6", "GEL", "круассан"))
        assertEquals(listOf(800L, 600L), actions.map { (it as VoiceAction.Record).draft.amountMinor })
    }

    @Test
    fun wantingIsNoLongerAnIntent() {
        // "Сомневаюсь" is gone: an old model reply with "consider" books nothing.
        assertTrue(map(item("consider", "50", "USD", "бургер")).single() is VoiceAction.NotUnderstood)
    }

    private fun said(transcript: String, vararg items: VoiceItem, categories: List<Category> = listOf(food), history: Map<String, Long> = emptyMap()) =
        VoiceMapper.actions(VoiceResult(transcript, items.toList()), accounts, categories, settings, rates, now, zone, history)
            .map { (it as VoiceAction.Record).draft }

    @Test
    fun aSeparatorTheModelLostIsPutBackFromTheWords() {
        // From a real export: "80,84 продукт" was booked as 8,84 ₾.
        val draft = said("80,84 продукт", item("expense", "8.84", "GEL", "продукты")).single()
        assertEquals(8_084L, draft.amountMinor)
        assertTrue(draft.isEstimate)
        // And the other way round: "10.50" read as 1050.
        assertEquals(1_050L, said("кофе 10.50", item("expense", "1050", "GEL", "кофе")).single().amountMinor)
    }

    @Test
    fun anAmountTheWordsBackUpStands() {
        val draft = said("Хачапури 17,48", item("expense", "17.48", "GEL", "хачапури")).single()
        assertEquals(1_748L, draft.amountMinor)
        assertEquals(false, draft.isEstimate)
        // The recognizer wrote "1850" for 18.50: the model's 18.50 stands, not 1 850 ₾.
        assertEquals(1_850L, said("столовая 1850", item("expense", "18.5", "GEL", "столовая")).single().amountMinor)
        // Lari and tetri, and tetri alone.
        assertEquals(1_120L, said("такси 11 лари 20 тетри", item("expense", "11.2", "GEL", "такси")).single().amountMinor)
        assertEquals(70L, said("проезд в автобусе 70 тетри", item("expense", "0.7", "GEL", "автобус")).single().amountMinor)
        // Amounts in words are the model's to read.
        assertEquals(false, said("кэшбэк ноль запятая тридцать шесть", item("income", "0.36", "GEL", "кэшбэк")).single().isEstimate)
    }

    @Test
    fun withSeveralAmountsADoubtfulOneIsOnlyMarked() {
        val drafts = said("9 лари выпечка, 2 лари кока-кола", item("expense", "9", "GEL", "выпечка"), item("expense", "20", "GEL", "кока-кола"))
        assertEquals(listOf(900L, 2_000L), drafts.map { it.amountMinor })
        assertEquals(listOf(false, true), drafts.map { it.isEstimate })
    }

    @Test
    fun aFractionSaidIsNeverReadAsTheWholeNumber() {
        // "10.50" said and 10 returned: the separator in the words decides.
        assertEquals(1_050L, said("кофе 10.50", item("expense", "10", "GEL", "кофе")).single().amountMinor)
    }

    @Test
    fun historyRemembersTheLatestCategoryOfEachNote() {
        fun op(id: Long, time: Long, note: String, category: Long?) =
            sh.aminov.golda.data.OperationFull(sh.aminov.golda.data.Operation(id, OpType.EXPENSE, time, category, note = note), emptyList())
        val history = VoiceMapper.history(
            listOf(op(2, 20, "Кола", 5), op(1, 10, "Кола", 4), op(3, 30, "  ", 6), op(4, 40, "Хлеб", null)),
        )
        assertEquals(mapOf("кола" to 5L), history)
    }

    @Test
    fun anItemLeftWithoutACategoryTakesTheOneItsNoteHadBefore() {
        val groceries = Category(8, "groceries", "Продукты", "🛒", CategoryKind.EXPENSE)
        val drafts = said(
            "9 лари выпечка, 2 лари кока-кола",
            item("expense", "9", "GEL", "выпечка"), item("expense", "2", "GEL", "кока-кола"),
            categories = listOf(food, groceries),
            history = mapOf("кока-кола" to groceries.id, "выпечка" to 99L), // 99: a category since deleted
        )
        assertEquals(listOf(null, groceries.id), drafts.map { it.categoryId })
    }

    @Test
    fun transferWithBothAmounts() {
        val draft = (map(item("transfer", "10000", "RUB", "", "account" to "1", "to" to "2", "toAmount" to "108")).single() as VoiceAction.Record).draft
        assertEquals(OpType.TRANSFER, draft.type)
        assertEquals(1_000_000L, draft.amountMinor)
        assertEquals(10_800L, draft.toAmountMinor)
        assertEquals(multiUsd.id, draft.toAccountId)
        assertEquals(false, draft.isEstimate)
        val guessed = (map(item("transfer", "10000", "RUB", "", "account" to "1", "to" to "2")).single() as VoiceAction.Record).draft
        assertTrue(guessed.isEstimate)
    }

    @Test
    fun transferWithoutDestinationIsNotGuessed() {
        assertTrue(map(item("transfer", "100", "USD", "", "account" to "2")).single() is VoiceAction.NotUnderstood)
    }

    @Test
    fun yesterdayLandsAtNoonYesterday() {
        val draft = (map(item("expense", "20", "GEL", "такси", "date" to "2026-10-01")).single() as VoiceAction.Record).draft
        assertEquals(LocalDate.of(2026, 10, 1).atTime(LocalTime.NOON).toInstant(zone).toEpochMilli(), draft.timestamp)
    }

    @Test
    fun nonsenseAndMissingAmountsAreReported() {
        assertTrue(map(item("unknown", null, null)).single() is VoiceAction.NotUnderstood)
        assertTrue(map(item("expense", null, "GEL", "что-то")).single() is VoiceAction.NotUnderstood)
        assertTrue(VoiceMapper.actions(VoiceResult("ммм", emptyList()), accounts, listOf(food), settings, rates, now, zone).single() is VoiceAction.NotUnderstood)
    }

    @Test
    fun promptListsAccountsAndCategories() {
        val prompt = VoicePrompt.system(accounts, listOf(food), settings, LocalDate.of(2026, 10, 2))
        assertTrue(prompt.contains("- 3: Наличные, GEL, cash"))
        assertTrue(prompt.contains("eating_out (Еда вне дома)"))
        assertTrue(prompt.contains("Местная валюта: GEL"))
    }
}
