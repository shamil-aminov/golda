package sh.aminov.golda.domain

import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.Category
import sh.aminov.golda.data.Obligation
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.OperationFull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What Gemini is asked for a review of a period (SPEC §8): habits and patterns the person doesn't see
 * for themselves, the numbers behind each, and an experiment or two for the next period. The numbers
 * are worked out here, by code (the period before, weekdays, the time of day, purchases that repeat);
 * the model only reads and explains them.
 */
object ReviewPrompt {
    /** The most recent operations listed one by one; older ones count in the totals only. */
    const val MAX_LINES = 300

    val system = """
        Ты проверяешь траты одного человека за период, как аккуратный бухгалтер. Перед тобой данные, посчитанные приложением: итоги, сравнение с прошлым периодом, регулярные платежи, балансы счетов, траты, похожие на повтор, и каждая трата с датой.
        Человек сам знает свои привычки. Ему нужно то, что легко пропустить: ошибки, лишние платежи, потери. Точность важнее находок: лучше не сказать ничего, чем сказать то, чего нет в данных.

        Пиши по-русски, на «ты», спокойно и коротко. Формат Markdown:
        - заголовок «## <период>»;
        - одна строка о главном: сколько ушло и на что основное;
        - «### Стоит проверить» — от нуля до трёх пунктов, и только то, что видно в строках данных: трата, похожая на ошибку или двойную оплату; заметные комиссии и потери на обмене; регулярный платёж, который прошёл не так, как в списке регулярных (другая сумма, лишний или пропущенный раз). В каждом пункте дата, сумма и что именно проверить. Если такого нет, напиши одну строку «Ничего необычного». Не добавляй пункты ради количества и не пересказывай итоги и привычки;
        - «### Что дальше» — регулярные платежи, которые ещё впереди, с датами и суммами, и хватает ли на них денег на счетах. Если регулярных платежей впереди нет, пропусти этот раздел.
        Как записаны данные, учитывай это и не делай выводов из того, что объясняется самой записью:
        - время у траты — когда её записали, не обязательно когда купили; несколько трат в одну минуту внесены пачкой задним числом; «—» вместо времени значит, что известен только день. Ночное время записи само по себе не значит ночной покупки;
        - одно и то же называется по-разному: руками пишут общими словами («еда», «продукты», «пицца»), из банковской выписки приходят названия магазинов и сервисов (Glovo, Wolt). Смена названий не значит смены привычки: не пиши «впервые», «перешёл на», «начал», если поменялись только названия;
        - одна запись может быть несколькими покупками («продукты и прочее»), поэтому число записей не равно числу покупок;
        - «перевод» — деньги между людьми, а не покупка для себя и не зарплата; метку «зарплата» у переводов не учитывай;
        - «Не записано» — разница, найденная при сверке с банком: сумма забытых трат, а не одна покупка;
        - до даты начала учёта трат просто не записано: это не нулевые траты.
        Числа бери только из данных, сам ничего не досчитывай, кроме простых долей и разниц. Суммы в рублях с пробелами между тысячами, «₽» после числа; деньги на счетах и регулярные платежи в их валюте. До 200 слов. Без вступления и без заключения. Без длинных тире, многоточий и сокращений с точкой: пиши словами.
    """.trimIndent()

    /** How Gemini answers questions under a review: about these data, briefly, showing its sums. */
    val chatSystem = """
        Ты финансовый аналитик одного человека и уже написал разбор его трат за период. Теперь он задаёт вопросы по этим тратам.
        Отвечай по-русски, на «ты», коротко: 2–6 предложений или короткий список, без вступлений. Markdown можно.
        Опирайся только на данные периода из первого сообщения. Если считаешь, показывай расчёт одной строкой: «кофе 23 раза, в среднем 450 ₽, всего 10 350 ₽». Если для ответа данных нет, так и скажи.
        Про «сколько сэкономил бы» считай по фактическим тратам периода и, если просят, в пересчёте на месяц или год, с пометкой, что это оценка.
        Суммы в рублях с пробелами между тысячами, «₽» после числа. Без длинных тире и многоточий.
    """.trimIndent()

