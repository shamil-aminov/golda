package sh.aminov.golda.domain

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    /** The card spent from day to day; null: the one most expenses come from (see Envelopes). */
    val spendAccountId: Long? = null,
    /**
     * The spending period as epoch days, both included: the money on the card is meant to last from
     * [periodStart] to [periodEnd]. Zero: a month from the card's last top-up.
     */
    val periodStart: Long = 0,
    val periodEnd: Long = 0,
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
    /** The model that writes reviews: slower and deeper than the one that hears purchases. */
    val reviewModel: String = "gemini-3.8-flash",
    /** The key itself stays encrypted in storage and never travels with the settings. */
    val hasGeminiKey: Boolean = false,
    /** Sunday-evening nudge to compare balances with the bank. */
    val reconcileReminder: Boolean = true,
    /** When each account was last checked against the bank (account id to epoch millis), matches included. */
    val reconciledAt: Map<Long, Long> = emptyMap(),
    /** The colour theme picked in Settings (see ui.Palette); "golda" follows the system's light or dark. */
    val palette: String = "golda",
    /** The folder a weekly backup goes to (a document tree the person picked); null: none. */
    val autoBackupFolder: String? = null,
    /** When the last weekly backup was written, epoch millis; 0: never. */
    val lastAutoBackup: Long = 0,
    /** Generated themes kept by the user, as ThemeSpec keys. */
    val savedThemes: List<String> = emptyList(),
)
