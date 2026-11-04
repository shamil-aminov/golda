package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.AnalyticsScreen
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HomeScreen
import sh.aminov.golda.ui.Tones
import java.util.TimeZone

/**
 * Drafts of the bold themes after Bungie's Marathon: white or near-black pages, the bold colours on
 * the plates (the cards), the picked state, the action and the categories. Temporary: the chosen ones
 * move into Palette and this file goes. See docs/tasks/bold-themes.md.
 */
internal object BoldDrafts {
    class Draft(val key: String, val light: Tones, val dark: Tones) {
        /** The dark half on a page of pure black, for OLED screens. */
        val oled: Tones get() = BoldDrafts.set(true, dark.card, dark.graphite, dark.onGraphite, dark.gold, dark.onGold, page = Color.Black)
    }

    /** One half: the page, its inks and the quiet surfaces neutral, the colour on the [card] and the two states. */
    fun set(dark: Boolean, card: Color, picked: Color, onPicked: Color, action: Color, onAction: Color, page: Color? = null) = Tones(
        dark = dark,
        page = page ?: if (dark) Color(0xFF121212) else Color.White, card = card,
        soft = if (dark) Color(0xFF222222) else Color(0xFFEDEDED), line = if (dark) Color(0xFF363636) else Color(0xFFD4D4D4),
        muted = if (dark) Color(0xFFD4D4D4) else Color(0xFF2E2E2E), text = if (dark) Color.White else Color(0xFF0A0A0A),
        graphite = picked, onGraphite = onPicked, gold = action, onGold = onAction,
        error = if (dark) Color(0xFFF87171) else Color(0xFFDC2626), onError = if (dark) Color(0xFF450A0A) else Color.White,
        errorSoft = if (dark) Color(0xFF4C1D1D) else Color(0xFFFEE2E2), onErrorSoft = if (dark) Color(0xFFFECACA) else Color(0xFF991B1B),
        infoL = if (dark) 0.70f else 0.74f, infoC = 0.16f,
    )

    private fun light(vararg c: Long) = set(false, Color(c[0]), Color(c[1]), Color(c[2]), Color(c[3]), Color(c[4]))
    private fun dark(vararg c: Long) = set(true, Color(c[0]), Color(c[1]), Color(c[2]), Color(c[3]), Color(c[4]))

    // card, picked, on picked, action, on action
    val all = listOf(
        // Acid lime plates, black picked, ultramarine action; at night ultramarine plates and the lime as the action.
        Draft("marathon", light(0xFFDFFF1A, 0xFF0A0A0A, 0xFFDFFF1A, 0xFF2233FF, 0xFFFFFFFF), dark(0xFF2233FF, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFD7FF1F, 0xFF0A0A0A)),
        // Blue plates with a red-orange action.
        Draft("ultramarine", light(0xFF9AA8FF, 0xFF2233FF, 0xFFFFFFFF, 0xFFFF4A10, 0xFF0A0A0A), dark(0xFF1A2BD0, 0xFFDFFF1A, 0xFF0A0A0A, 0xFFFF5A1F, 0xFF0A0A0A)),
        // Red-orange, the ref's 0241 plate.
        Draft("signal", light(0xFFFF7A3D, 0xFF0A0A0A, 0xFFFFFFFF, 0xFF2233FF, 0xFFFFFFFF), dark(0xFFA81800, 0xFFDFFF1A, 0xFF0A0A0A, 0xFFFF5A1F, 0xFF0A0A0A)),
        // The pixel squares' magenta with lime.
        Draft("pixel", light(0xFFFF6ACB, 0xFF2233FF, 0xFFFFFFFF, 0xFFDFFF1A, 0xFF0A0A0A), dark(0xFF8A0068, 0xFFDFFF1A, 0xFF0A0A0A, 0xFFFF3DBE, 0xFF0A0A0A)),
        // The visor's cyan glow with hot pink.
        Draft("hologram", light(0xFF3DE8FF, 0xFF0A0A0A, 0xFF3DE8FF, 0xFFFF2EA6, 0xFF0A0A0A), dark(0xFF005A66, 0xFFFF2EA6, 0xFF0A0A0A, 0xFF2EE6FF, 0xFF0A0A0A)),
        // Phosphor green.
        Draft("terminal", light(0xFF7CFF4F, 0xFF0A0A0A, 0xFF7CFF4F, 0xFFFF4A10, 0xFF0A0A0A), dark(0xFF0F4A14, 0xFF39FF14, 0xFF000000, 0xFF39FF14, 0xFF000000)),
        // Electric violet with yellow.
        Draft("violet", light(0xFFB794FF, 0xFF0A0A0A, 0xFFFFE500, 0xFF5B14FF, 0xFFFFFFFF), dark(0xFF5B14FF, 0xFFFFFFFF, 0xFF2A0A6B, 0xFFFFE500, 0xFF0A0A0A)),
        // The armour's plates: grey on white, black on black, the colour only in the two states.
        Draft("plate", light(0xFFEDEDED, 0xFF0A0A0A, 0xFFDFFF1A, 0xFFFF4A10, 0xFF0A0A0A), dark(0xFF1C1C1C, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFDFFF1A, 0xFF0A0A0A)),
    )

