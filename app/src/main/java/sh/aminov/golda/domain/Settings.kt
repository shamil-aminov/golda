package sh.aminov.golda.domain

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    /** Paid by the hour (true) or a fixed monthly salary (false). */
    val incomeHourly: Boolean = true,
    val hourlyRate: Double = 0.0,
    val monthlySalary: Double = 0.0,
    val taxPercent: Double = 0.0,
    val hoursPerWeek: Double = 40.0,
    /** Day of month the salary arrives. */
    val payday: Int = 15,
    val displayCurrencies: List<String> = listOf("RUB", "USD"),
    /** Currency of the country you are in; voice input falls back to it. */
    val localCurrency: String = "RUB",
    /**
     * The main currency ("Основная валюта"): the big numbers and totals are shown in it. The ledger
     * stays in rubles; see [Base].
     */
    val baseCurrency: String = "RUB",
    /** How much more than the CBR rate rubles cost you abroad. */
    val markup: Double = 0.10,
    val lastAccountId: Long? = null,
    val geminiModel: String = "gemini-3.5-flash-lite",
    /** The key itself stays encrypted in storage and never travels with the settings. */
    val hasGeminiKey: Boolean = false,
    /** Sunday-evening nudge to compare balances with the bank. */
    val reconcileReminder: Boolean = true,
    /** The goal whose "reached" badge has already bloomed, so it celebrates only once. */
    val celebratedGoalId: Long? = null,
    /** When each account was last checked against the bank (account id to epoch millis), matches included. */
    val reconciledAt: Map<Long, Long> = emptyMap(),
) {
    /** One hour of work after tax, in rubles. */
    val hourNet: Double
        get() = if (incomeHourly) {
            hourlyRate * (1 - taxPercent / 100)
        } else {
            val hoursPerMonth = hoursPerWeek * 52 / 12
            if (hoursPerMonth > 0) monthlySalary * (1 - taxPercent / 100) / hoursPerMonth else 0.0
        }

    /** Expected salary after tax for the work done in [month]. */
    fun salaryFor(month: YearMonth): Double = if (incomeHourly) {
        val workdays = (1..month.lengthOfMonth()).count {
            month.atDay(it).dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }
        workdays * hoursPerWeek / 5 * hourlyRate * (1 - taxPercent / 100)
    } else {
        monthlySalary * (1 - taxPercent / 100)
    }

    fun nextPayday(today: LocalDate): LocalDate {
        fun inMonth(month: YearMonth) = month.atDay(payday.coerceIn(1, month.lengthOfMonth()))
        val thisMonth = inMonth(YearMonth.from(today))
        return if (thisMonth.isAfter(today)) thisMonth else inMonth(YearMonth.from(today).plusMonths(1))
    }

    /**
     * The payday the current pay period started on, today included. Not simply a month before the
     * next one: with payday on the 31st, the one before 28 February is 31 January, not the 28th.
     */
    fun lastPayday(today: LocalDate): LocalDate = nextPayday(nextPayday(today).minusMonths(1).minusDays(1))
}
