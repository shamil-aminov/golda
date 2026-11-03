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
        Не больше 200 слов. Без вступления и без заключения. Без длинных тире, многоточий и сокращений с точкой: пиши словами.
    """.trimIndent()

    private val day = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))

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
            appendLine("Период: с ${period.from.format(day)} по ${period.to.format(day)}, $days дн.")
            appendLine("Суммы — сколько это стоило в рублях.")
            appendLine("Расходы всего: ${rub(report.spentRub)}; ежедневные ${rub(report.dailyRub)}, регулярные ${rub(report.regularRub)}, крупные ${rub(report.bigRub)}.")
            appendLine("Ежедневное в среднем: ${rub(report.averagePerDayRub)} в день.")
            appendLine("Доходы: ${rub(report.incomeRub)}. Потери на обмене против ЦБ: ${rub(report.fxLossRub)}.")
            appendLine()
            appendLine("По категориям:")
            report.categories.forEach { spend ->
                val count = expenses.count { it.op.categoryId == spend.categoryId }
                val bucket = spend.categoryId?.let { categories[it] }?.let { Buckets.of(it) }?.let { bucketWord(it) } ?: "ежедневные"
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
                val tag = full.op.tag?.let { " ($it)" }.orEmpty()
                appendLine("${Ledger.localDate(full.op.timestamp, zone)} | ${name(full.op.categoryId)}$tag | ${bucketWord(Buckets.of(full.op, categories))} | $note | ${rub(cost)}$said | $account")
            }
        }.trim()
    }

    private fun bucketWord(bucket: sh.aminov.golda.data.Bucket) = when (bucket) {
        sh.aminov.golda.data.Bucket.DAILY -> "ежедневные"
        sh.aminov.golda.data.Bucket.REGULAR -> "регулярные"
        sh.aminov.golda.data.Bucket.BIG -> "крупные"
    }

    /**
     * The period in words, with no dots or dashes: "октябрь" for a whole month (or its start to
     * [today]), "последние 30 дней" for days up to [today], "с 5 по 12 октября" or "с 5 октября
     * по 3 ноября" otherwise. Pass a [today] after the period to always get the dates.
     */
    fun title(period: Period, today: LocalDate): String {
        val wholeMonth = period.from.dayOfMonth == 1 && period.from.month == period.to.month && period.from.year == period.to.year &&
            (period.to == period.from.withDayOfMonth(period.from.lengthOfMonth()) || period.to == today)
        if (wholeMonth) return period.from.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, I18n.locale)
        if (period.to == today && period.from.isBefore(today)) {
            val n = period.days.size.toLong()
            return tr("последние ", "last ") + n + " " + plural(n, "день", "дня", "дней", "day", "days")
        }
        return span(period.from, period.to)
    }

    /** "с 5 по 12 октября", "с 5 октября по 3 ноября"; English "Oct 5 to 12", "Oct 5 to Nov 3". */
    fun span(from: LocalDate, to: LocalDate): String {
        val sameMonth = from.month == to.month && from.year == to.year
        if (!I18n.russian) {
            val md = DateTimeFormatter.ofPattern("MMMM d", I18n.locale)
            return from.format(md) + " to " + if (sameMonth) to.dayOfMonth.toString() else to.format(md)
        }
        val dm = DateTimeFormatter.ofPattern("d MMMM", I18n.locale)
        return "с " + (if (sameMonth) from.dayOfMonth.toString() else from.format(dm)) + " по " + to.format(dm)
    }
}
