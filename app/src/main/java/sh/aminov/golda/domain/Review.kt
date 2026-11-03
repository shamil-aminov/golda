package sh.aminov.golda.domain

import sh.aminov.golda.data.Account
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.OperationFull
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What Gemini is asked for a review of a period (SPEC §8): a dry, detailed Markdown note on where
 * the money went. The numbers are worked out here, by code; the model only reads and explains them.
 */
object ReviewPrompt {
    /** The most recent operations listed one by one; older ones count in the totals only. */
    const val MAX_LINES = 300

    val system = """
        Ты разбираешь личные траты за период. Пиши по-русски, сухо и конкретно, как аналитик, без воды и без советов про инвестиции.
        Формат — Markdown:
        - заголовок «## <период>»;
        - 4–7 пунктов с главными наблюдениями: куда ушли деньги, что выросло или выделяется, необычные траты, дни без записей;
        - «### Что можно урезать» — 2–3 пункта, только из того, что видно в данных («Радости», кафе, такси и т. п.);
        - числа бери из данных, ничего не придумывай; суммы в рублях с пробелами между тысячами, «₽» после числа.
        Не больше 200 слов. Без вступления и без заключения.
    """.trimIndent()

    private val day = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("ru"))

    /** The period's figures and operations, as plain lines the model reads. */
    fun data(
        period: Period,
        operations: List<OperationFull>,
        accounts: Map<Long, Account>,
        categories: Map<Long, Category>,
        rates: Rates,
        today: LocalDate,
        zone: ZoneId,
    ): String {
        val report = Analytics.report(operations, accounts, categories, period, today, zone, rates)
        fun rub(minor: Long) = Fmt.amount(minor, "RUB")
        fun name(id: Long?) = id?.let { categories[it] }?.label() ?: "без категории"
        val inPeriod = operations
            .filter { val d = Ledger.localDate(it.op.timestamp, zone); !d.isBefore(period.from) && !d.isAfter(period.to) }
            .sortedBy { it.op.timestamp }
        val expenses = inPeriod.filter { it.op.type == OpType.EXPENSE }
        val days = period.days.size
        return buildString {
            appendLine("Период: ${period.from.format(day)} – ${period.to.format(day)} ($days дн.)")
            appendLine("Суммы — сколько это стоило в рублях.")
            appendLine("Расходы всего: ${rub(report.spentRub)}; каждый день ${rub(report.dailyRub)}, регулярно ${rub(report.regularRub)}, крупное ${rub(report.bigRub)}.")
            appendLine("Ежедневное в среднем: ${rub(report.averagePerDayRub)} в день.")
            appendLine("Доходы: ${rub(report.incomeRub)}. Потери на обмене против ЦБ: ${rub(report.fxLossRub)}.")
            appendLine()
            appendLine("По категориям:")
            report.categories.forEach { spend ->
                val count = expenses.count { it.op.categoryId == spend.categoryId }
                val bucket = spend.categoryId?.let { categories[it] }?.let { Buckets.of(it) }?.let { bucketWord(it) } ?: "каждый день"
                appendLine("- ${name(spend.categoryId)} ($bucket): ${rub(spend.rubMinor)}, $count шт.")
            }
            appendLine()
            appendLine("Ежедневное по дням: " + report.days.joinToString("; ") { (d, minor) -> "${d.dayOfMonth}.${d.monthValue} ${minor / 100}" })
            appendLine()
            val listed = expenses.takeLast(MAX_LINES)
            if (listed.size < expenses.size) appendLine("Траты (последние ${listed.size} из ${expenses.size}):") else appendLine("Траты:")
            listed.forEach { full ->
                val cost = -full.postings.sumOf { it.rubMinor }
                val said = full.op.purchaseAmountMinor?.let { minor -> full.op.purchaseCurrency?.let { " (${Fmt.amount(minor, it)})" } }.orEmpty()
                val account = full.postings.firstOrNull()?.let { accounts[it.accountId]?.name }.orEmpty()
                val note = full.op.note.ifBlank { "—" }
                appendLine("${Ledger.localDate(full.op.timestamp, zone)} | ${name(full.op.categoryId)} | ${bucketWord(Buckets.of(full.op, categories))} | $note | ${rub(cost)}$said | $account")
            }
        }.trim()
    }

    private fun bucketWord(bucket: sh.aminov.golda.data.Bucket) = when (bucket) {
        sh.aminov.golda.data.Bucket.DAILY -> "каждый день"
        sh.aminov.golda.data.Bucket.REGULAR -> "регулярно"
        sh.aminov.golda.data.Bucket.BIG -> "крупное"
    }

    /** "октябрь" for a whole month or its start to today, "27 окт – 2 ноя" otherwise. */
    fun title(period: Period, today: LocalDate): String {
        val wholeMonth = period.from.dayOfMonth == 1 && period.from.month == period.to.month &&
            (period.to == period.from.withDayOfMonth(period.from.lengthOfMonth()) || period.to == today)
        if (wholeMonth) return period.from.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, I18n.locale)
        val short = DateTimeFormatter.ofPattern(if (I18n.russian) "d MMM" else "MMM d", I18n.locale)
        return "${period.from.format(short)} – ${period.to.format(short)}"
    }
}
