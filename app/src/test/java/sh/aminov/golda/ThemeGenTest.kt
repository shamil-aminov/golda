package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.ui.ThemeGen
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

    private fun androidx.compose.ui.graphics.Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
