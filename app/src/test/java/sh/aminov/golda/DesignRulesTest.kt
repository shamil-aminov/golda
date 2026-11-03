package sh.aminov.golda

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The design rules the owner agreed on, checked in the source so a change that breaks one fails the
 * build instead of waiting for someone to notice on the phone. A line that has to break a rule says
 * why with a `// scale-exempt: …` comment.
 */
class DesignRulesTest {
    private val sources: List<Pair<String, String>> = File("src/main/java").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { it.invariantSeparatorsPath to it.readText() }
        .toList()

    /** Every `tr("Русский", "English")` pair whose two halves are plain string literals. */
    private val texts: List<Triple<String, String, String>> by lazy {
        val pair = Regex("""\btr\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*\)""")
        sources.flatMap { (path, text) -> pair.findAll(text).map { Triple(path, it.groupValues[1], it.groupValues[2]) }.toList() }
    }

    private fun report(what: String, found: List<String>) =
        assertTrue("$what:\n" + found.joinToString("\n"), found.isEmpty())

    @Test
    fun theTextsAreFound() {
        // Guards the guards: if the pattern stopped matching, every rule below would pass for nothing.
        assertTrue("only ${texts.size} texts found", texts.size > 300)
    }

    @Test
    fun aOneSentenceTextHasNoFullStop() {
        // "Счета, операции и настройки" — not "…настройки.". Several sentences may end like prose.
        fun oneSentence(s: String) = !Regex("""[.!?] \S""").containsMatchIn(s)
        report(
            "One-sentence texts ending with a full stop",
            texts.flatMap { (path, ru, en) -> listOf(ru, en).filter { oneSentence(it) && it.trimEnd().endsWith('.') }.map { "$path: $it" } },
        )
    }

    @Test
    fun noEllipsesAndNoDashesAsSeparators() {
        report(
            "Ellipses or long dashes",
            texts.flatMap { (path, ru, en) ->
                listOf(ru, en).filter { "…" in it || "..." in it || " — " in it || " – " in it }.map { "$path: $it" }
            },
        )
    }

    @Test
    fun theEnglishIsEnglish() {
        report(
            "Cyrillic in the English half",
            texts.filter { (_, _, en) -> Regex("[А-Яа-яЁё]").containsMatchIn(en) && "«" !in en && "“" !in en }.map { (p, _, en) -> "$p: $en" },
        )
    }

    @Test
    fun typeComesFromTheScale() {
        // 14 / 16 (list rows) / 22 / 36 / 58 / 94: labelLarge, bodyMedium, titleSmall, titleMedium,
        // bodyLarge, titleLarge, displaySmall and the big figure. Nothing in between.
        val offScale = Regex("""typography\.(headline\w*|displayMedium|displayLarge\b|bodySmall|labelMedium|labelSmall)""")
        report(
            "Text styles off the type scale",
            sources.filterNot { (path, _) -> path.endsWith("ui/Theme.kt") }.flatMap { (path, text) ->
                text.lines().withIndex()
                    .filter { (_, line) -> offScale.containsMatchIn(line) && "scale-exempt:" !in line }
                    .map { (i, line) -> "$path:${i + 1}: ${line.trim()}" }
            },
        )
    }

    @Test
    fun fontSizesAreOnlySetToFitText() {
        // A size in sp outside the theme is only a bound for text that shrinks to fit.
        report(
            "Font sizes set by hand",
            sources.filterNot { (path, _) -> path.endsWith("ui/Theme.kt") }.flatMap { (path, text) ->
                text.lines().withIndex()
                    .filter { (_, line) -> Regex("""\b\d+(\.\d+)?\.sp\b""").containsMatchIn(line) && "TextAutoSize" !in line && "scale-exempt:" !in line }
                    .map { (i, line) -> "$path:${i + 1}: ${line.trim()}" }
            },
        )
    }
}
