package sh.aminov.golda.domain

import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.CategoryKind
import sh.aminov.golda.data.OperationFull
import sh.aminov.golda.data.OpType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** One thing said in a voice note, as the model extracted it. Nothing here is computed. */
data class VoiceItem(
    val intent: String,
    val amount: String?,
    val currency: String?,
    val note: String,
    val category: String?,
    val accountId: String?,
    val toAccountId: String?,
    val toAmount: String?,
    val date: String?,
)

data class VoiceResult(val transcript: String, val items: List<VoiceItem>)

sealed interface VoiceAction {
    data class Record(val draft: Draft) : VoiceAction
    data class NotUnderstood(val what: String) : VoiceAction
}

object VoicePrompt {
    /** JSON schema the model must answer with. */
    val schema = """
        {"type":"object","properties":{
          "transcript":{"type":"string"},
          "items":{"type":"array","items":{"type":"object","properties":{
            "intent":{"type":"string","enum":["expense","income","transfer","unknown"]},
            "amount":{"type":"string","nullable":true},
            "currency":{"type":"string","nullable":true},
            "note":{"type":"string"},
            "category":{"type":"string","nullable":true},
            "account_id":{"type":"string","nullable":true},
            "to_account_id":{"type":"string","nullable":true},
            "to_amount":{"type":"string","nullable":true},
            "date":{"type":"string","nullable":true}
          },"required":["intent","note"]}}
        },"required":["transcript","items"]}
    """.trimIndent()

    fun system(accounts: List<Account>, categories: List<Category>, settings: Settings, today: LocalDate): String {
        val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("ru"))
        val accountLines = accounts.joinToString("\n") { "- ${it.id}: ${it.name}, ${it.currency}, ${it.type.name.lowercase()}" }
        fun cats(kind: CategoryKind) = categories.filter { it.kind == kind }.joinToString(", ") { "${it.key} (${it.name})" }
        return """
            Ты разбираешь голосовые записи о личных деньгах в JSON. Ничего не считай и не конвертируй, только извлекай сказанное.
            Сегодня ${today} (${weekday}). Местная валюта: ${settings.localCurrency}. Валюты пользователя: ${settings.displayCurrencies.joinToString()}.

            Счета (id: название, валюта, тип):
            $accountLines

            Категории расходов: ${cats(CategoryKind.EXPENSE)}.
            Категории доходов: ${cats(CategoryKind.INCOME)}.

            Правила:
            - Каждая трата, доход или перевод — отдельный элемент items. «Кофе 8 и круассан 6» — два расхода.
            - Если человек оговорился и поправился («15 бат, ой, лари»), бери исправленное.
            - Перевод между своими счетами или снятие наличных — transfer: account_id откуда, to_account_id куда, to_amount — сколько пришло, если сказано.
            - amount и to_amount — число строкой с точкой: "15", "1500.5". Дробные суммы: «10.50», «10,50», «10 запятая 50», «10 лари 50 тетри» — это "10.5", а не "1050". «70 тетри» — "0.7". Валюта — код ISO: лари GEL, бат THB, доллар/бакс USD, рубль RUB, евро EUR. Не названа — null.
            - account_id только из списка выше и только если счёт явно назван или однозначно следует из сказанного («наличкой», «с кредитки»), иначе null.
            - category — ключ из списка или null, у каждого элемента свой, даже когда в одной фразе несколько покупок. note — коротко, что купил, с маленькой буквы.
            - date в формате YYYY-MM-DD, только если назван день («вчера», «в понедельник»), иначе null.
            - Если не про деньги или не разобрать — один элемент с intent unknown.
        """.trimIndent()
    }
}

object VoiceMapper {
    fun actions(
        result: VoiceResult,
        accounts: List<Account>,
        categories: List<Category>,
        settings: Settings,
        rates: Rates,
        recordedAt: Long,
        zone: ZoneId,
        /** Categories of earlier entries by their note, for an item the model left without one ([history]). */
        history: Map<String, Long> = emptyMap(),
        /** Expenses per account, for the account a purchase in a currency comes from ([pick]). */
        spentFrom: Map<Long, Int> = emptyMap(),
    ): List<VoiceAction> {
        if (result.items.isEmpty()) return listOf(VoiceAction.NotUnderstood(result.transcript))
        val said = SpokenAmounts.parse(result.transcript)
        val single = result.items.size == 1
        return result.items.map { item -> action(item, result.transcript, said, single, accounts, categories, settings, rates, recordedAt, zone, history, spentFrom) }
    }

    /**
     * The category last used for each note ("кока-кола" → groceries), newest winning: the model
     * sometimes leaves the items of a phrase with several purchases without one.
     */
    fun history(operations: List<OperationFull>): Map<String, Long> = operations
        .filter { it.op.categoryId != null && it.op.note.isNotBlank() }
        .sortedBy { it.op.timestamp }
        .associate { it.op.note.trim().lowercase() to it.op.categoryId!! }

