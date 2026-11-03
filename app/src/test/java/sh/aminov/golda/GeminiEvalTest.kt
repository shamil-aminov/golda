package sh.aminov.golda

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sh.aminov.golda.data.Account
import sh.aminov.golda.data.AccountType
import sh.aminov.golda.data.DefaultCategories
import sh.aminov.golda.data.Gemini
import sh.aminov.golda.domain.Fmt
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.domain.VoiceItem
import sh.aminov.golda.domain.VoicePrompt
import java.io.File
import java.time.LocalDate

/**
 * Evals of what Gemini makes of what people say: the phrases in `evals/phrases.tsv` go through the
 * app's own prompt, schema and reply reading, as text instead of audio, and every field is scored.
 * Recordings in `evals/voice/` (an `.ogg` with a `.tsv` line of the same name) go through as audio.
 *
 * It costs requests and the answers vary, so it runs only by hand, with a key:
 * `GEMINI_API_KEY=… ./gradlew testDebugUnitTest --tests "*GeminiEvalTest"`
 * (optionally `GEMINI_MODEL=…`). The report is printed and kept in `build/evals.txt`.
 */
@RunWith(RobolectricTestRunner::class)
class GeminiEvalTest {
    private val key = System.getenv("GEMINI_API_KEY").orEmpty()
    private val model = System.getenv("GEMINI_MODEL")?.takeIf { it.isNotBlank() } ?: Settings().geminiModel
    private val accounts = listOf(
        Account(1, "Карта ₽", "RUB", AccountType.CARD, includeInFree = true),
        Account(2, "Накопительный", "RUB", AccountType.SAVINGS, includeInFree = false, interestRate = 12.0),
        Account(3, "Доллары", "USD", AccountType.CARD, includeInFree = true),
        Account(4, "TBC", "GEL", AccountType.CARD, includeInFree = true),
        Account(5, "Наличные", "GEL", AccountType.CASH, includeInFree = true),
        Account(6, "Кредитка", "RUB", AccountType.CREDIT, includeInFree = false, interestRate = 29.9),
    )
    private val settings = Settings(localCurrency = "GEL", displayCurrencies = listOf("RUB", "USD", "GEL", "THB"))
    private val system = VoicePrompt.system(accounts, DefaultCategories.all, settings, LocalDate.of(2026, 10, 24))

    private data class Expected(val intent: String, val amount: String, val currency: String, val account: String, val to: String, val toAmount: String, val category: String)
    private data class Case(val said: String, val items: List<Expected>, val audio: File? = null)

    private fun cases(): List<Case> {
        val lines = javaClass.classLoader!!.getResource("evals/phrases.tsv")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        val out = mutableListOf<Case>()
        for (line in lines) {
            val c = line.split('\t')
            val e = Expected(c[1], c[2], c[3], c[4], c[5], c[6], c[7])
            if (c[0] == "+") out[out.lastIndex] = out.last().copy(items = out.last().items + e) else out += Case(c[0], listOf(e))
        }
        File("src/test/resources/evals/voice").listFiles { f -> f.extension == "ogg" }.orEmpty().sorted().forEach { ogg ->
            File(ogg.path.removeSuffix(".ogg") + ".tsv").takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() && !it.startsWith("#") }?.let { rows ->
                val items = rows.map { r -> r.split('\t').let { c -> Expected(c[1], c[2], c[3], c[4], c[5], c[6], c[7]) } }
                out += Case(rows.first().split('\t')[0], items, ogg)
            }
        }
        return out
    }

    /** "*" fits anything, "-" an empty field; amounts compare as money, the rest as text. */
    private fun fits(expected: String, got: String?, money: Boolean = false): Boolean = when (expected) {
        "*" -> true
        "-" -> got.isNullOrBlank()
        else -> if (money) Fmt.parseMinor(expected, "USD") == got?.let { Fmt.parseMinor(it, "USD") } else expected.equals(got, ignoreCase = true)
    }

    private fun score(e: Expected, g: VoiceItem?): Map<String, Boolean> = mapOf(
        "intent" to (g?.intent == e.intent),
        "amount" to fits(e.amount, g?.amount, money = true),
        "currency" to fits(e.currency, g?.currency),
        "account" to fits(e.account, g?.accountId),
        "to" to fits(e.to, g?.toAccountId),
        "to_amount" to fits(e.toAmount, g?.toAmount, money = true),
        "category" to fits(e.category, g?.category),
    )

    @Test
    fun whatGeminiMakesOfWhatIsSaid() = runBlocking {
        assumeTrue("Set GEMINI_API_KEY to run the evals", key.isNotBlank())
        val report = StringBuilder("Model $model\n\n")
        val totals = mutableMapOf<String, Pair<Int, Int>>()
        var casesRight = 0
        val all = cases()
        for (case in all) {
            val result = runCatching {
                if (case.audio != null) Gemini.parse(case.audio, system, key, model) else Gemini.parseText(case.said, system, key, model)
            }
            val items = result.getOrNull()?.items.orEmpty()
            var right = items.size == case.items.size
            val lines = mutableListOf<String>()
            case.items.forEachIndexed { i, e ->
                val got = items.getOrNull(i)
                val s = score(e, got)
                s.forEach { (field, ok) -> totals[field] = (totals[field] ?: (0 to 0)).let { (a, b) -> (a + if (ok) 1 else 0) to b + 1 } }
                if (s.values.any { !it }) {
                    right = false
                    lines += "    wanted $e\n    got    $got"
                }
            }
            if (right) casesRight++
            val tag = if (case.audio != null) "🎤 " else ""
            report.append(if (right) "✓ " else "✗ ").append(tag).append(case.said)
            result.exceptionOrNull()?.let { report.append("  (failed: ${it.message})") }
            report.append('\n')
            lines.forEach { report.append(it).append('\n') }
        }
        report.append("\n$casesRight of ${all.size} phrases fully right\n")
        totals.forEach { (field, ab) -> report.append("  $field: ${ab.first}/${ab.second}\n") }
        println(report)
        File("build/evals.txt").writeText(report.toString())
        // A floor so a prompt or model change that makes things worse is noticed; raise it as it improves.
        assertTrue("Only $casesRight of ${all.size} phrases fully right; see build/evals.txt", casesRight >= all.size * 0.8)
    }
}
