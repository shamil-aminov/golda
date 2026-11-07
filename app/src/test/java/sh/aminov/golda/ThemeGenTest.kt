package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.toArgb
import sh.aminov.golda.data.legacyThemeMode
import sh.aminov.golda.ui.Palette
import sh.aminov.golda.ui.ThemeGen
import sh.aminov.golda.ui.isDarkMode
import sh.aminov.golda.ui.tonesFor
import sh.aminov.golda.ui.ThemeMode
import sh.aminov.golda.ui.ThemeSpec
import sh.aminov.golda.ui.ThemeStyle
import sh.aminov.golda.ui.hueOf
import sh.aminov.golda.ui.oklch
import sh.aminov.golda.ui.inkOn
import sh.aminov.golda.ui.tileInk
import sh.aminov.golda.ui.operationColor
import sh.aminov.golda.ui.categoryColor
import sh.aminov.golda.data.OpType
import sh.aminov.golda.data.DefaultCategories
import kotlin.random.Random

class ThemeGenTest {
    @Test
    fun entryIslandKeepsItsPresetColoursAndContrast() {
        listOf(false, true).forEach { dark ->
            val golda = tonesFor("golda", dark)
            assertEquals(if (dark) 0xFF6B5D3E.toInt() else 0xFFDACBA5.toInt(), golda.entryContainer.toArgb())
            assertEquals(if (dark) 0xFFFFF4DA.toInt() else 0xFF332C1A.toInt(), golda.entryContent.toArgb())
            assertTrue(ratio(golda.entryContent, golda.entryContainer) >= 4.5)
            assertEquals(golda.entryContainer, tonesFor("golda", dark, oled = true).entryContainer)
            Palette.entries.filter { it != Palette.GOLDA }.forEach { preset ->
                val t = preset.tones(dark)
                assertEquals(if (dark) t.soft else t.card, t.entryContainer)
                assertEquals(t.text, t.entryContent)
            }
            val custom = tonesFor(ThemeSpec(212).key, dark)
            assertEquals(if (dark) custom.soft else custom.card, custom.entryContainer)
            assertEquals(custom.text, custom.entryContent)
        }
    }

    @Test
    fun aSpecSurvivesItsKey() {
        val spec = ThemeSpec(212, ThemeStyle.EXPRESSIVE, ThemeMode.DARK, 2)
        assertEquals(spec, ThemeSpec.parse(spec.key))
        assertNull(ThemeSpec.parse("beach"))
        assertNull(ThemeSpec.parse("custom:x:CALM:SYSTEM:1"))
    }

    @Test
    fun oklchKeepsItsHue() {
        listOf(0, 40, 142, 200, 255, 315).forEach { h ->
            val back = hueOf(oklch(0.72, 0.12, h.toDouble()))
            val diff = kotlin.math.abs(((back - h + 540) % 360) - 180)
            assertTrue("hue $h came back $back", diff <= 3)
        }
    }

    @Test
    fun lightThemesAreLightAndDarkOnesDark() {
        repeat(20) { i ->
            val spec = ThemeSpec.random(Random(i))
            val light = ThemeGen.tones(spec.copy(mode = ThemeMode.LIGHT), systemDark = true)
            val dark = ThemeGen.tones(spec.copy(mode = ThemeMode.DARK), systemDark = false)
            assertTrue(!light.dark && dark.dark)
            // Gamma-encoded, as stored: a near-white page, a near-black ink, and the other way round.
            assertTrue(light.page.luminance() > 0.9f && light.text.luminance() < 0.3f)
            assertTrue(dark.page.luminance() < 0.2f && dark.text.luminance() > 0.85f)
        }
    }