    private fun action(
        item: VoiceItem,
        transcript: String,
        said: List<SpokenAmounts.Said>,
        single: Boolean,
        accounts: List<Account>,
        categories: List<Category>,
        settings: Settings,
        rates: Rates,
        recordedAt: Long,
        zone: ZoneId,
        history: Map<String, Long>,
        spentFrom: Map<Long, Int>,
    ): VoiceAction {
        val byId = accounts.associateBy { it.id }
        fun named(id: String?) = id?.toLongOrNull()?.let { byId[it] }
        val currency = item.currency?.uppercase()?.takeIf { it.length == 3 } ?: settings.localCurrency
        val parsed = item.amount?.let { Fmt.parseMinor(it, currency) }?.takeIf { it > 0 }
        // The model's number checked against the words: 80,84 said and 8.84 returned is put right when
        // the phrase has one amount, and marked ≈ to check when it is not clear which one was meant.
        val checked = parsed?.let { SpokenAmounts.check(it, currency, said, single) }
        val amount = checked?.minor
        val doubtful = checked?.doubtful == true
        val note = item.note.trim().replaceFirstChar { it.uppercase() }
        val timestamp = timestamp(item.date, recordedAt, zone)
        val categoryId = item.category?.let { key -> categories.firstOrNull { it.key == key }?.id }
            ?: history[note.lowercase()]?.takeIf { id -> categories.any { it.id == id } }

        return when (item.intent) {
            "expense" -> {
                if (amount == null) return VoiceAction.NotUnderstood(transcript)
                val account = named(item.accountId) ?: pick(accounts, currency, settings, spentFrom) ?: return VoiceAction.NotUnderstood(transcript)
                if (account.currency == currency) {
                    VoiceAction.Record(Draft(OpType.EXPENSE, timestamp, account.id, amount, categoryId = categoryId, note = note, isEstimate = doubtful, voiceText = transcript))
                } else {
                    val charged = rates.cardCharge(amount, currency, account.currency) ?: return VoiceAction.NotUnderstood(transcript)
                    VoiceAction.Record(
                        Draft(
                            OpType.EXPENSE, timestamp, account.id, charged, categoryId = categoryId, note = note,
                            purchaseAmountMinor = amount, purchaseCurrency = currency, isEstimate = true, voiceText = transcript,
                        ),
                    )
                }
            }

            "income" -> {
                if (amount == null) return VoiceAction.NotUnderstood(transcript)
                val account = named(item.accountId) ?: pick(accounts, currency, settings, spentFrom) ?: return VoiceAction.NotUnderstood(transcript)
                if (account.currency == currency) {
                    VoiceAction.Record(Draft(OpType.INCOME, timestamp, account.id, amount, categoryId = categoryId, note = note, isEstimate = doubtful, voiceText = transcript))
                } else {
                    // Converted at the display rate: an estimate, and the amount as said is kept beside it.
                    val credited = rates.convert(amount, currency, account.currency) ?: return VoiceAction.NotUnderstood(transcript)
                    VoiceAction.Record(
                        Draft(
                            OpType.INCOME, timestamp, account.id, credited, categoryId = categoryId, note = note,
                            purchaseAmountMinor = amount, purchaseCurrency = currency, isEstimate = true, voiceText = transcript,
                        ),
                    )
                }
            }

            "transfer" -> {
                if (amount == null) return VoiceAction.NotUnderstood(transcript)
                val from = named(item.accountId) ?: pick(accounts, currency, settings, spentFrom) ?: return VoiceAction.NotUnderstood(transcript)
                val to = named(item.toAccountId)?.takeIf { it.id != from.id } ?: return VoiceAction.NotUnderstood(transcript)
                val sent = if (from.currency == currency) amount else rates.convert(amount, currency, from.currency)
                    ?: return VoiceAction.NotUnderstood(transcript)
                val received = item.toAmount?.let { Fmt.parseMinor(it, to.currency) }?.takeIf { it > 0 }
                    ?: if (to.currency == from.currency) sent else rates.convert(sent, from.currency, to.currency)
                    ?: return VoiceAction.NotUnderstood(transcript)
                // Either side worked out at the display rate rather than said: an estimate.
                val guessed = (item.toAmount == null && to.currency != from.currency) || from.currency != currency
                VoiceAction.Record(
                    Draft(
                        OpType.TRANSFER, timestamp, from.id, sent, toAccountId = to.id, toAmountMinor = received,
                        note = note, isEstimate = guessed, voiceText = transcript,
                    ),
                )
            }

            else -> VoiceAction.NotUnderstood(transcript)
        }
    }

