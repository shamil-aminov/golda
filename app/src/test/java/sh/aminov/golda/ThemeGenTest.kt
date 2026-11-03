package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
import kotlin.random.Random

class ThemeGenTest {
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
            }
        }
        // Twenty-odd, calm first.
        assertTrue(Palette.entries.size >= 20)
        assertEquals(Palette.entries.sortedBy { it.bold }, Palette.entries)
        assertEquals(Palette.CLEAR, Palette.of("clear-night"))
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
