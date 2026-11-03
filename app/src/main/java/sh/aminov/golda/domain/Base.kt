package sh.aminov.golda.domain

/**
 * The main currency the big figures and totals are shown in ("Основная валюта").
 *
 * Only presentation: the ledger, the budget and the analytics keep counting in rubles (each
 * posting's ruble cost basis), and an aggregate is converted here, at today's display rate
 * (CBR × (1 + markup)), just before it is shown. With rubles every method gives exactly what the
 * ruble formatting gave before. A currency without a rate falls back to rubles.
 */
class Base(private val rates: Rates, wanted: String) {
    /** The currency actually used: [wanted], or rubles when there is no rate for it. */
    val code: String = if (wanted == "RUB" || rates.display(wanted) != null) wanted else "RUB"

    val symbol: String get() = Currencies.symbol(code)

    val isRub: Boolean get() = code == "RUB"

    /** [rubMinor] kopecks in the main currency, major units. */
    fun major(rubMinor: Long): Double = if (isRub) rubMinor / 100.0 else rates.fromRub(rubMinor, code)!!

    /** [rubMinor] kopecks in the main currency's minor units. */
    fun minor(rubMinor: Long): Long = if (isRub) rubMinor else Currencies.toMinor(major(rubMinor), code)

    /** An approximate amount, "510 ₽" or "5,5 $", as [Fmt.approx] writes converted values. */
    fun approx(rubMinor: Long): String = Fmt.approx(major(rubMinor), code)

    /** Whole units for a big number: "1 849 ₽", "20 $". */
    fun whole(rubMinor: Long): String = Fmt.whole(minor(rubMinor), code) + " " + symbol

    companion object {
        /** The main currency of [settings]. */
        fun of(settings: Settings, rates: Rates) = Base(rates, settings.baseCurrency)
    }
}