    /**
     * The talk as Gemini reads it, oldest first: the period's data with the request for a review,
     * the review written then, every question and answer since, and the new [question].
     * True is a turn of the person's, false one of the model's.
     */
    fun turns(data: String, review: String, earlier: List<Pair<Boolean, String>>, question: String): List<Pair<Boolean, String>> =
        listOf(true to "Данные периода:\n\n$data\n\nНапиши разбор.", false to review) + earlier + (true to question.trim())

    private val day = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))
    private val weekdays = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

    /** Night, morning, afternoon, evening: the parts of a day habits show in. */
    private fun partOfDay(hour: Int) = when (hour) {
        in 0..5 -> "1 ночь, 0–6"
        in 6..11 -> "2 утро, 6–12"
        in 12..17 -> "3 день, 12–18"
        else -> "4 вечер, 18–24"
    }

    /** The period's figures and operations, as plain lines the model reads. */
    fun data(
        period: Period,
        operations: List<OperationFull>,
        accounts: Map<Long, Account>,
        categories: Map<Long, Category>,
        rates: Rates,
        today: LocalDate,
        zone: ZoneId,
        /** Regular payments: what is known to come every month. */
        regulars: List<Obligation> = emptyList(),
    ): String {
        val report = Analytics.report(operations, accounts, categories, period, today, zone, rates)
        // The same number of days just before, to compare with.
        val length = period.days.size.toLong()
        val before = Period(period.from.minusDays(length), period.from.minusDays(1))
        val previous = Analytics.report(operations, accounts, categories, before, before.to, zone, rates)
        fun rub(minor: Long) = Fmt.amount(minor, "RUB")
        fun name(id: Long?) = id?.let { categories[it] }?.label() ?: "без категории"
        fun change(now: Long, then: Long): String = when {
            then <= 0 && now <= 0 -> "без изменений"
            then <= 0 -> "раньше не было"
            else -> {
                val pct = Math.round((now - then) * 100.0 / then)
                (if (pct > 0) "+" else "") + pct + " %"
            }
        }
        val inPeriod = operations
            .filter { val d = Ledger.localDate(it.op.timestamp, zone); !d.isBefore(period.from) && !d.isAfter(period.to) }
            .sortedBy { it.op.timestamp }
        val expenses = inPeriod.filter { it.op.type == OpType.EXPENSE }
        fun cost(full: OperationFull) = -full.postings.sumOf { it.rubMinor }
        fun at(full: OperationFull) = Instant.ofEpochMilli(full.op.timestamp).atZone(zone).toLocalDateTime()
        // The app's own "now" has seconds in it; a day picked by hand or a bank statement's line is a
        // whole minute, its time made up. Those say nothing about the time of day.
        fun timed(full: OperationFull) = full.op.timestamp % 60_000 != 0L
        val days = period.days.size
        val start = operations.minOfOrNull { it.op.timestamp }?.let { Ledger.localDate(it, zone) }
        return buildString {
            appendLine("Период: с ${period.from.format(day)} по ${period.to.format(day)}, $days дн.")
            start?.let { appendLine("Учёт ведётся с ${it.format(day)}; до этого трат просто не записано.") }
            appendLine("Суммы — сколько это стоило в рублях.")
            appendLine("Расходы всего: ${rub(report.spentRub)}; ежедневные ${rub(report.dailyRub)}, регулярные ${rub(report.regularRub)}, разовые ${rub(report.bigRub)}.")
            appendLine("Ежедневное в среднем: ${rub(report.dailyPerDayRub)} в день.")
            appendLine("Доходы: ${rub(report.incomeRub)}. Потери на обмене против ЦБ: ${rub(report.fxLossRub)}.")
            appendLine()
            appendLine("Прошлый такой же период, с ${before.from.format(day)} по ${before.to.format(day)}:")
            if (start != null && start.isAfter(before.to)) {
                appendLine("Это было до начала учёта, сравнивать не с чем.")
            } else if (previous.spentRub > 0) {
                appendLine("Расходы всего: ${rub(previous.spentRub)} (сейчас ${change(report.spentRub, previous.spentRub)}); ежедневные ${rub(previous.dailyRub)} (сейчас ${change(report.dailyRub, previous.dailyRub)}).")
            } else {
                appendLine("Трат не записано, сравнивать не с чем.")
            }
            appendLine()
            appendLine("По категориям, сейчас и в прошлом периоде:")
            val then = previous.categories.associate { it.categoryId to it.rubMinor }
            report.categories.forEach { spend ->
                val count = expenses.count { it.op.categoryId == spend.categoryId }
                val bucket = spend.categoryId?.let { categories[it] }?.let { Buckets.of(it) }?.let { bucketWord(it) } ?: "ежедневные"
                val was = then[spend.categoryId] ?: 0
                appendLine("- ${name(spend.categoryId)} ($bucket): ${rub(spend.rubMinor)}, $count шт.; было ${rub(was)}, ${change(spend.rubMinor, was)}")
            }
            previous.categories.filter { p -> report.categories.none { it.categoryId == p.categoryId } }.forEach { p ->
                appendLine("- ${name(p.categoryId)}: сейчас ничего; было ${rub(p.rubMinor)}")
            }
            appendLine()
            when (Analytics.barFor(report.dailyDays.size)) {
                Analytics.Bar.DAY -> appendLine("Ежедневное по дням: " + report.dailyDays.joinToString("; ") { (d, minor) -> "${d.dayOfMonth}.${d.monthValue} ${weekdays[d.dayOfWeek.value - 1]} ${minor / 100}" })
                // A long period by weeks or months, so the lines stay readable and the request short.
                Analytics.Bar.WEEK -> appendLine("Ежедневное по неделям, с даты: " + Analytics.bars(report.dailyDays).joinToString("; ") { (d, minor) -> "${d.dayOfMonth}.${d.monthValue}.${d.year} ${minor / 100}" })
                Analytics.Bar.MONTH -> appendLine("Ежедневное по месяцам: " + Analytics.bars(report.dailyDays).joinToString("; ") { (d, minor) -> "${d.monthValue}.${d.year} ${minor / 100}" })
            }
            appendLine()
            // How many of each weekday the period had, for an honest average per weekday.
            val weekdayCount = period.days.groupingBy { it.dayOfWeek.value }.eachCount()
            appendLine("Все траты по дням недели, в среднем за такой день:")
            (1..7).forEach { w ->
                val those = expenses.filter { at(it).dayOfWeek.value == w }
                val n = weekdayCount[w] ?: 0
                if (n > 0) appendLine("- ${weekdays[w - 1]}: ${rub(those.sumOf(::cost) / n)}, ${those.size} шт. за $n дн.")
            }
            appendLine()
            val timedExpenses = expenses.filter(::timed)
            appendLine("По времени суток, только траты с настоящим временем записи (${timedExpenses.size} из ${expenses.size}):")
            timedExpenses.groupBy { partOfDay(at(it).hour) }.toSortedMap().forEach { (part, list) ->
                appendLine("- ${part.drop(2)}: ${rub(list.sumOf(::cost))}, ${list.size} шт.")
            }
            appendLine()
            // The same thing bought again and again: by Gemini's tag when there is one, else the note.
            val repeated = expenses.groupBy { (it.op.tag ?: it.op.note).trim().lowercase() }
                .filterKeys { it.isNotBlank() }
                .filterValues { it.size >= 2 }
                .entries.sortedByDescending { e -> e.value.sumOf(::cost) }.take(10)
            if (repeated.isNotEmpty()) {
                appendLine("Повторяющиеся покупки:")
                repeated.forEach { (what, list) ->
                    val total = list.sumOf(::cost)
                    appendLine("- $what: ${list.size} раз, всего ${rub(total)}, в среднем ${rub(total / list.size)}")
                }
                appendLine()
            }
            if (regulars.isNotEmpty()) {
                appendLine("Регулярные платежи:")
                regulars.forEach { r -> appendLine("- ${r.name}: ${Fmt.amount(r.amountMinor, r.currency)}, ${r.dayOfMonth} числа") }
                appendLine()
                // What comes next, by date, for "Что дальше": the code counts the dates, not the model.
                appendLine("Сегодня ${today.format(day)}. Ближайшие регулярные платежи:")
                regulars.map { it to Budget.nextDue(it.dayOfMonth, today) }.sortedBy { it.second }.forEach { (r, due) ->
                    appendLine("- ${due.format(day)}: ${r.name}, ${Fmt.amount(r.amountMinor, r.currency)}")
                }
                appendLine()
            }
            // What there is to pay with, each in its own currency; debts as what is owed.
            val states = Ledger.states(accounts.values.toList(), operations.flatMap { it.postings })
            if (states.isNotEmpty()) {
                appendLine("На счетах сейчас:")
                states.values.forEach { s ->
                    val debt = s.account.type == AccountType.CREDIT || s.account.type == AccountType.LOAN
                    appendLine("- ${s.account.name}: ${Fmt.amount(s.balanceMinor, s.currency)}" + if (debt) " (долг)" else "")
                }
                appendLine()
            }
            // The same sum from the same account on the same day: a double payment, or two real ones.
            val twice = expenses.groupBy { full -> Triple(at(full).toLocalDate(), full.postings.firstOrNull()?.accountId, full.postings.sumOf { it.amountMinor }) }
                .filterValues { it.size >= 2 }
            if (twice.isNotEmpty()) {
                appendLine("Одна и та же сумма с одного счёта в один день:")
                twice.forEach { (key, list) ->
                    val currency = key.second?.let { accounts[it]?.currency } ?: "RUB"
                    appendLine("- ${key.first}: ${list.size} раза по ${Fmt.amount(-key.third, currency)} (${list.joinToString { it.op.note.ifBlank { "—" } }})")
                }
                appendLine()
            }
            val listed = expenses.takeLast(MAX_LINES)
            if (listed.size < expenses.size) appendLine("Траты (последние ${listed.size} из ${expenses.size}):") else appendLine("Траты:")
            appendLine("дата | день | время | категория (метка) | корзина | что | сколько | счёт")
            listed.forEach { full ->
                val said = full.op.purchaseAmountMinor?.let { minor -> full.op.purchaseCurrency?.let { " (${Fmt.amount(minor, it)})" } }.orEmpty()
                val account = full.postings.firstOrNull()?.let { accounts[it.accountId]?.name }.orEmpty()
                val note = full.op.note.ifBlank { "—" }
                val tag = full.op.tag?.let { " ($it)" }.orEmpty()
                val time = at(full)
                val clock = if (timed(full)) "%02d:%02d".format(time.hour, time.minute) else "—"
                appendLine("${time.toLocalDate()} | ${weekdays[time.dayOfWeek.value - 1]} | $clock | ${name(full.op.categoryId)}$tag | ${bucketWord(Buckets.of(full.op, categories))} | $note | ${rub(cost(full))}$said | $account")
            }
        }.trim()
    }

    private fun bucketWord(bucket: sh.aminov.golda.data.Bucket) = when (bucket) {
        sh.aminov.golda.data.Bucket.DAILY -> "ежедневные"
        sh.aminov.golda.data.Bucket.REGULAR -> "регулярные"
        sh.aminov.golda.data.Bucket.BIG -> "разовые"
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