    /**
     * The account a purchase in [currency] most likely comes from when none is named: the last one
     * used if it is in that currency; else, in that currency, the one most expenses came from, then
     * one counted for spending, then any that is not a debt, then a credit card; else the last one
     * used anyway (the purchase is then converted), else the first spendable one.
     */
    fun pick(accounts: List<Account>, currency: String, settings: Settings, spentFrom: Map<Long, Int> = emptyMap()): Account? {
        val last = settings.lastAccountId?.let { id -> accounts.firstOrNull { it.id == id } }
        val inCurrency = accounts.filter { it.currency == currency && it.type != AccountType.LOAN }
        val spendable = accounts.filter { it.includeInFree || it.type == AccountType.CREDIT }
        return last?.takeIf { it.currency == currency }
            ?: inCurrency.filter { (spentFrom[it.id] ?: 0) > 0 }.maxByOrNull { spentFrom.getValue(it.id) }
            ?: inCurrency.firstOrNull { it.includeInFree }
            ?: inCurrency.firstOrNull { it.type != AccountType.CREDIT }
            ?: inCurrency.firstOrNull()
            ?: last
            ?: spendable.firstOrNull()
            ?: accounts.firstOrNull()
    }

    /** How many expenses came from each account: what [pick] goes by. */
    fun spentFrom(operations: List<OperationFull>): Map<Long, Int> = operations
        .filter { it.op.type == OpType.EXPENSE }
        .flatMap { it.postings }
        .groupingBy { it.accountId }
        .eachCount()

    private fun timestamp(date: String?, recordedAt: Long, zone: ZoneId): Long {
        val day = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return recordedAt
        val recordedDay = Ledger.localDate(recordedAt, zone)
        return if (day == recordedDay || day.isAfter(recordedDay)) recordedAt
        else day.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
    }
}

/**
 * The amounts a transcript says, read by code rather than by the model: "80,84", "11.94",
 * "30 запятая 21", "11 лари 20 тетри", "70 тетри", "1850". Only digits are read; amounts in words
 * ("ноль запятая тридцать шесть") are left to the model.
 */
object SpokenAmounts {
    /** One amount said: in hundredths of a unit, and whether it was a bare whole number ("1850"). */
    data class Said(val hundredths: Long, val whole: Boolean)

    data class Checked(val minor: Long, val doubtful: Boolean)

    private const val MAJOR = """(?:лари|лар|бат\w*|доллар\w*|бакс\w*|руб\w*|евро|₾|\$|€|₽|฿)"""
    private const val MINOR = """(?:тетри|сатанг\w*|цент\w*|коп\w*)"""

    /** Most specific first: each match is blanked out before the next pattern reads the rest. */
    private val patterns = listOf<Pair<Regex, (MatchResult) -> Said>>(
        Regex("""(\d+)\s*$MAJOR\s+(\d{1,2})\s*$MINOR""") to { m -> Said(m.groupValues[1].toLong() * 100 + m.groupValues[2].toLong(), false) },
        Regex("""(\d+)\s+(?:запятая|точка)\s+(\d{1,2})(?!\d)""") to { m -> Said(m.groupValues[1].toLong() * 100 + cents(m.groupValues[2]), false) },
        Regex("""(\d+)[.,](\d{1,2})(?!\d)""") to { m -> Said(m.groupValues[1].toLong() * 100 + cents(m.groupValues[2]), false) },
        Regex("""(\d{1,2})\s*$MINOR""") to { m -> Said(m.groupValues[1].toLong(), false) },
        Regex("""\d+""") to { m -> Said(m.value.toLong() * 100, true) },
    )

    /** "5" after a separator is 50 hundredths, "05" is 5. */
    private fun cents(digits: String): Long = if (digits.length == 1) digits.toLong() * 10 else digits.toLong()

    fun parse(transcript: String): List<Said> {
        var rest = transcript.lowercase()
        val found = mutableListOf<Pair<Int, Said>>()
        for ((regex, read) in patterns) {
            for (m in regex.findAll(rest)) {
                val value = runCatching { read(m) }.getOrNull() ?: continue
                found += m.range.first to value
            }
            // Blanked out, so "80,84" is not read again as 80 and 84.
            rest = regex.replace(rest) { " ".repeat(it.value.length) }
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    /**
     * [minor] as the model read it, against what the transcript says. It stands if the transcript says
     * it, or says nothing in digits; a bare "1850" for 18.50 stands too (the recognizer drops a
     * separator more often than the model invents one). If the phrase is one item with one amount that
     * differs, that amount is taken instead, marked doubtful; with several amounts the model's stays,
     * doubtful.
     */
    fun check(minor: Long, currency: String, said: List<Said>, single: Boolean): Checked {
        val digits = Currencies.digits(currency)
        if (said.isEmpty() || digits > 2) return Checked(minor, false)
        // Hundredths per minor unit: 1 for lari and dollars, 100 for a currency without fractions.
        val scale = if (digits == 2) 1L else if (digits == 1) 10L else 100L
        fun asMinor(s: Said): Long? = if (s.hundredths % scale == 0L) s.hundredths / scale else null
        val matches = said.any { s -> asMinor(s) == minor || (s.whole && digits == 2 && s.hundredths / 100 == minor) }
        if (matches) return Checked(minor, false)
        val only = said.singleOrNull()?.let(::asMinor)
        return if (single && only != null && only > 0) Checked(only, true) else Checked(minor, true)
    }
}