    /** Every ready theme has both halves, each light or dark as named and as readable as the calm ones. */
    @Test
    fun everyPresetIsReadableLightAndDark() {
        Palette.entries.forEach { p ->
            listOf(false, true).forEach { dark ->
                val t = p.tones(dark)
                val name = "${p.key} ${if (dark) "dark" else "light"}"
                assertEquals(name, dark, t.dark)
                // WCAG: body text 7:1 on the page and the cards, quiet text and the picked and action
                // labels 4.5:1, the same bar the calm themes were drawn to.
                assertTrue("$name text/page ${ratio(t.text, t.page)}", ratio(t.text, t.page) >= 7.0)
                assertTrue("$name text/card ${ratio(t.text, t.card)}", ratio(t.text, t.card) >= 7.0)
                assertTrue("$name muted/card ${ratio(t.muted, t.card)}", ratio(t.muted, t.card) >= 4.5)
                assertTrue("$name onPicked ${ratio(t.onGraphite, t.graphite)}", ratio(t.onGraphite, t.graphite) >= 4.5)
                assertTrue("$name onAction ${ratio(t.onGold, t.gold)}", ratio(t.onGold, t.gold) >= 4.5)
                assertTrue("$name onPlate ${ratio(t.onPlate, t.plate)}", ratio(t.onPlate, t.plate) >= 7.0)
                assertTrue("$name onPlateMuted ${ratio(t.onPlateMuted, t.plate)}", ratio(t.onPlateMuted, t.plate) >= 4.5)
                // A bold one's pace bar stands out on its plate: 3:1, WCAG's bar for a mark that carries meaning.
                t.pace?.let { assertTrue("$name pace ${ratio(it, t.plate)}", ratio(it, t.plate) >= 3.0) }
                // isDark() reads the page's luminance: a light half stays above the half.
                assertEquals("$name page", dark, t.page.relative() < 0.5)
                // OLED: the dark half on black keeps its inks.
                if (dark) assertTrue("$name oled", ratio(t.text, tonesFor(p.key, dark = true, oled = true).page) >= 7.0)
                // A row's icon on its colour, every category and a transfer: 3:1, WCAG's bar for a mark.
                (categoryKeys + null).forEach { key ->
                    OpType.entries.forEach { type ->
                        val fill = operationColor(key, type, t)
                        assertTrue("$name $key $type icon", ratio(inkOn(fill), fill) >= 3.0)
                    }
                }
                // The words on a category's mosaic tile: 4.5:1, whatever the swatch.
                (categoryKeys + null).forEach { key ->
                    val fill = categoryColor(key, t)
                    assertTrue("$name $key tile ${ratio(tileInk(fill), fill)}", ratio(tileInk(fill), fill) >= 4.5)
                }
            }
        }
        // Twenty-odd, calm first.
        assertTrue(Palette.entries.size >= 20)
        assertEquals(Palette.entries.sortedBy { it.bold }, Palette.entries)
        assertEquals(Palette.CLEAR, Palette.of("clear-night"))
        // The bold themes before 0.20 land on their heirs, not on Golda.
        listOf("sunset", "lava", "tropic", "aurora", "candy", "cyber", "neon", "acid", "indigo").forEach {
            assertTrue(it, Palette.of(it).bold)
        }
        assertEquals(Palette.MARATHON, Palette.of("acid"))
        // Марафон in the game's own colours: the lime mic and the ultramarine plate in both halves, the blue-grey cards.
        listOf(Palette.MARATHON.light, Palette.MARATHON.dark).forEach { assertEquals(0xFFC2FE0C.toInt(), it.gold.toArgb()) }
        listOf(Palette.MARATHON.light, Palette.MARATHON.dark).forEach { assertEquals(0xFF3601FB.toInt(), it.plate.toArgb()) }
        assertEquals(0xFF29324F.toInt(), Palette.MARATHON.dark.card.toArgb())
        // In a bold theme the mic and the plate carry the colour; the picked state stays black or white.
        Palette.entries.filter { it.bold }.flatMap { listOf(it.light, it.dark) }.forEach {
            assertTrue(it.graphite.toArgb() in setOf(0xFF0A0A0A.toInt(), 0xFFFFFFFF.toInt()))
            assertTrue(it.gold != it.plate)
        }
        assertEquals(Palette.GOLDA, Palette.of("nonsense"))
    }

    private val categoryKeys = DefaultCategories.all.map { it.key }

    /** A category keeps its colour whatever else is spent; its own hue apart from the others in a theme. */
    @Test
    fun aCategoryHasItsOwnColour() {
        val t = Palette.GOLDA.light
        assertEquals(categoryColor("food", t), categoryColor("food", t))
        val spending = DefaultCategories.all.filter { it.kind == sh.aminov.golda.data.CategoryKind.EXPENSE }.map { categoryColor(it.key, t) }
        assertEquals(spending.size, spending.distinct().size)
        // A category of the user's own takes Другое's.
        assertEquals(categoryColor("other", t), categoryColor("mine", t))
    }

    @Test
    fun theModeIsOneSettingAndOldThemesKeepTheirs() {
        assertTrue(isDarkMode("DARK", systemDark = false) && !isDarkMode("LIGHT", systemDark = true))
        assertTrue(isDarkMode("SYSTEM", systemDark = true) && !isDarkMode(null, systemDark = false))
        assertEquals("DARK", legacyThemeMode("midnight"))
        assertEquals("LIGHT", legacyThemeMode("beach"))
        assertEquals("SYSTEM", legacyThemeMode("golda"))
        assertEquals("DARK", legacyThemeMode(ThemeSpec(40, mode = ThemeMode.DARK).key))
        // A generated theme's own mode is not looked at any more: the app's is.
        val spec = ThemeSpec(40, mode = ThemeMode.DARK)
        assertTrue(!tonesFor(spec.key, dark = false).dark)
    }

    private fun linear(c: Float) = if (c <= 0.04045f) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    private fun androidx.compose.ui.graphics.Color.relative() = 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    private fun ratio(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
        val (hi, lo) = listOf(a.relative(), b.relative()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun androidx.compose.ui.graphics.Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
