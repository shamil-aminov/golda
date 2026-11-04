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
 * Drafts of the bold themes after Bungie's Marathon (acid lime, ultramarine, red-orange, black and
 * white plates), to choose from before any of them replaces a preset. Temporary: the chosen ones
 * move into Palette and this file goes. See docs/tasks/bold-themes.md.
 */
internal object BoldDrafts {
    class Draft(val key: String, val light: Tones, val dark: Tones)

    private fun set(dark: Boolean, vararg c: Long) = Tones(
        dark = dark,
        page = Color(c[0]), card = Color(c[1]), soft = Color(c[2]), line = Color(c[3]), muted = Color(c[4]), text = Color(c[5]),
        graphite = Color(c[6]), onGraphite = Color(c[7]), gold = Color(c[8]), onGold = Color(c[9]),
        error = if (dark) Color(0xFFF87171) else Color(0xFFDC2626), onError = if (dark) Color(0xFF450A0A) else Color.White,
        errorSoft = if (dark) Color(0xFF4C1D1D) else Color(0xFFFEE2E2), onErrorSoft = if (dark) Color(0xFFFECACA) else Color(0xFF991B1B),
        infoL = if (dark) 0.70f else 0.74f, infoC = 0.16f,
    )

    // page, card, soft, line, muted, text, picked, on picked, action, on action
    val all = listOf(
        // Acid lime pages, black picked, ultramarine action; at night black with the lime as the action.
        Draft("marathon",
            set(false, 0xFFDFFF1A, 0xFFF7FFC9, 0xFFC9EB00, 0xFFA8C400, 0xFF3D4500, 0xFF0A0A0A, 0xFF0A0A0A, 0xFFDFFF1A, 0xFF2233FF, 0xFFFFFFFF),
            set(true, 0xFF0A0A0A, 0xFF171717, 0xFF262626, 0xFF3A3A3A, 0xFFB9C77A, 0xFFF3FFC4, 0xFF2233FF, 0xFFFFFFFF, 0xFFD7FF1F, 0xFF0A0A0A)),
        // Ultramarine with a red-orange action.
        Draft("ultramarine",
            set(false, 0xFFAFBBFF, 0xFFE4E8FF, 0xFF98A6FF, 0xFF7F8FF5, 0xFF25307A, 0xFF050A3A, 0xFF0A0A0A, 0xFFFFFFFF, 0xFFFF4A10, 0xFF0A0A0A),
            set(true, 0xFF0D1BB0, 0xFF1A2BD0, 0xFF2B3CE0, 0xFF4252EA, 0xFFC4CCFF, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFDFFF1A, 0xFFFF5A1F, 0xFF0A0A0A)),
        // Red-orange, the ref's 0241 plate; ultramarine picked by day, lime by night.
        Draft("signal",
            set(false, 0xFFFFAA7A, 0xFFFFE6D6, 0xFFFF9662, 0xFFF07E48, 0xFF6A2100, 0xFF1A0600, 0xFF2233FF, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFFFAA7A),
            set(true, 0xFF5A1000, 0xFF761900, 0xFF8E2406, 0xFFA8330F, 0xFFFFC9AE, 0xFFFFFFFF, 0xFFDFFF1A, 0xFF0A0A0A, 0xFFFF5A1F, 0xFF0A0A0A)),
        // The pixel squares' magenta with lime.
        Draft("pixel",
            set(false, 0xFFFF9EE0, 0xFFFFE3F6, 0xFFFF86D6, 0xFFF06CC6, 0xFF6E0B50, 0xFF230018, 0xFF2233FF, 0xFFFFFFFF, 0xFFDFFF1A, 0xFF0A0A0A),
            set(true, 0xFF3E0034, 0xFF5A004B, 0xFF72005F, 0xFF8C1478, 0xFFF7B8E6, 0xFFFFFFFF, 0xFFDFFF1A, 0xFF0A0A0A, 0xFFFF3DBE, 0xFF0A0A0A)),
        // The visor's cyan glow with hot pink.
        Draft("hologram",
            set(false, 0xFF6CF0FF, 0xFFD6FBFF, 0xFF4FE2F5, 0xFF2FCBE0, 0xFF00434D, 0xFF001A1F, 0xFF0A0A0A, 0xFF6CF0FF, 0xFFFF2EA6, 0xFF0A0A0A),
            set(true, 0xFF00262E, 0xFF00363F, 0xFF004A55, 0xFF00616E, 0xFF9FE6F0, 0xFFEFFDFF, 0xFFFF2EA6, 0xFF0A0A0A, 0xFF2EE6FF, 0xFF0A0A0A)),
        // Phosphor green; at night green on black, like a screen.
        Draft("terminal",
            set(false, 0xFF9DFF7A, 0xFFE3FFD8, 0xFF86F25E, 0xFF6AD944, 0xFF164A00, 0xFF031400, 0xFF0A0A0A, 0xFF9DFF7A, 0xFFFF4A10, 0xFF0A0A0A),
            set(true, 0xFF000000, 0xFF0B140B, 0xFF142414, 0xFF1F3A1F, 0xFF7FD877, 0xFFB8FFA8, 0xFF143D14, 0xFFB8FFA8, 0xFF39FF14, 0xFF000000)),
        // Electric violet with a yellow picked state.
        Draft("violet",
            set(false, 0xFFCDB0FF, 0xFFF1E8FF, 0xFFBE99FF, 0xFFA97FF5, 0xFF47208F, 0xFF14052E, 0xFF0A0A0A, 0xFFFFE500, 0xFF5B14FF, 0xFFFFFFFF),
            set(true, 0xFF2A0A6B, 0xFF3A1290, 0xFF4B1EAA, 0xFF5F30C2, 0xFFD8C6FF, 0xFFFFFFFF, 0xFFFFE500, 0xFF0A0A0A, 0xFFB48CFF, 0xFF0A0A0A)),
        // The armour's white plates and black, red-orange and lime the only colour.
        Draft("plate",
            set(false, 0xFFFFFFFF, 0xFFF2F2F2, 0xFFE2E2E2, 0xFFC8C8C8, 0xFF4A4A4A, 0xFF0A0A0A, 0xFF0A0A0A, 0xFFDFFF1A, 0xFFFF4A10, 0xFF0A0A0A),
            set(true, 0xFF000000, 0xFF141414, 0xFF242424, 0xFF383838, 0xFFB5B5B5, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFDFFF1A, 0xFF0A0A0A)),
    )
}

class BoldDraftsContrastTest {
    /** The same bar as the presets' (ThemeGenTest), and a light half the app takes for light. */
    @Test
    fun everyDraftIsReadable() {
        BoldDrafts.all.forEach { d ->
            listOf(d.light, d.dark).forEach { t ->
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
        }
    }
}