    /** The ones shown before and after a pure black page. */
    val oledKeys = listOf("marathon", "ultramarine", "pixel")
}

class BoldDraftsContrastTest {
    /** The same bar as the presets' (ThemeGenTest), and a light half the app takes for light. */
    @Test
    fun everyDraftIsReadable() {
        BoldDrafts.all.forEach { d ->
            listOf(d.light, d.dark, d.oled).forEach { t ->
                val name = "${d.key} ${if (t.dark) "dark" else "light"}"
                assertTrue("$name text/page", ratio(t.text, t.page) >= 7.0)
                assertTrue("$name text/card", ratio(t.text, t.card) >= 7.0)
                assertTrue("$name muted/card", ratio(t.muted, t.card) >= 4.5)
                assertTrue("$name muted/page", ratio(t.muted, t.page) >= 4.5)
                assertTrue("$name onPicked", ratio(t.onGraphite, t.graphite) >= 4.5)
                assertTrue("$name onAction", ratio(t.onGold, t.gold) >= 4.5)
                // isDark() reads the surface's luminance: a light half must stay above the half.
                assertTrue("$name page luminance", (t.page.luminance() >= 0.5f) != t.dark)
            }
        }
    }

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}

/** Home and Аналитика in every draft, both halves: recordRoborazziDebug with --tests "*BoldDraftsShots*". */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BoldDraftsShots {
    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    /** One composition for every frame: compose takes setContent only once a test. */
    @Test
    fun everyDraft() {
        val data = ScreenshotTest().demo()
        var tones by mutableStateOf(BoldDrafts.all[0].light)
        var home by mutableStateOf(true)
        compose.setContent {
            GoldaTheme(tones) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (home) HomeScreen(data, PaddingValues(), rememberLazyListState(), {}, {}, onEdit = {})
                    else AnalyticsScreen(data, PaddingValues()) {}
                }
            }
        }
        listOf(true, false).forEach { h ->
            BoldDrafts.all.forEach { d ->
                listOf(d.light, d.dark).forEach { t ->
                    tones = t
                    home = h
                    compose.waitForIdle()
                    val name = "${d.key}_${if (t.dark) "dark" else "light"}_${if (h) "home" else "insights"}"
                    compose.onRoot().captureRoboImage("src/test/screenshots/bold/$name.png")
                }
            }
            BoldDrafts.all.filter { it.key in BoldDrafts.oledKeys }.forEach { d ->
                tones = d.oled
                home = h
                compose.waitForIdle()
                compose.onRoot().captureRoboImage("src/test/screenshots/bold/${d.key}_oled_${if (h) "home" else "insights"}.png")
            }
        }
    }
}
