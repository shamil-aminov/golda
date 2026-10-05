package sh.aminov.golda.domain

import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.CategoryKind
import sh.aminov.golda.data.Goal
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
    /** "Хочу купить бургер за 50 $": not spent yet. */
    data class Consider(val title: String, val amountMinor: Long, val currency: String) : VoiceAction
    data class NotUnderstood(val what: String) : VoiceAction
}

object VoicePrompt {
    /** JSON schema the model must answer with. */
    val schema = """
        {"type":"object","properties":{
          "transcript":{"type":"string"},
          "items":{"type":"array","items":{"type":"object","properties":{
            "intent":{"type":"string","enum":["expense","income","transfer","consider","unknown"]},
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
            - «Хочу купить», «стоит ли брать», «думаю купить» — intent consider.
            - Перевод между своими счетами или снятие наличных — transfer: account_id откуда, to_account_id куда, to_amount — сколько пришло, если сказано.
            - amount и to_amount — число строкой с точкой: "15", "1500.5". Валюта — код ISO: лари GEL, бат THB, доллар/бакс USD, рубль RUB, евро EUR. Не названа — null.
            - account_id только из списка выше и только если счёт явно назван или однозначно следует из сказанного («наличкой», «с кредитки»), иначе null.
            - category — ключ из списка или null. note — коротко, что купил, с маленькой буквы.
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
    ): List<VoiceAction> {
        if (result.items.isEmpty()) return listOf(VoiceAction.NotUnderstood(result.transcript))
        return result.items.map { item -> action(item, result.transcript, accounts, categories, settings, rates, recordedAt, zone) }
    }

    private fun action(
        item: VoiceItem,
        transcript: String,
        accounts: List<Account>,
        categories: List<Category>,
        settings: Settings,
        rates: Rates,
        recordedAt: Long,
        zone: ZoneId,
    ): VoiceAction {
        val byId = accounts.associateBy { it.id }
        fun named(id: String?) = id?.toLongOrNull()?.let { byId[it] }
        val currency = item.currency?.uppercase()?.takeIf { it.length == 3 } ?: settings.localCurrency
        val amount = item.amount?.let { Fmt.parseMinor(it, currency) }?.takeIf { it > 0 }
        val note = item.note.trim().replaceFirstChar { it.uppercase() }
        val timestamp = timestamp(item.date, recordedAt, zone)
        val categoryId = item.category?.let { key -> categories.firstOrNull { it.key == key }?.id }

        return when (item.intent) {
            "consider" ->
                if (amount == null) VoiceAction.NotUnderstood(transcript)
                else VoiceAction.Consider(note.ifBlank { tr("Покупка", "Purchase") }, amount, currency)

            "expense" -> {
                if (amount == null) return VoiceAction.NotUnderstood(transcript)
                val account = named(item.accountId) ?: pick(accounts, currency, settings) ?: return VoiceAction.NotUnderstood(transcript)
                if (account.currency == currency) {
                    VoiceAction.Record(Draft(OpType.EXPENSE, timestamp, account.id, amount, categoryId = categoryId, note = note, voiceText = transcript))
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
                val account = named(item.accountId) ?: pick(accounts, currency, settings) ?: return VoiceAction.NotUnderstood(transcript)
                if (account.currency == currency) {
                    VoiceAction.Record(Draft(OpType.INCOME, timestamp, account.id, amount, categoryId = categoryId, note = note, voiceText = transcript))
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
                val from = named(item.accountId) ?: pick(accounts, currency, settings) ?: return VoiceAction.NotUnderstood(transcript)
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

    /** The expense for a purchase decided on after a [VoiceAction.Consider]. */
    fun buy(consider: VoiceAction.Consider, accounts: List<Account>, settings: Settings, rates: Rates, now: Long): Draft? {
        val account = pick(accounts, consider.currency, settings) ?: return null
        if (account.currency == consider.currency) {
            return Draft(OpType.EXPENSE, now, account.id, consider.amountMinor, note = consider.title)
        }
        val charged = rates.cardCharge(consider.amountMinor, consider.currency, account.currency) ?: return null
        return Draft(
            OpType.EXPENSE, now, account.id, charged, note = consider.title,
            purchaseAmountMinor = consider.amountMinor, purchaseCurrency = consider.currency, isEstimate = true,
        )
    }

    /**
     * The expense for buying a reached goal: from the account the goal is saved on, when it has one and
     * that account holds enough (converted like a card purchase if the currencies differ); otherwise
     * as any other purchase ([buy]).
     */
    fun buyGoal(goal: Goal, states: Map<Long, AccountState>, accounts: List<Account>, settings: Settings, rates: Rates, now: Long): Draft? {
        val consider = VoiceAction.Consider(goal.name, goal.targetMinor, goal.currency)
        val saved = goal.accountId?.let { states[it] }
        if (saved != null) {
            val account = saved.account
            if (account.currency == goal.currency) {
                if (saved.balanceMinor >= goal.targetMinor) return Draft(OpType.EXPENSE, now, account.id, goal.targetMinor, note = goal.name)
            } else {
                val charged = rates.cardCharge(goal.targetMinor, goal.currency, account.currency)
                if (charged != null && saved.balanceMinor >= charged) {
                    return Draft(
                        OpType.EXPENSE, now, account.id, charged, note = goal.name,
                        purchaseAmountMinor = goal.targetMinor, purchaseCurrency = goal.currency, isEstimate = true,
                    )
                }
            }
        }
        return buy(consider, accounts, settings, rates, now)
    }

    /**
     * The account a phrase most likely means when it names none: the last one
     * used if it is in the right currency, else a free-money account in that
     * currency, else the last one used anyway (the purchase is then converted).
     */
    fun pick(accounts: List<Account>, currency: String, settings: Settings): Account? {
        val last = settings.lastAccountId?.let { id -> accounts.firstOrNull { it.id == id } }
        val spendable = accounts.filter { it.includeInFree || it.type == AccountType.CREDIT }
        return last?.takeIf { it.currency == currency }
            ?: spendable.firstOrNull { it.currency == currency }
            ?: last
            ?: spendable.firstOrNull()
            ?: accounts.firstOrNull()
    }

    private fun timestamp(date: String?, recordedAt: Long, zone: ZoneId): Long {
        val day = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return recordedAt
        val recordedDay = Ledger.localDate(recordedAt, zone)
        return if (day == recordedDay || day.isAfter(recordedDay)) recordedAt
        else day.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
    }
}
